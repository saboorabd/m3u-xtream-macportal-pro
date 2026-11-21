package com.m3upro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        if (savedInstanceState == null) intent?.data?.let { vm.addFromUri(it) }
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { App(vm) } } }
    }

    /** If the app ever crashes, the stack trace is kept and shown in Settings -> Diagnostics on the next start. */
    private fun installCrashReporter() {
        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                prefs.edit().putString("last", android.util.Log.getStackTraceString(e).take(8000) +
                    "\n\n--- log ---\n" + DebugLog.dump().takeLast(3000)).commit()
            } catch (ex: Throwable) { /* nothing more we can do */ }
            if (prev != null) prev.uncaughtException(t, e)
        }
    }
}
