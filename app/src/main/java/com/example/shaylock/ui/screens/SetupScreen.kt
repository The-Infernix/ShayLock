package com.example.shaylock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaylock.R
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.TrackedApp
import com.example.shaylock.core.TrackedAppState
import com.example.shaylock.core.formatCountdown
import com.example.shaylock.core.formatLimit
import com.example.shaylock.ui.PermissionUiState
import com.example.shaylock.ui.components.AppIcon
import com.example.shaylock.ui.theme.InkAccent
import com.example.shaylock.ui.theme.InkAccentContainer
import com.example.shaylock.ui.theme.InkDanger
import com.example.shaylock.ui.theme.InkDangerContainer
import com.example.shaylock.ui.theme.InkHairline
import com.example.shaylock.ui.theme.InkOnAccent
import com.example.shaylock.ui.theme.InkOnDangerContainer
import com.example.shaylock.ui.theme.InkOnWarningContainer
import com.example.shaylock.ui.theme.InkSuccess
import com.example.shaylock.ui.theme.InkSuccessContainer
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary
import com.example.shaylock.ui.theme.InkWarning
import com.example.shaylock.ui.theme.InkWarningContainer

/**
 * The dashboard / onboarding screen for the multi-app blocker.
 *
 * The screen reveals itself progressively:
 *  1. missing required permissions are shown first,
 *  2. the protected app list is always available so apps can be picked before arming,
 *  3. once protection is on, every app shows its own progress bar, remaining time and overrides.
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    permissionState: PermissionUiState,
    apps: List<TrackedAppState>,
    blockerEnabled: Boolean,
    isUsingDefaultPin: Boolean,
    onAddApps: () -> Unit,
    onRequestSetLimit: (packageName: String, limitMs: Long) -> Unit,
    onRequestRemove: (packageName: String) -> Unit,
    onRequestUnlock: (packageName: String) -> Unit,
    onResumeBlocking: (packageName: String) -> Unit,
    onEnableProtection: () -> Unit,
    onRequestDisable: () -> Unit,
    onManagePin: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onPreviewOverlay: () -> Unit,
    onOpenAbout: () -> Unit = {},
) {
    val ready = permissionState.readyToBlock
    val anyOverride = apps.any { it.isOverrideActive }
    val blockedCount = apps.count { it.isBlocked }

    val showPermissions = !ready
    val showApps = ready || apps.isNotEmpty()
    val showStartProtection = ready && apps.isNotEmpty() && !blockerEnabled
    val showControls = blockerEnabled && apps.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
    ) {
        ScreenHeader(
            isActive = blockerEnabled && apps.isNotEmpty(),
            isPaused = anyOverride,
        )

        Spacer(Modifier.height(26.dp))

        StatusHero(
            permissionState = permissionState,
            apps = apps,
            blockerEnabled = blockerEnabled,
            blockedCount = blockedCount,
            hasOverride = anyOverride,
        )

        /* ---------------------------------------------------------------------------------
         * Permissions - only while something is missing
         * ------------------------------------------------------------------------------- */
        AnimatedVisibility(
            visible = showPermissions,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(30.dp))

                SectionLabel("GET PROTECTED")
                Spacer(Modifier.height(10.dp))

                if (!permissionState.accessibilityEnabled) {
                    PermissionRow(
                        icon = Icons.Filled.Settings,
                        title = "Accessibility service",
                        description = "Lets ShayLock notice when a protected app is on screen " +
                            "and measure the time it is actually used.",
                        status = if (permissionState.accessibilityConnecting) {
                            PermissionRowStatus.CONNECTING
                        } else {
                            PermissionRowStatus.REQUIRED
                        },
                        steps = listOf(
                            "Open Settings → Accessibility.",
                            "Tap ShayLock under Installed services.",
                            "Turn on \"Use service\" and confirm the prompt.",
                        ),
                        actionLabel = "Open settings",
                        onAction = onOpenAccessibilitySettings,
                    )

                    if (!permissionState.overlayGranted ||
                        !permissionState.notificationsGranted
                    ) {
                        Hairline()
                    }
                }

                if (!permissionState.overlayGranted) {
                    PermissionRow(
                        icon = Icons.Filled.Warning,
                        title = "Display over other apps",
                        description = "Allows the full-screen lock screen to cover a blocked app.",
                        status = PermissionRowStatus.REQUIRED,
                        steps = listOf(
                            "Open Settings → Apps → ShayLock.",
                            "Tap \"Display over other apps\".",
                            "Turn the switch on.",
                        ),
                        actionLabel = "Open settings",
                        onAction = onOpenOverlaySettings,
                    )

                    if (!permissionState.notificationsGranted) {
                        Hairline()
                    }
                }

                if (!permissionState.notificationsGranted) {
                    PermissionRow(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        description = "Optional. Shows the ongoing protection notification.",
                        status = PermissionRowStatus.OPTIONAL,
                        steps = listOf("Tap \"Allow\" on the Android prompt."),
                        actionLabel = "Allow",
                        onAction = onRequestNotifications,
                    )
                }
            }
        }

        /* ---------------------------------------------------------------------------------
         * Protected apps
         * ------------------------------------------------------------------------------- */
        AnimatedVisibility(
            visible = showApps,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(30.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("PROTECTED APPS")
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onAddApps,
                        colors = ButtonDefaults.textButtonColors(contentColor = InkAccent),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add apps", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (apps.isEmpty()) {
                    EmptyAppsCard(onAddApps = onAddApps)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        apps.forEach { state ->
                            TrackedAppCard(
                                state = state,
                                onRequestSetLimit = onRequestSetLimit,
                                onRequestRemove = onRequestRemove,
                                onRequestUnlock = onRequestUnlock,
                                onResumeBlocking = onResumeBlocking,
                            )
                        }
                    }
                }
            }
        }

        /* ---------------------------------------------------------------------------------
         * Arm / disarm
         * ------------------------------------------------------------------------------- */
        AnimatedVisibility(
            visible = showStartProtection,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onEnableProtection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                    ),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_shaylock_mark),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Start protection", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Starting protection is free. Every app keeps its own allowance, " +
                        "and disabling everything later needs your PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextTertiary,
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(30.dp))

                SectionLabel("CONTROLS")
                Spacer(Modifier.height(12.dp))

                if (isUsingDefaultPin) {
                    InlineNotice(
                        icon = Icons.Filled.Warning,
                        text = "The default PIN 2310 is still active. Set a custom PIN to " +
                            "protect your overrides and settings.",
                        color = InkWarning,
                        container = InkWarningContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                ActionRow(
                    title = "App PIN",
                    subtitle = if (isUsingDefaultPin) {
                        "Default PIN in use - tap to set your own"
                    } else {
                        "Custom PIN protects every override and setting"
                    },
                    action = "Change",
                    onClick = onManagePin,
                )

                Hairline()

                ActionRow(
                    title = "Turn off protection",
                    subtitle = "Stops blocking every protected app",
                    action = "Turn off",
                    destructive = true,
                    onClick = onRequestDisable,
                )

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onPreviewOverlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_shaylock_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(0.7f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Preview the lock screen",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkTextSecondary,
                    )
                }
            }
        }

        if (!ready && apps.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Protection starts once the accessibility and overlay permissions are " +
                    "active.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextTertiary,
            )
        }

        if (!ready && apps.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Grant the missing permissions, then tap \"Add apps\" to choose what " +
                    "ShayLock should protect.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextTertiary,
            )
        }

        Spacer(Modifier.height(30.dp))

        TextButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = InkTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "About & Settings Info",
                style = MaterialTheme.typography.labelLarge,
                color = InkTextSecondary,
            )
        }

        Spacer(Modifier.height(18.dp))

        Footer(
            ready = ready,
            notificationsGranted = permissionState.notificationsGranted,
            onRequestNotifications = onRequestNotifications,
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Made with ❤️ by Infernix",
            style = MaterialTheme.typography.labelSmall,
            color = InkTextTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/* =============================================================================================
 * Header and hero
 * =========================================================================================== */

@Composable
private fun ScreenHeader(isActive: Boolean, isPaused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InkAccentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shaylock_mark),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ShayLock",
                style = MaterialTheme.typography.titleMedium,
                color = InkTextPrimary,
            )
            Text(
                text = "Multi-app screen-time blocker",
                style = MaterialTheme.typography.labelSmall,
                color = InkTextTertiary,
                letterSpacing = 0.4.sp,
            )
        }

        val label: String
        val container: Color
        val content: Color
        when {
            isPaused -> {
                label = "UNLOCKED"
                container = InkWarningContainer
                content = InkOnWarningContainer
            }

            isActive -> {
                label = "ACTIVE"
                container = InkSuccessContainer
                content = InkSuccess
            }

            else -> {
                label = "SETUP"
                container = InkSurfaceHigh
                content = InkTextSecondary
            }
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(content),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

@Composable
private fun StatusHero(
    permissionState: PermissionUiState,
    apps: List<TrackedAppState>,
    blockerEnabled: Boolean,
    blockedCount: Int,
    hasOverride: Boolean,
) {
    val totalLimit = apps.sumOf { it.limitMs }
    val totalUsed = apps.sumOf { it.usedMs }
    val totalRemaining = apps.sumOf { it.remainingMs }
    val maxOverrideRemaining = apps.maxOfOrNull { it.overrideRemainingMs } ?: 0L

    val accent = when {
        !permissionState.readyToBlock &&
            permissionState.accessibilityConnecting -> InkWarning
        !permissionState.readyToBlock -> InkDanger
        !blockerEnabled -> InkAccent
        blockedCount > 0 -> InkDanger
        hasOverride -> InkWarning
        else -> InkSuccess
    }
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(400),
        label = "heroAccent",
    )

    val eyebrow: String
    val value: String
    val suffix: String
    val detail: String
    when {
        !permissionState.readyToBlock && permissionState.accessibilityConnecting -> {
            eyebrow = "CONNECTING"
            value = "Almost there"
            suffix = ""
            detail = "Android is finishing the accessibility connection."
        }

        !permissionState.readyToBlock -> {
            eyebrow = "NOT PROTECTED"
            value = "Setup required"
            suffix = ""
            detail = "Grant the missing permissions below to start protecting your apps."
        }

        apps.isEmpty() -> {
            eyebrow = "READY"
            value = "No apps yet"
            suffix = ""
            detail = "Choose the apps you want to protect. Each one gets its own daily timer."
        }

        !blockerEnabled -> {
            eyebrow = "PAUSED"
            value = "${apps.size} ready"
            suffix = if (apps.size == 1) "app" else "apps"
            detail = "Start protection to enforce the allowances you configured."
        }

        hasOverride -> {
            eyebrow = "TEMPORARY UNLOCK"
            value = formatCountdown(maxOverrideRemaining)
            suffix = "left"
            detail = "Only the unlocked app is usable; the block returns automatically."
        }

        blockedCount > 0 -> {
            eyebrow = "BLOCKED"
            value = if (blockedCount == 1) "1 app locked" else "$blockedCount apps locked"
            suffix = ""
            detail = "The daily allowance is used up. Everything resets at midnight."
        }

        else -> {
            eyebrow = "PROTECTED"
            value = formatLimit(totalRemaining)
            suffix = "left today"
            detail = "Across ${apps.size} protected ${if (apps.size == 1) "app" else "apps"}. " +
                "Only foreground time counts."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(InkSurface),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(animatedAccent),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedAccent,
                    letterSpacing = 1.4.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = InkTextPrimary,
                )
                if (suffix.isNotEmpty()) {
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelLarge,
                        color = InkTextSecondary,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
            )

            if (apps.isNotEmpty() && totalLimit > 0L) {
                val progress by animateFloatAsState(
                    targetValue = (totalUsed.toFloat() / totalLimit).coerceIn(0f, 1f),
                    animationSpec = tween(600),
                    label = "usageProgress",
                )

                Spacer(Modifier.height(20.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = animatedAccent,
                    trackColor = InkHairline,
                )

                Spacer(Modifier.height(10.dp))

                Row {
                    Text(
                        text = "${formatLimit(totalUsed)} used",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTextTertiary,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatLimit(totalLimit)} total per day",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTextTertiary,
                        letterSpacing = 0.3.sp,
                    )
                }
            }
        }
    }
}

