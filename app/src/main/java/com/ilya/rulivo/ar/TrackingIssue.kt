package com.ilya.rulivo.ar

/** Camera failure information shared with the UI without exposing ARCore objects. */
enum class TrackingIssue { LOW_LIGHT, TOO_MUCH_MOTION, FEW_FEATURES, CAMERA_UNAVAILABLE, RELOCALIZING }
