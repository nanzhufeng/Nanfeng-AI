package com.nanzhufeng.ai.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Explicit shared geometry for every app switch. */
internal val SettingsSwitchTrackWidth = 58.dp
internal val SettingsSwitchTrackHeight = 28.dp
private val SettingsSwitchThumbDiameter = 21.dp
private val SettingsSwitchThumbInset = 3.5.dp
private val SettingsSwitchTouchTargetHeight = 48.dp

/**
 * A deliberately compact switch. Material 3 [androidx.compose.material3.Switch] always forces
 * its 52×32dp visual tokens, so sizing that composable only changes its parent constraints.
 * This component draws the shared 58×28dp geometry while keeping a 48dp-high touch target.
 */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            SettingsSwitchTrackWidth - SettingsSwitchThumbDiameter - (SettingsSwitchThumbInset * 2)
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 140),
        label = "settings-switch-thumb-offset",
    )
    val trackAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.42f
            pressed -> 0.82f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 80),
        label = "settings-switch-press-alpha",
    )
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        // surfaceVariant can equal the white settings card in custom light themes.  Deriving this
        // quiet gray from onSurface keeps the off state visible in both light and dark skins.
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .requiredSize(SettingsSwitchTrackWidth, SettingsSwitchTouchTargetHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SettingsSwitchTrackWidth, SettingsSwitchTrackHeight)
                .alpha(trackAlpha)
                .background(trackColor, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = SettingsSwitchThumbInset)
                    .offset(x = thumbOffset)
                    .size(SettingsSwitchThumbDiameter)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