/* =============================================================================================
 * Protected app cards
 * =========================================================================================== */

@Composable
private fun EmptyAppsCard(onAddApps: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InkAccentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = InkAccent,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "No apps protected yet",
            style = MaterialTheme.typography.titleSmall,
            color = InkTextPrimary,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Pick any installed app. ShayLock gives each one an independent daily " +
                "allowance and keeps its own usage counter.",
            style = MaterialTheme.typography.bodySmall,
            color = InkTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onAddApps,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkAccent,
                contentColor = InkOnAccent,
            ),
        ) {
            Text("Choose apps", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TrackedAppCard(
    state: TrackedAppState,
    onRequestSetLimit: (String, Long) -> Unit,
    onRequestRemove: (String) -> Unit,
    onRequestUnlock: (String) -> Unit,
    onResumeBlocking: (String) -> Unit,
) {
    var showLimitEditor by remember(state.packageName) { mutableStateOf(false) }
    var selectedLimitMs by remember(state.packageName, state.limitMs) {
        mutableLongStateOf(state.limitMs)
    }

    val status = when {
        !state.isBlockerEnabled -> AppStatus.OFF
        state.isBlocked -> AppStatus.BLOCKED
        state.isOverrideActive -> AppStatus.UNLOCKED
        else -> AppStatus.PROTECTED
    }
    val progressColor = when (status) {
        AppStatus.OFF -> InkTextTertiary
        AppStatus.BLOCKED -> InkDanger
        AppStatus.UNLOCKED -> InkWarning
        AppStatus.PROTECTED -> InkAccent
    }
    val progress by animateFloatAsState(
        targetValue = state.usedFraction,
        animationSpec = tween(500),
        label = "appProgress_${state.packageName}",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkSurface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                packageName = state.packageName,
                label = state.label,
                modifier = Modifier.size(44.dp),
                cornerRadius = 13.dp,
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkTextPrimary,
                )
                Text(
                    text = state.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTextTertiary,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(8.dp))

            AppStatusChip(status)
        }

        Spacer(Modifier.height(14.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50)),
            color = progressColor,
            trackColor = InkHairline,
        )

        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    state.isOverrideActive ->
                        "${formatCountdown(state.overrideRemainingMs)} unlock left"
                    state.isBlocked -> "Daily allowance used up"
                    !state.isBlockerEnabled -> "${formatLimit(state.remainingMs)} set aside"
                    else -> "${formatLimit(state.remainingMs)} left today"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state.isBlocked) InkDanger else InkTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatLimit(state.usedMs)} / ${formatLimit(state.limitMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = InkTextTertiary,
            )
        }

        if (state.isBlockerEnabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.overridesRemainingToday} of " +
                    "${BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY} PIN unlocks left today",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.overridesRemainingToday == 0) InkDanger else InkTextTertiary,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                state.isOverrideActive -> {
                    TextButton(
                        onClick = { onResumeBlocking(state.packageName) },
                        colors = ButtonDefaults.textButtonColors(contentColor = InkAccent),
                    ) {
                        Text("Resume blocking", style = MaterialTheme.typography.labelLarge)
                    }
                }

                state.isBlocked -> {
                    Button(
                        onClick = { onRequestUnlock(state.packageName) },
                        enabled = state.overridesRemainingToday > 0,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkWarningContainer,
                            contentColor = InkOnWarningContainer,
                            disabledContainerColor = InkSurfaceHigh,
                            disabledContentColor = InkTextTertiary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.overridesRemainingToday > 0) {
                                "Unlock 5 min"
                            } else {
                                "No unlocks left"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = { showLimitEditor = !showLimitEditor },
                enabled = state.canChangeLimit,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = InkAccent,
                    disabledContentColor = InkTextTertiary,
                ),
            ) {
                Text(
                    text = if (state.canChangeLimit) "Limit" else "Limit locked",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            TextButton(
                onClick = { onRequestRemove(state.packageName) },
                colors = ButtonDefaults.textButtonColors(contentColor = InkDanger),
            ) {
                Text("Remove", style = MaterialTheme.typography.labelLarge)
            }
        }

        AnimatedVisibility(
            visible = showLimitEditor && state.canChangeLimit,
            enter = fadeIn(tween(200)) + expandVertically(tween(260)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(240)),
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                AllowanceChips(
                    selected = selectedLimitMs,
                    onSelect = { selectedLimitMs = it },
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onRequestSetLimit(state.packageName, selectedLimitMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccentContainer,
                        contentColor = InkAccent,
                    ),
                ) {
                    Text("Save allowance", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.isBlockerEnabled) {
                        "Saving a new allowance needs your PIN and is limited to once per app " +
                            "per day."
                    } else {
                        "Saving a new allowance before protection starts is free."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextTertiary,
                )
            }
        }
    }
}

