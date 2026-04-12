package com.cprint.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * cPrint Application class
 * Initializes logging, dependency injection and other app-wide configurations
 */
@HiltAndroidApp
class CPrintApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeTimber()
    }

    /**
     * Initialize Timber logging
     */
    private fun initializeTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
