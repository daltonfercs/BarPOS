package com.example.barpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.barpos.ui.theme.BarPOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarPOSTheme { 
                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = MaterialTheme.colorScheme.background
                ) { 
                    MainNavigation() 
                } 
            }
        }
    }
}
