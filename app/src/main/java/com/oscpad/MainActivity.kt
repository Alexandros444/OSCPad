package com.oscpad

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oscpad.ui.OscPadApp
import com.oscpad.ui.OscPadTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A control surface should never dim or lock mid-performance.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        requestedOrientation = vm.orientation.activityFlag
        setContent {
            val orientation = vm.orientation
            LaunchedEffect(orientation) { requestedOrientation = orientation.activityFlag }
            OscPadTheme(vm.theme, vm.uiStyle) {
                OscPadApp(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }
}
