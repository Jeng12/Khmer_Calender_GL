package com.aistudio.khmercalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.ui.navigation.KhmerCalendarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Warm up the milestone table off the main thread to avoid first-frame jank
        lifecycleScope.launch(Dispatchers.Default) {
            KhmerCalendarHelper.warmUp()
        }
        setContent { KhmerCalendarApp() }
    }

    /**
     * Auto-refresh the home-screen widgets whenever the app returns to the
     * foreground. The widget's own [updatePeriodMillis] is fixed at the 30-minute
     * platform floor, so this keeps today's date/shift and notes/events fresh the
     * moment the user comes back to the app, without any manual refresh action.
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching { com.aistudio.khmercalendar.widget.WidgetPrefs.refresh(this@MainActivity) }
        }
    }
}