private enum class AppStatus(val label: String) {
    PROTECTED("PROTECTED"),
    BLOCKED("BLOCKED"),
    UNLOCKED("UNLOCKED"),
    OFF("OFF"),
}

@Composable
private fun AppStatusChip(status: AppStatus) {
    val container = when (status) {
        AppStatus.PROTECTED -> InkSuccessContainer
        AppStatus.BLOCKED -> InkDangerContainer
        AppStatus.UNLOCKED -> InkWarningContainer
        AppStatus.OFF -> InkSurfaceHigh
    }
    val content = when (status) {
        AppStatus.PROTECTED -> InkSuccess
        AppStatus.BLOCKED -> InkOnDangerContainer
        AppStatus.UNLOCKED -> InkOnWarningContainer
        AppStatus.OFF -> InkTextSecondary
    }

    Text(
        text = status.label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/* =============================================================================================
 * Permission rows
 * =========================================================================================== */

private enum class PermissionRowStatus(val label: String) {
    CONNECTING("Connecting…"),
    REQUIRED("Action required"),
    OPTIONAL("Optional"),
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    status: PermissionRowStatus,
    steps: List<String>,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val statusColor = when (status) {
        PermissionRowStatus.CONNECTING -> InkWarning
        PermissionRowStatus.REQUIRED -> InkDanger
        PermissionRowStatus.OPTIONAL -> InkTextTertiary
    }
    val iconContainer = when (status) {
        PermissionRowStatus.CONNECTING -> InkWarningContainer
        PermissionRowStatus.REQUIRED -> InkDangerContainer
        PermissionRowStatus.OPTIONAL -> InkSurfaceHigh
    }
    val iconContent = when (status) {
        PermissionRowStatus.CONNECTING -> InkOnWarningContainer
        PermissionRowStatus.REQUIRED -> InkOnDangerContainer
        PermissionRowStatus.OPTIONAL -> InkTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContent,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkTextPrimary,
                )
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    letterSpacing = 0.5.sp,
                )
            }

            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = InkAccent),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = InkTextSecondary,
            modifier = Modifier.padding(start = 50.dp),
        )

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(start = 50.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            steps.forEachIndexed { index, step ->
                Row {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextTertiary,
                    )
                }
            }
        }
    }
}

