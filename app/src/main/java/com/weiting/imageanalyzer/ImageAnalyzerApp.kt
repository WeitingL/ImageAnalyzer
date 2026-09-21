package com.weiting.imageanalyzer

import android.app.Application
import com.weiting.imageanalyzer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class ImageAnalyzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Tags every line with the calling class, so filtering by tag in logcat is enough.
            Timber.plant(Timber.DebugTree())
            Timber.d("ImageAnalyzer started")
        }

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@ImageAnalyzerApp)
            modules(appModule)
        }
    }
}
