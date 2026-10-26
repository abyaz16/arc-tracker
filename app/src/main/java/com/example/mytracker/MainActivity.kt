package com.example.mytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mytracker.data.Store
import com.example.mytracker.notify.Notifier
import com.example.mytracker.ui.theme.MyTrackerTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Process singleton, so rotation doesn't reload state from disk mid-edit.
        val store = Store.get(this)

        // Reschedule notifications in the background to avoid stalling the main thread during launch
        store.onDataChanged = {
            backgroundExecutor.execute { Notifier.reschedule(applicationContext, store) }
        }
        backgroundExecutor.execute { Notifier.reschedule(applicationContext, store) }

        setContent {
            // read from the store so flipping the theme repaints immediately
            MyTrackerTheme(mode = store.db.settings.theme.key) {
                CadenceApp(store)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Plan ages as the day advances, reschedule on background executor
        backgroundExecutor.execute {
            Notifier.reschedule(applicationContext, Store.get(this))
        }
    }
}
