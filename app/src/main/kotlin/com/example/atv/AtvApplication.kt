package com.example.atv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AtvApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Always plant debug tree for now (BuildConfig not available at this stage)
        Timber.plant(Timber.DebugTree())
    }
}
