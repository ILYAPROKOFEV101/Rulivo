package com.ilya.rulivo.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class CameraPermissionHandler(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("rulivo_permissions", Activity.MODE_PRIVATE)
    var granted by mutableStateOf(hasPermission())
        private set
    var permanentlyDenied by mutableStateOf(false)
        private set

    fun refresh() {
        granted = hasPermission()
        permanentlyDenied = !granted && preferences.getBoolean("requested", false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    fun onResult() {
        preferences.edit { putBoolean("requested", true) }
        refresh()
    }

    fun openSettings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)))
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

data class CameraPermissionActions(val state: CameraPermissionHandler, val request: () -> Unit)

@Composable
fun rememberCameraPermissionHandler(activity: Activity): CameraPermissionActions {
    val state = remember(activity) { CameraPermissionHandler(activity).also { it.refresh() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state.onResult() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return CameraPermissionActions(state) {
        if (state.permanentlyDenied) state.openSettings() else launcher.launch(Manifest.permission.CAMERA)
    }
}
