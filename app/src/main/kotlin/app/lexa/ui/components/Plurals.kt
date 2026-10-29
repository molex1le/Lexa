package app.lexa.ui.components

import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private val Russian = Locale.forLanguageTag("ru")

/**
 * A plural string chosen by Russian rules whatever the device language: the UI is Russian only, and
 * on an English phone the platform would pick «other» for 0 and 5 («0 дня» instead of «0 дней»).
 */
@Composable
fun pluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration) {
        val russian = Configuration(configuration).apply { setLocale(Russian) }
        context.createConfigurationContext(russian).resources
    }
    return resources.getQuantityString(id, count, *formatArgs)
}
