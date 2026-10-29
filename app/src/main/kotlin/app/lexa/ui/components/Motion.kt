package app.lexa.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Overrides the system «Убрать анимацию» check: screenshot tests provide true so every animated
 * value is captured in its final state. Null (the default) reads the system setting.
 */
val LocalReducedMotion = staticCompositionLocalOf<Boolean?> { null }

/** True when motion must be instant: the system animator duration scale is 0 (or a test says so). */
@Composable
fun rememberReducedMotion(): Boolean {
    LocalReducedMotion.current?.let { return it }
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
