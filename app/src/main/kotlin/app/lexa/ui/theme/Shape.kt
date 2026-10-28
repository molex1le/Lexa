package app.lexa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** One radius for everything that has a shape (docs/DESIGN_TOKENS.md «Сетка и форма»). */
val LexaCorner = RoundedCornerShape(6.dp)

internal val LexaShapes = Shapes(
    extraSmall = LexaCorner,
    small = LexaCorner,
    medium = LexaCorner,
    large = LexaCorner,
    extraLarge = LexaCorner,
)
