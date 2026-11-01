package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.annotation.SuppressLint
import android.app.Activity
import android.opengl.GLSurfaceView
import com.ilya.rulivo.ui.measurement.MeasurementStatus

// Created by AndroidView with an Activity and callbacks, never inflated from XML.
@SuppressLint("ViewConstructor")
class ArMeasurementView(
    activity: Activity,
    onStatus: (MeasurementStatus) -> Unit,
    onFrame: (ArFrameState) -> Unit,
) : GLSurfaceView(activity) {
    val controller = ArMeasurementController(activity, this, onStatus, onFrame)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        // Releasing the EGL context on pause also releases camera textures and GL programs.
        preserveEGLContextOnPause = false
        setRenderer(controller)
        renderMode = RENDERMODE_CONTINUOUSLY
        keepScreenOn = true
        contentDescription = text(R.string.camera_for_ar_measurements)
    }
}
