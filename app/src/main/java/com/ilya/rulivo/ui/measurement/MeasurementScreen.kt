package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilya.rulivo.ar.ArMeasurementView
import com.ilya.rulivo.ar.CaptureRequest
import com.ilya.rulivo.ar.CaptureResult
import com.ilya.rulivo.ar.MeasurementReading
import com.ilya.rulivo.permissions.rememberCameraPermissionHandler
import com.ilya.rulivo.ui.components.AimingGrid
import com.ilya.rulivo.ui.components.CenterReticle
import com.ilya.rulivo.ui.components.MeasurementControls
import com.ilya.rulivo.ui.components.MeasurementResultCard
import com.ilya.rulivo.ui.components.ScanProgressCard
import com.ilya.rulivo.ui.theme.CameraPanel
import com.ilya.rulivo.ui.theme.Ink
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted
import com.ilya.rulivo.ui.theme.RulivoTheme

@Composable
fun MeasurementScreen(activity: Activity, viewModel: MeasurementViewModel, visible: Boolean = true,
    onTools: (() -> Unit)? = null, captureRequest: CaptureRequest? = null, onCaptured: (CaptureResult) -> Unit = {}) {
    val preferences = remember { activity.getSharedPreferences("rulivo", Activity.MODE_PRIVATE) }
    var introComplete by remember { mutableStateOf(preferences.getBoolean("intro_complete", false)) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val permission = rememberCameraPermissionHandler(activity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isVisible by rememberUpdatedState(visible)
    LaunchedEffect(state.capture?.confirmed, captureRequest?.id) {
        state.capture?.confirmed?.takeIf { it.requestId == captureRequest?.id }?.let(onCaptured)
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        when {
            !introComplete -> WelcomeScreen(onStart = {
                preferences.edit { putBoolean("intro_complete", true) }
                introComplete = true
                if (!permission.state.granted) permission.request()
            }, onHelp = { showHelp = true })
            !permission.state.granted -> {
                LaunchedEffect(Unit) { viewModel.onStatus(MeasurementStatus.CameraPermissionRequired) }
                PermissionScreen(permission.state.permanentlyDenied, permission.request)
            }
            else -> key(attempt) {
                val arView = remember(activity, attempt) {
                    ArMeasurementView(activity, viewModel::onStatus, viewModel::onFrame)
                }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                AndroidView(factory = { arView }, modifier = Modifier.fillMaxSize(),
                    update = { it.controller.setMeasurementUnit(state.unit); it.controller.setCaptureRequest(captureRequest) })
                LaunchedEffect(arView, visible) {
                    if (visible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) arView.controller.resume()
                    else arView.controller.pause()
                }
                DisposableEffect(arView, lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> if (isVisible) arView.controller.resume()
                            Lifecycle.Event.ON_PAUSE -> arView.controller.pause()
                            Lifecycle.Event.ON_DESTROY -> arView.controller.destroy()
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    if (isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) arView.controller.resume()
                    onDispose {
                        lifecycle.removeObserver(observer)
                        arView.controller.destroy()
                    }
                }
                when (val status = state.status) {
                    MeasurementStatus.ArNotSupported -> MessageScreen(
                        title = text(R.string.ar_not_available),
                        message = text(R.string.this_device_does_not_support_ar_measurements),
                        actionLabel = text(R.string.close_application), onAction = activity::finish,
                    )
                    is MeasurementStatus.Error -> MessageScreen(
                        title = text(R.string.failed_to_start), message = status.message,
                        actionLabel = text(R.string.retry), onAction = { attempt++ },
                    )
                    MeasurementStatus.CameraPermissionRequired -> PermissionScreen(
                        permission.state.permanentlyDenied, onRequest = {
                            permission.state.refresh()
                            permission.request()
                            attempt++
                        },
                    )
                    MeasurementStatus.CheckingAvailability -> MessageScreen(
                        title = text(R.string.camera_preparation), message = text(R.string.checking_ar_support_and_google_services),
                        loading = true,
                    )
                    else -> if (captureRequest != null) CaptureOverlay(state, captureRequest,
                        onBegin = arView.controller::beginMeasurement, onCommand = arView.controller::captureCommand,
                        onCancel = { onTools?.invoke() }, animationsEnabled = visible && !showHelp) else MeasurementOverlay(state, {
                        if (state.scanPhase.isPreparing) arView.controller.beginMeasurement()
                        else arView.controller.placePoint(state.sceneRevision)
                    }, arView.controller::reset,
                        viewModel::selectUnit, onHelp = { showHelp = true },
                        onUndo = { arView.controller.undo(state.sceneRevision) },
                        onCancelCurrent = { arView.controller.cancelCurrent(state.sceneRevision) }, onTools = onTools,
                        animationsEnabled = visible && !showHelp,
                        onContinue = { arView.controller.continueLine(state.sceneRevision) },
                        onCloseContour = { arView.controller.closeContour(state.sceneRevision) })
                }
            }
        }
        if (onTools != null && (!introComplete || !permission.state.granted || state.status is MeasurementStatus.Error ||
                state.status == MeasurementStatus.ArNotSupported || state.status == MeasurementStatus.CheckingAvailability)) {
            TextButton(onClick = onTools, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) { Text(text(R.string.back_to_modes)) }
        }
    }
    if (showHelp) HelpDialog { showHelp = false }
}

@Composable
internal fun MeasurementOverlay(
    state: MeasurementUiState,
    onPlace: () -> Unit,
    onReset: () -> Unit,
    onUnitChange: (MeasurementUnit) -> Unit,
    onHelp: () -> Unit,
    onUndo: () -> Unit = {},
    onCancelCurrent: () -> Unit = {},
    onTools: (() -> Unit)? = null,
    animationsEnabled: Boolean = true,
    onContinue: () -> Unit = {},
    onCloseContour: () -> Unit = {},
) {
    // This box has exactly the camera viewport's bounds. Insets only affect the controls.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val controlsMaxHeight = if (landscape) maxHeight - 40.dp else (maxHeight / 2 - 46.dp).coerceAtLeast(100.dp)
        val resultMaxHeight = if (landscape) maxHeight - 140.dp else maxHeight / 2 - 132.dp
        val resultMaxWidth = if (landscape) minOf(280.dp, maxWidth / 2 - 60.dp) else 280.dp
        val controlsMaxWidth = if (landscape) minOf(340.dp, maxWidth / 2 - 52.dp) else 480.dp
        var headerBounds by remember { mutableStateOf(Rect.Zero) }
        var resultBounds by remember { mutableStateOf(Rect.Zero) }
        var controlsBounds by remember { mutableStateOf(Rect.Zero) }
        val controlsScroll = rememberScrollState()
        LaunchedEffect(state.scanPhase, state.trackingIssue, state.pointCount) {
            controlsScroll.scrollTo(0)
        }
        AimingGrid(listOf(headerBounds, resultBounds, controlsBounds))
        Box(Modifier.fillMaxWidth().height(200.dp).background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))))
        CenterReticle(state.canPlacePoint, state.hasTarget, Modifier.align(Alignment.Center))
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).onGloballyPositioned { headerBounds = it.boundsInRoot() }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Brand()
                Surface(shape = CircleShape, color = CameraPanel) {
                    Row {
                        if (onTools != null) TextButton(onClick = onTools) { Text(text(R.string.modes), color = Color.White) }
                        TextButton(onClick = onHelp) { Text(if (onTools == null) text(R.string.how_to_measure) else text(R.string.help_short), color = Color.White) }
                    }
                }
            }
            val resultModifier = Modifier.align(if (landscape) Alignment.TopStart else Alignment.TopCenter)
                .padding(top = 76.dp)
                .heightIn(max = resultMaxHeight.coerceAtLeast(100.dp))
                .verticalScroll(rememberScrollState()).onGloballyPositioned { resultBounds = it.boundsInRoot() }
            if (state.scanPhase.isPreparing) ScanProgressCard(state, resultModifier.widthIn(max = resultMaxWidth), animationsEnabled)
            else MeasurementResultCard(state, resultModifier)
            MeasurementControls(state, onPlace, onReset, onUnitChange, onUndo, onCancelCurrent,
                Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                    .widthIn(max = controlsMaxWidth).fillMaxWidth()
                    .testTag("measurement-controls")
                    .heightIn(max = controlsMaxHeight).onGloballyPositioned { controlsBounds = it.boundsInRoot() }
                    .verticalScroll(controlsScroll), onHelp = onHelp, animationsEnabled = animationsEnabled, onContinue = onContinue, onCloseContour = onCloseContour)
        }
    }
}

