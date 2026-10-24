package com.example.shaylock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaylock.R
import com.example.shaylock.core.BlockController
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.formatLimit
import com.example.shaylock.ui.components.NumericKeypad
import com.example.shaylock.ui.components.PinDots
import com.example.shaylock.ui.theme.InkAccent
import com.example.shaylock.ui.theme.InkBottom
import com.example.shaylock.ui.theme.InkDanger
import com.example.shaylock.ui.theme.InkHairline
import com.example.shaylock.ui.theme.InkOnAccent
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary
import com.example.shaylock.ui.theme.InkTop
import com.example.shaylock.util.AppLabels

/**
 * The strict lock screen that is drawn on top of a blocked app.
 *
 * The only way out is the correct 4-digit PIN. There is no "skip", no "snooze" and the back /
 * home buttons are swallowed by the window and by the accessibility watchdog.
 */
@Composable
fun OverlayScreen(
    targetPackage: String,
    onSubmitPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val appLabel = remember(targetPackage) { AppLabels.labelFor(context, targetPackage) }

    val limit by BlockController.dailyLimit.collectAsState()
    val usedTodayMs by BlockController.usedTodayMs.collectAsState()
    val pinExtensionsUsedToday by BlockController.pinExtensionsUsedToday.collectAsState()
    val pinExtensionsRemaining =
        (BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY - pinExtensionsUsedToday).coerceAtLeast(0)

    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    fun submit() {
        if (pinExtensionsRemaining <= 0) return
        if (pin.length != BlockPolicy.PIN_LENGTH) return
        if (onSubmitPin(pin)) {
            hasError = false
            onUnlocked()
        } else {
            // Wrong PIN: wipe the entry and keep the app blocked.
            hasError = true
            pin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkTop, InkBottom))),
        contentAlignment = Alignment.Center,
    ) {
        // A very soft ambient tint keeps the lock screen from feeling like a flat black rectangle.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-140).dp)
                .size(380.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(InkDanger.copy(alpha = 0.10f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Header -------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = InkDanger)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "BLOCKED",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkDanger,
                    letterSpacing = 1.8.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(InkSurface),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_shaylock_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "$appLabel is blocked",
                style = MaterialTheme.typography.headlineSmall,
                color = InkTextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (limit.limitMs > 0L) {
                    "Your daily allowance is used up. It resets automatically at midnight."
                } else {
                    "$appLabel is blocked until the rule is turned off."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                textAlign = TextAlign.Center,
            )

            if (limit.limitMs > 0L) {
                Spacer(Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${formatLimit(usedTodayMs)} used",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkTextPrimary,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(12.dp)
                            .background(InkHairline),
                    )
                    Text(
                        text = "${formatLimit(limit.limitMs)} allowance",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- PIN card -----------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(InkSurface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (pinExtensionsRemaining > 0) {
                        "Enter your 4-digit PIN to unlock for 5 minutes."
                    } else {
                        "No PIN extensions left today."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (pinExtensionsRemaining > 0) {
                        "$pinExtensionsRemaining of " +
                            "${BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY} PIN extensions left today"
                    } else {
                        "Instagram stays blocked until midnight."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pinExtensionsRemaining > 0) InkTextTertiary else InkDanger,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                PinDots(
                    filled = pin.length,
                    hasError = hasError,
                    total = BlockPolicy.PIN_LENGTH,
                    dotColor = InkAccent,
                )

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.height(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedVisibility(
                        visible = hasError,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                    ) {
                        Text(
                            text = "Incorrect PIN. $appLabel stays blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDanger,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                NumericKeypad(
                    enabled = pinExtensionsRemaining > 0,
                    onDigit = { digit ->
                        hasError = false
                        if (pin.length < BlockPolicy.PIN_LENGTH) {
                            pin += digit
                        }
                    },
                    onBackspace = {
                        hasError = false
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    },
                    contentColor = InkTextPrimary,
                    pressedColor = InkSurfaceHigh,
                    keySize = 60.dp,
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { submit() },
                    enabled = pinExtensionsRemaining > 0 &&
                        pin.length == BlockPolicy.PIN_LENGTH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                        disabledContainerColor = InkSurfaceHigh,
                        disabledContentColor = InkTextTertiary,
                    ),
                ) {
                    Text("Unlock for 5 minutes", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "The correct PIN opens Instagram for five minutes.",
                style = MaterialTheme.typography.labelSmall,
                color = InkTextTertiary,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A tiny pulsing dot used to signal live state on the dark overlay.
 */
@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .size(5.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}
