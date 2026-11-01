package com.ilya.rulivo.core.dunta

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationController {
    fun isIgnoring(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
