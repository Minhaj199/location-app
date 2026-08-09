package com.locationalarm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.locationalarm.app.ui.home.HomeScreen
import com.locationalarm.app.ui.theme.LocationAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationAlarmTheme {
                HomeScreen()
            }
        }
    }
}
