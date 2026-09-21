package com.weiting.imageanalyzer

import android.app.Application
import timber.log.Timber

class ImageAnalyzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Tags every line with the calling class, so filtering by tag in logcat is enough.
            Timber.plant(Timber.DebugTree())
            Timber.d("ImageAnalyzer started")
        }
    }
}
