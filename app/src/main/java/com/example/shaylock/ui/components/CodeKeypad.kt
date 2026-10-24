package com.example.shaylock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The shared numeric keypad used by the lock screen and the PIN dialog.
 *
 * A custom keypad is used instead of the soft keyboard on purpose: it never offers autofill or
 * keyboard suggestions, so the PIN cannot leak into another app's input method.
 */

/**
 * [total] dots that fill up as the PIN is typed. Filled dots pop in, and a wrong entry shakes the
 * row while shifting the dots to the error color.
 */
@Composable
fun PinDots(
    filled: Int,
    hasError: Boolean,
    total: Int,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val fillColor by animateColorAsState(
        targetValue = if (hasError) errorColor else dotColor,
        animationSpec = tween(180),
        label = "pinDotFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (hasError) errorColor else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "pinDotBorder",
    )

    val shake = remember { Animatable(0f) }
    LaunchedEffect(hasError) {
        if (hasError) {
            shake.snapTo(0f)
            listOf(-10f, 10f, -7f, 5f, 0f).forEach { target ->
                shake.animateTo(target, animationSpec = tween(55))
            }
        } else {
            shake.snapTo(0f)
        }
    }

    Row(
        modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val isFilled = index < filled
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "pinDotScale",
            )
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(fillColor),
                )
            }
        }
    }
}

/**
 * A 0-9 keypad with a backspace key.
 *
 * The keys are disabled once [enabled] is false, which is how the caller stops further input once
 * the PIN is complete.
 */
@Composable
fun NumericKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    keySize: Dp = 66.dp,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<"),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val availableWidth = (maxWidth - spacing * 2f) / 3f
        val diameter = minOf(keySize, availableWidth, 72.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { key ->
                        when (key) {
                            "" -> Spacer(Modifier.size(diameter))

                            "<" -> KeypadKey(
                                label = "⌫",
                                size = diameter,
                                enabled = enabled,
                                contentColor = contentColor,
                                pressedColor = pressedColor,
                                onClick = onBackspace,
                            )

                            else -> KeypadKey(
                                label = key,
                                size = diameter,
                                enabled = enabled,
                                contentColor = contentColor,
                                pressedColor = pressedColor,
                                onClick = { onDigit(key.first()) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single keypad key: a flat circular target with a smooth bounded ripple, light haptic tick and
 * a subtle press-scale.
 */
@Composable
private fun KeypadKey(
    label: String,
    size: Dp,
    enabled: Boolean,
    contentColor: Color,
    pressedColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val indication = LocalIndication.current

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyScale",
    )
    val background by animateColorAsState(
        targetValue = if (pressed && enabled) pressedColor else Color.Transparent,
        animationSpec = tween(140),
        label = "keyBackground",
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
            color = contentColor.copy(alpha = if (enabled) 0.95f else 0.3f),
        )
    }
}
