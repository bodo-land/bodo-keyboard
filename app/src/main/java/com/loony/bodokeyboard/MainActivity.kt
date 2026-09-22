package com.loony.bodokeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loony.bodokeyboard.ui.preview.PreviewView
import com.loony.bodokeyboard.ui.settings.SettingsView
import com.loony.bodokeyboard.ui.setup.SetupView
import com.loony.bodokeyboard.ui.theme.Accent
import com.loony.bodokeyboard.ui.theme.AppBg
import com.loony.bodokeyboard.ui.theme.DivLine
import com.loony.bodokeyboard.ui.theme.Surface1
import com.loony.bodokeyboard.ui.theme.Surface2
import com.loony.bodokeyboard.ui.theme.TextPri
import com.loony.bodokeyboard.ui.theme.TextSec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BodoTheme { MainScreen() } }
    }
}

@Composable
fun BodoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary      = Accent,
            secondary    = Accent,
            background   = AppBg,
            surface      = Surface1,
            surfaceVariant = Surface2,
            onPrimary    = Color(0xFF1A1A1A),
            onBackground = TextPri,
            onSurface    = TextPri
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    var tab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        Triple(Icons.Default.CheckCircle, "Setup",    0),
        Triple(Icons.Default.Keyboard,    "Try It",   1),
        Triple(Icons.Default.Settings,    "Settings", 2),
    )

    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Surface1)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    navItems.forEach { (icon, label, index) ->
                        val selected = tab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick  = { tab = index },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) Accent else TextSec
                                )
                            },
                            label = {
                                Text(
                                    label,
                                    color    = if (selected) Accent else TextSec,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Accent.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppBg)
        ) {
            when (tab) {
                0 -> SetupView()
                1 -> PreviewView()
                2 -> SettingsView()
            }
        }
    }
}
