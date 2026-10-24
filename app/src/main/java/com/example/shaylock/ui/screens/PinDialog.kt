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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary

/**
 * A modal PIN prompt used for every protected setting.
 *
 * Changing the daily allowance, switching the blocker off, clearing today's usage and pausing
 * the block all go through this dialog, so there is exactly one place where the PIN is entered.
 */
@Composable
fun PinDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

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
                    .background(InkAccentContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_shaylock_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = InkTextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

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
                if (hasError) {
                    Text(
                        text = "Incorrect PIN. Try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDanger,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            NumericKeypad(
                enabled = true,
                onDigit = { digit ->
                    hasError = false
                    if (pin.length < BlockPolicy.PIN_LENGTH) pin += digit
                },
                onBackspace = {
                    hasError = false
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                },
                contentColor = InkTextPrimary,
                pressedColor = InkSurfaceHigh,
                keySize = 56.dp,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (onConfirm(pin)) {
                        onDismiss()
                    } else {
                        hasError = true
                        pin = ""
                    }
                },
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
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = InkTextTertiary),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
