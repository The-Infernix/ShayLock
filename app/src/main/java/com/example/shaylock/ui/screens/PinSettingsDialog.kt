package com.example.shaylock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.shaylock.R
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.ui.components.NumericKeypad
import com.example.shaylock.ui.components.PinDots
import com.example.shaylock.ui.theme.InkAccent
import com.example.shaylock.ui.theme.InkAccentContainer
import com.example.shaylock.ui.theme.InkDanger
import com.example.shaylock.ui.theme.InkOnAccent
import com.example.shaylock.ui.theme.InkSuccess
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary

/**
 * Multi-step dialog that replaces the app PIN.
 *
 * The current PIN is verified first, then the new PIN has to be entered twice. Verification is
 * delegated to [onVerifyCurrentPin], which keeps the expensive PBKDF2 check inside
 * [com.example.shaylock.core.BlockController] and out of the composable.
 *
 * The legacy default PIN cannot be chosen as the new PIN: it is public knowledge and would
 * defeat the point of replacing it.
 */
@Composable
fun PinSettingsDialog(
    isUsingDefaultPin: Boolean,
    onDismiss: () -> Unit,
    onVerifyCurrentPin: (String) -> Boolean,
    onChangePin: (String) -> Boolean,
) {
    var stage by remember { mutableStateOf(PinStage.VERIFY) }
    var pin by remember { mutableStateOf("") }
    var pendingPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetError() {
        if (error != null) error = null
    }

    fun submit() {
        if (pin.length != BlockPolicy.PIN_LENGTH) return

        when (stage) {
            PinStage.VERIFY -> {
                if (onVerifyCurrentPin(pin)) {
                    stage = PinStage.NEW
                    pin = ""
                    error = null
                } else {
                    error = "Incorrect PIN. Try again."
                    pin = ""
                }
            }

            PinStage.NEW -> {
                if (pin == BlockPolicy.DEFAULT_PIN) {
                    error = "That is the public default PIN. Choose a different one."
                    pin = ""
                } else {
                    pendingPin = pin
                    stage = PinStage.CONFIRM
                    pin = ""
                    error = null
                }
            }

            PinStage.CONFIRM -> {
                if (pin != pendingPin) {
                    error = "The PINs do not match. Start again."
                    pendingPin = ""
                    stage = PinStage.NEW
                    pin = ""
                } else if (onChangePin(pin)) {
                    pin = ""
                    error = null
                    stage = PinStage.DONE
                } else {
                    error = "The PIN could not be saved. Try again."
                    pin = ""
                }
            }

            PinStage.DONE -> Unit
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(InkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (stage == PinStage.DONE) InkSuccess.copy(alpha = 0.16f) else InkAccentContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (stage == PinStage.DONE) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = InkSuccess,
                        modifier = Modifier.size(30.dp),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_shaylock_mark),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (stage) {
                    PinStage.VERIFY -> "Enter current PIN"
                    PinStage.NEW -> "Choose a new PIN"
                    PinStage.CONFIRM -> "Confirm the new PIN"
                    PinStage.DONE -> "PIN updated"
                },
                style = MaterialTheme.typography.titleLarge,
                color = InkTextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (stage) {
                    PinStage.VERIFY -> if (isUsingDefaultPin) {
                        "The default PIN 2310 is still active. Confirm it to set your own."
                    } else {
                        "Confirm your current PIN to replace it."
                    }
                    PinStage.NEW -> "Pick four digits you will remember. Every override and " +
                        "setting change will use this PIN."
                    PinStage.CONFIRM -> "Enter the same four digits again."
                    PinStage.DONE -> "Overrides and settings are now protected by your new PIN."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                textAlign = TextAlign.Center,
            )

            if (stage != PinStage.DONE) {
                Spacer(Modifier.height(18.dp))

                PinDots(
                    filled = pin.length,
                    hasError = error != null,
                    total = BlockPolicy.PIN_LENGTH,
                    dotColor = InkAccent,
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier.height(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (error != null) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDanger,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                NumericKeypad(
                    enabled = true,
                    onDigit = { digit ->
                        resetError()
                        if (pin.length < BlockPolicy.PIN_LENGTH) pin += digit
                    },
                    onBackspace = {
                        resetError()
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    },
                    contentColor = InkTextPrimary,
                    pressedColor = InkSurfaceHigh,
                    keySize = 52.dp,
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { submit() },
                    enabled = pin.length == BlockPolicy.PIN_LENGTH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                        disabledContainerColor = InkSurfaceHigh,
                        disabledContentColor = InkTextTertiary,
                    ),
                ) {
                    Text(
                        text = when (stage) {
                            PinStage.VERIFY, PinStage.NEW -> "Continue"
                            PinStage.CONFIRM -> "Save PIN"
                            PinStage.DONE -> ""
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                    ),
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = InkTextTertiary),
            ) {
                Text(if (stage == PinStage.DONE) "Close" else "Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private enum class PinStage {
    VERIFY,
    NEW,
    CONFIRM,
    DONE,
}
