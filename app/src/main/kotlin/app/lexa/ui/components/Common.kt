package app.lexa.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lexa.R
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

/** Outline icon, 20dp, ink by default. */
@Composable
fun LexaIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LexaTheme.colors.ink,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(20.dp),
        tint = tint,
    )
}

/** A 48dp icon-only action with no container. */
@Composable
fun LexaIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Spacing.touch),
        colors = IconButtonDefaults.iconButtonColors(contentColor = LexaTheme.colors.ink),
    ) {
        LexaIcon(icon, contentDescription)
    }
}

/**
 * The top of every screen: a 48dp action row (back on the left, one action on the right),
 * then the screen title at heading size, left-aligned.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    titleEnd: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onBack != null) {
                LexaIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack,
                    Modifier.padding(start = Spacing.xs))
            } else {
                Spacer(Modifier.width(Spacing.gutter))
            }
            if (trailing != null) {
                Row(Modifier.padding(end = Spacing.xs)) { trailing() }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = LexaTheme.type.heading,
                color = LexaTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            titleEnd?.invoke()
        }
    }
}

/**
 * An honest empty state: one sentence in the user's voice, left-aligned, and at most one action
 * (a marker button named with a verb).
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            text = message,
            style = LexaTheme.type.title,
            color = LexaTheme.colors.ink,
        )
        if (actionLabel != null) {
            MarkerButton(text = actionLabel, onClick = onAction)
        }
    }
}
