package app.lexa.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lexa.R
import app.lexa.ui.components.LexaIcon
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

enum class TopLevel(@StringRes val label: Int, @DrawableRes val icon: Int) {
    TRAINING(R.string.nav_training, R.drawable.ic_training),
    TRANSLATOR(R.string.nav_translator, R.drawable.ic_translate),
    WORDS(R.string.nav_words, R.drawable.ic_words),
    STATS(R.string.nav_stats, R.drawable.ic_stats),
}

/**
 * Four destinations on the sheet tone. The active one is ink at weight 600, the rest pencil:
 * no pill, no marker – the marker belongs to content, not chrome.
 */
@Composable
fun LexaBottomBar(
    current: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.sheet)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .heightIn(min = 64.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TopLevel.entries.forEach { item ->
            val selected = item == current
            val tint = if (selected) colors.ink else colors.pencil
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The label below names the item, so the icon is decorative for TalkBack.
                LexaIcon(item.icon, contentDescription = null, tint = tint)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(item.label),
                    style = LexaTheme.type.meta.copy(
                        fontWeight = if (selected) FontWeight(600) else FontWeight(400),
                    ),
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
