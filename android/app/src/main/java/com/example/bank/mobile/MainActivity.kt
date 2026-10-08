package com.example.bank.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.bank.mobile.data.StartPoint
import com.example.bank.mobile.ui.AppNavigation
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.LimeTheme

/** AppCompatActivity (not plain ComponentActivity) so the in-app Khmer/English switch works on every Android version. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Banking screens must not appear in screenshots or the recent-apps preview.
        // Debug builds allow screenshots so UI tests and developers can capture screens.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val session = container.session
        setContent {
            LimeTheme {
                var start by remember { mutableStateOf<StartPoint?>(null) }
                LaunchedEffect(Unit) { start = session.startPoint() }
                Box(Modifier.fillMaxSize().background(Lime.Background)) {
                    start?.let { AppNavigation(it) }
                }
            }
        }
    }
}
