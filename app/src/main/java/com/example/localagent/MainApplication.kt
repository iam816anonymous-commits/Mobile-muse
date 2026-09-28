package com.example.localagent

import android.app.Application
import android.util.Log
import com.example.localagent.safety.CrashHandler

class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MainApplication initialized")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this, defaultHandler))
    }
}