@Composable
private fun Brand() {
    Column {
        Text(text(R.string.app_name), fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = (-1).sp)
        Text(text(R.string.ar_measure), fontSize = 9.sp, letterSpacing = 3.sp, color = Lime)
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onHelp: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { Brand() }
        Spacer(Modifier.height(24.dp))
        RulerIllustration()
        Spacer(Modifier.height(24.dp))
        Text(text(R.string.measure_distances_with_your_camera), style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(16.dp))
        Text(text(R.string.show_the_camera_the_surface_then_mark), color = Muted,
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = CameraPanel, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Instruction(text(R.string.instruction_step, 1), text(R.string.point_the_camera_at_the_floor_or_314))
                Instruction(text(R.string.instruction_step, 2), text(R.string.wait_until_ready_then_tap_start_measurement))
                Instruction(text(R.string.instruction_step, 3), text(R.string.aim_the_green_crosshair_at_the_desired))
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp)) { Text(text(R.string.start_measurement)) }
        TextButton(onClick = onHelp) { Text(text(R.string.about_measurements_and_arcore), color = Muted) }
        Text(text(R.string.powered_by_google_play_services_for_ar),
            style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Instruction(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = Lime, style = MaterialTheme.typography.labelLarge)
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RulerIllustration() {
    Canvas(Modifier.size(144.dp)) {
        drawCircle(Lime.copy(alpha = 0.06f))
        drawCircle(Lime.copy(alpha = 0.08f), radius = size.width * 0.36f)
        val start = Offset(size.width * 0.22f, size.height * 0.64f)
        val end = Offset(size.width * 0.78f, size.height * 0.36f)
        drawLine(Lime, start, end, 3.dp.toPx(), StrokeCap.Round)
        for (step in 1..7) {
            val point = start + (end - start) * (step / 8f)
            drawLine(Lime.copy(alpha = 0.5f), point, point + Offset(-3.dp.toPx(), -6.dp.toPx()), 1.dp.toPx())
        }
        listOf(start, end).forEach {
            drawCircle(Ink, 9.dp.toPx(), it)
            drawCircle(Color.White, 6.dp.toPx(), it)
            drawCircle(Lime, 3.dp.toPx(), it)
        }
    }
}

@Composable
private fun PermissionScreen(permanentlyDenied: Boolean, onRequest: () -> Unit) {
    MessageScreen(title = text(R.string.allow_access_to_camera),
        message = if (permanentlyDenied) text(R.string.camera_access_disabled_open_rulivo_settings_permissions)
        else text(R.string.the_camera_is_needed_to_find_surfaces),
        actionLabel = if (permanentlyDenied) text(R.string.open_settings) else text(R.string.allow_camera),
        onAction = onRequest)
}

@Composable
private fun MessageScreen(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    loading: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Brand()
            if (loading) CircularProgressIndicator(color = Lime, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(message, color = Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(text(R.string.help_with_scanning)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text(R.string.message_1_show_the_camera_a_lit_floor))
                Text(text(R.string.message_2_when_surfaces_found_appears_tap_start))
                Text(text(R.string.message_3_follow_the_current_step_for_a))
                Text(text(R.string.contour_help))
                Text(text(R.string.if_the_camera_has_lost_its_bearings))
                Text(text(R.string.if_the_surface_is_not_recognized_try))
                Text(text(R.string.the_result_is_approximate_and_depends_on))
                Text(text(R.string.the_application_uses_google_play_services_for))
                TextButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) {
                    Text(text(R.string.google_privacy_policy))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text(R.string.got_it)) } })
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WelcomePreview() { RulivoTheme { WelcomeScreen({}, {}) } }

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MeasurementPreview() {
    RulivoTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
            MeasurementOverlay(MeasurementUiState(status = MeasurementStatus.ReadyForFirstPoint,
                measurements = listOf(MeasurementReading(1L, 1.42, true)), canPlacePoint = true, isTracking = true,
                scanPhase = com.ilya.rulivo.ar.SpaceScanPhase.MEASURING,
                hint = text(R.string.line_saved_start_the_next_measurement)), {}, {}, {}, {})
        }
    }
}