/* =============================================================================================
 * Allowance editor
 * =========================================================================================== */

@Composable
private fun AllowanceChips(selected: Long, onSelect: (Long) -> Unit) {
    val options = BlockPolicy.DAILY_LIMIT_OPTIONS

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val isSelected = selected == option
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) InkAccentContainer else InkSurfaceHigh)
                            .clickable(role = Role.RadioButton) { onSelect(option) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = InkAccent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = formatLimit(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) InkAccent else InkTextSecondary,
                        )
                    }
                }
                repeat(2 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/* =============================================================================================
 * Dashboard action rows and footer
 * =========================================================================================== */

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) InkDanger else InkTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) InkDanger else InkAccent,
        )
    }
}

@Composable
private fun Footer(
    ready: Boolean,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    Column {
        Hairline()
        Spacer(Modifier.height(22.dp))

        Text(
            text = "HOW IT WORKS",
            style = MaterialTheme.typography.labelMedium,
            color = InkTextTertiary,
            letterSpacing = 1.4.sp,
        )

        Spacer(Modifier.height(12.dp))

        listOf(
            "Pick any installed app; each one gets its own daily allowance.",
            "Only time an app is really on screen counts against that app.",
            "When an app's allowance runs out, a strict lock screen appears.",
            "Your PIN opens one app for five minutes, " +
                "${BlockPolicy.MAX_OVERRIDES_PER_APP_PER_DAY} times per app each day.",
            "Everything resets at midnight; the allowances themselves stay.",
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(InkTextTertiary),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextTertiary,
                )
            }
        }

        if (ready && !notificationsGranted) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onRequestNotifications,
                colors = ButtonDefaults.textButtonColors(contentColor = InkTextSecondary),
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable protection alerts", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/* =============================================================================================
 * Small primitives
 * =========================================================================================== */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = InkTextTertiary,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun Hairline() {
    HorizontalDivider(color = InkHairline)
}

@Composable
private fun InlineNotice(
    icon: ImageVector,
    text: String,
    color: Color,
    container: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}
