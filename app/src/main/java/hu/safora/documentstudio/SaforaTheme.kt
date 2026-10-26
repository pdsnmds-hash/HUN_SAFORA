package hu.safora.documentstudio

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

fun saforaColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFB4A2FF), background = Color(0xFF17171E), surface = Color(0xFF24242E),
    onBackground = Color(0xFFF4F2FA), onSurface = Color(0xFFF4F2FA), onSurfaceVariant = Color(0xFFABA8BA),
    outline = Color(0xFF393944), primaryContainer = Color(0xFF39334F), onPrimaryContainer = Color(0xFFB4A2FF)
) else lightColorScheme(
    primary = Color(0xFF7258D9), background = Color(0xFFF8F7F3), surface = Color.White,
    onBackground = Color(0xFF242333), onSurface = Color(0xFF242333), onSurfaceVariant = Color(0xFF787684),
    outline = Color(0xFFE8E5E0), primaryContainer = Color(0xFFEAE5FF), onPrimaryContainer = Color(0xFF7258D9)
)

@Composable fun SaforaSystemBars(dark: Boolean) {
    val activity = androidx.activity.compose.LocalActivity.current ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
