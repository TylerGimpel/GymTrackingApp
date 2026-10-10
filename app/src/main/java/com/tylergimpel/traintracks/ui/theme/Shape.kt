package com.tylergimpel.traintracks.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Controls: buttons, fields, chips, steppers. */
val ControlShape = RoundedCornerShape(6.dp)

/** Cards, sheets and dialogs. */
val CardShape = RoundedCornerShape(10.dp)

/**
 * Squared off: two radii and nothing rounder, so dialogs and sheets stop being the pill-shaped
 * exception on screens that are otherwise all edges (#133).
 */
val GymShapes = Shapes(
    extraSmall = ControlShape,
    small = ControlShape,
    medium = CardShape,
    large = CardShape,
    extraLarge = CardShape,
)
