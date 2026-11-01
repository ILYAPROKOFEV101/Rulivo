package com.ilya.rulivo

import android.app.Application
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.ilya.rulivo.i18n.AppStrings
import com.ilya.rulivo.core.dunta.DuntaSdkController

class RulivoApplication : Application() {
    val duntaSdkController: DuntaSdkController by lazy { DuntaSdkController(this) }

    override fun onCreate() {
        super.onCreate()
        AppStrings.initialize(ContextCompat.getContextForLanguage(this).resources)
        duntaSdkController.prepare()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppStrings.initialize(ContextCompat.getContextForLanguage(this).resources)
        duntaSdkController.refreshLanguage()
    }
}
