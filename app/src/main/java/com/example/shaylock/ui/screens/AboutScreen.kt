package com.example.shaylock.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaylock.R
import com.example.shaylock.core.BlockController
import com.example.shaylock.ui.theme.InkAccent
import com.example.shaylock.ui.theme.InkAccentContainer
import com.example.shaylock.ui.theme.InkDanger
import com.example.shaylock.ui.theme.InkDangerContainer
import com.example.shaylock.ui.theme.InkHairline
import com.example.shaylock.ui.theme.InkOnAccent
import com.example.shaylock.ui.theme.InkOnDangerContainer
import com.example.shaylock.ui.theme.InkOnSuccessContainer
import com.example.shaylock.ui.theme.InkSuccess
import com.example.shaylock.ui.theme.InkSuccessContainer
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary

private const val APP_VERSION = "v1.0.0"
private const val GITHUB_URL = "https://github.com/"
private const val PROJECT_REPO_URL = "https://github.com/"

private val MIT_LICENSE_TEXT = """
MIT License

Copyright (c) 2026 Infernix

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
""".trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    accessibilityEnabled: Boolean,
    overlayGranted: Boolean,
    onNavigateBack: () -> Unit,
    onFixPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var debugModeUnlocked by remember { mutableStateOf(false) }
    var debugToggleEnabled by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("About & Settings Info", color = InkTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Branding Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = InkSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(InkAccentContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_shaylock_mark),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = "ShayLock",
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = APP_VERSION,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkTextSecondary,
                        modifier = Modifier.clickable {
                            versionTapCount++
                            if (versionTapCount >= 5 && !debugModeUnlocked) {
                                debugModeUnlocked = true
                            }
                        },
                    )
                    Text(
                        text = "Strict screen-time blocker for Instagram",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkTextTertiary,
                    )
                }
            }

            // System Status & Quick Recovery
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = InkSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = InkAccent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "System Status & Quick Recovery",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkTextPrimary,
                        )
                    }

                    StatusRow(
                        label = "Accessibility Service",
                        isActive = accessibilityEnabled,
                    )
                    StatusRow(
                        label = "Draw Over Apps",
                        isActive = overlayGranted,
                    )

                    if (!accessibilityEnabled) {
                        Button(
                            onClick = onFixPermissions,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkAccent,
                                contentColor = InkOnAccent,
                            ),
                        ) {
                            Text("Fix Permissions")
                        }
                        Text(
                            text = "Opens Accessibility Settings to re-enable the service.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTextTertiary,
                        )
                    }
                }
            }

            // Developer Links
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = InkSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = InkAccent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Developer Links",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkTextPrimary,
                        )
                    }

                    LinkRow(
                        title = "GitHub",
                        subtitle = GITHUB_URL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            context.startActivity(intent)
                        },
                    )
                    HorizontalDivider(color = InkHairline)
                    LinkRow(
                        title = "Project Repo",
                        subtitle = PROJECT_REPO_URL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPO_URL))
                            context.startActivity(intent)
                        },
                    )
                }
            }

            // Privacy & Transparency
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = InkSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = InkSuccess,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Transparency",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkTextPrimary,
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = null,
                            tint = InkSuccess,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "100% On-Device Processing. ShayLock does not collect, store, or transmit your browsing activity or personal usage data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkTextSecondary,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            // Open Source Licenses & Credits
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = InkSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = InkAccent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open Source Licenses & Credits",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkTextPrimary,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CreditItem("Kotlin", "Apache 2.0")
                        CreditItem("Jetpack Compose", "Apache 2.0")
                        CreditItem("WorkManager", "Apache 2.0")
                        CreditItem("Material 3", "Apache 2.0")
                    }

                    Text(
                        text = "Third-party libraries are used under their respective open-source " +
                            "licenses. ShayLock itself is released under the MIT License.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextTertiary,
                    )

                    Button(
                        onClick = { showLicenseDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkAccentContainer,
                            contentColor = InkAccent,
                        ),
                    ) {
                        Text("View ShayLock License (MIT)")
                    }
                }
            }

            // Developer Testing Bypass
            AnimatedVisibility(visible = debugModeUnlocked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = InkSurface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = InkDanger,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Developer Testing Bypass (Debug Mode)",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkTextPrimary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Debug Reset",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = InkTextPrimary,
                                )
                                Text(
                                    text = "Reset today's limit change cooldown and PIN unlock counters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkTextTertiary,
                                )
                            }
                            Switch(
                                checked = debugToggleEnabled,
                                onCheckedChange = { enabled ->
                                    debugToggleEnabled = enabled
                                    if (enabled) {
                                        BlockController.debugResetDailyLimits()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Made with ❤️ by Infernix",
                style = MaterialTheme.typography.labelSmall,
                color = com.example.shaylock.ui.theme.InkTextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("MIT License", color = InkTextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = MIT_LICENSE_TEXT,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("Close", color = InkAccent)
                }
            },
            containerColor = InkSurface,
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    isActive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkSurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = InkTextPrimary,
        )
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isActive) InkSuccessContainer else InkDangerContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) InkSuccess else InkDanger),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isActive) "ACTIVE" else "DISABLED",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) InkOnSuccessContainer else InkOnDangerContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = InkTextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkTextTertiary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = InkAccent,
        )
    }
}

@Composable
private fun CreditItem(
    name: String,
    license: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = InkTextSecondary,
        )
        Text(
            text = license,
            style = MaterialTheme.typography.bodySmall,
            color = InkTextTertiary,
        )
    }
}
