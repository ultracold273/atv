package com.example.atv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AtvApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure Timber logging based on build type
        // Debug: Verbose logging with full stack traces
        // Release: Error-level logging only, no sensitive data
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }
    
    /**
     * Production logging tree that only logs errors and critical events.
     * Filters out sensitive information like file paths and stream URLs.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log ERROR and ASSERT (critical) levels in production
            if (priority < android.util.Log.ERROR) {
                return
            }
            
            // Sanitize message to remove potential sensitive data
            val sanitizedMessage = sanitizeMessage(message)
            
            // Log using standard Android Log
            when (priority) {
                android.util.Log.ERROR -> android.util.Log.e(tag, sanitizedMessage, t)
                android.util.Log.ASSERT -> android.util.Log.wtf(tag, sanitizedMessage, t)
            }
        }
        
        /**
         * Remove potentially sensitive information from log messages.
         * Strips file paths, URLs with credentials, and user-specific data.
         */
        private fun sanitizeMessage(message: String): String {
            return message
                // Remove file paths that might contain usernames
                .replace(Regex("/storage/[^\\s]+"), "[FILE_PATH]")
                .replace(Regex("/data/[^\\s]+"), "[DATA_PATH]")
                // Remove URLs with potential credentials (user:pass@host)
                .replace(Regex("://[^:]+:[^@]+@"), "://[CREDENTIALS]@")
                // Remove query parameters that might contain tokens
                .replace(Regex("\\?[^\\s]+"), "?[PARAMS]")
        }
    }
}
