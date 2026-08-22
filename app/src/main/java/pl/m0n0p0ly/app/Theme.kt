package pl.m0n0p0ly.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun GameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Color(0xFF1AA7FF), secondary = Color(0xFF27D17F), surface = Color(0xFF17191E), background = Color(0xFF080A0D)),
        content = content
    )
}
