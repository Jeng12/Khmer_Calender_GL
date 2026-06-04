package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.calendar.KhmerCalendarHelper
import com.example.ui.navigation.KhmerCalendarApp
import com.example.ui.theme.MyApplicationTheme
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
        setContent {
            MyApplicationTheme {
                KhmerCalendarApp()
            }
        }
    }
}
