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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shaylock.R
import com.example.shaylock.core.BlockPolicy
import com.example.shaylock.core.DailyLimit
import com.example.shaylock.core.formatCountdown
import com.example.shaylock.core.formatLimit
import com.example.shaylock.ui.PermissionUiState
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
 * The single dashboard / onboarding screen.
 *
 * The screen reveals itself progressively:
 *  1. only missing required permissions are shown,
 *  2. once they are granted the permission section collapses and the allowance picker appears,
 *  3. once an allowance is armed the picker gives way to the focused active dashboard.
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    permissionState: PermissionUiState,
    limit: DailyLimit,
    usedTodayMs: Long,
    limitRemainingMs: Long,
    isBlockingEffective: Boolean,
    sessionRemainingMs: Long,
    pinExtensionsRemainingToday: Int,
    selectedLimitMs: Long,
    onSelectLimit: (Long) -> Unit,
    onRequestSetLimit: (Long) -> Unit,
    onRequestDisable: () -> Unit,
    onRequestPause: () -> Unit,
    onResumeNow: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onPreviewOverlay: () -> Unit,
    onOpenAbout: () -> Unit = {},
) {
    val ready = permissionState.readyToBlock
    val isDashboard = ready && limit.isEnabled
    val showPermissions = !ready
    val showAllowanceSetup = ready && !limit.isEnabled
    val showDashboard = isDashboard

    var showLimitEditor by remember { mutableStateOf(false) }
    LaunchedEffect(limit.limitMs, limit.isEnabled) {
        if (limit.isEnabled) showLimitEditor = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
    ) {
        ScreenHeader(
            isActive = isDashboard,
            isPaused = sessionRemainingMs > 0L,
        )

        Spacer(Modifier.height(26.dp))

        StatusHero(
            permissionState = permissionState,
            limit = limit,
            usedTodayMs = usedTodayMs,
            limitRemainingMs = limitRemainingMs,
            isBlockingEffective = isBlockingEffective,
            sessionRemainingMs = sessionRemainingMs,
        )

        if (!permissionState.targetInstalled) {
            Spacer(Modifier.height(14.dp))
            InlineNotice(
                icon = Icons.Filled.Warning,
                text = "Instagram does not appear to be installed, so there is nothing to " +
                    "block yet.",
                color = InkWarning,
                container = InkWarningContainer,
            )
        }

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
                        description = "Lets ShayLock notice when Instagram is on screen and " +
                            "measure the time it is actually used.",
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
                        description = "Allows the full-screen lock screen to cover Instagram.",
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
         * Allowance setup - once both permissions are active
         * ------------------------------------------------------------------------------- */
        AnimatedVisibility(
            visible = showAllowanceSetup,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(30.dp))

                SectionLabel("DAILY ALLOWANCE")
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Choose how much Instagram is allowed each day.",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Only time Instagram is really on screen counts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextSecondary,
                )

                Spacer(Modifier.height(16.dp))

                AllowanceChips(
                    selected = selectedLimitMs,
                    onSelect = onSelectLimit,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onRequestSetLimit(selectedLimitMs) },
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
                    Text("Start blocking", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The first setup needs no PIN. Every later change needs the PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextTertiary,
                )
            }
        }

        /* ---------------------------------------------------------------------------------
         * Active dashboard
         * ------------------------------------------------------------------------------- */
        AnimatedVisibility(
            visible = showDashboard,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(280)),
        ) {
            Column {
                Spacer(Modifier.height(30.dp))

                SectionLabel("CONTROLS")
                Spacer(Modifier.height(14.dp))

                when {
                    sessionRemainingMs > 0L -> {
                        Button(
                            onClick = onResumeNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkAccent,
                                contentColor = InkOnAccent,
                            ),
                        ) {
                            Text("Resume blocking", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    isBlockingEffective && pinExtensionsRemainingToday > 0 -> {
                        Button(
                            onClick = onRequestPause,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkWarningContainer,
                                contentColor = InkOnWarningContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Pause for 15 minutes", style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "$pinExtensionsRemainingToday of " +
                                "${BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY} PIN extensions left today.",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTextTertiary,
                        )
                    }

                    isBlockingEffective -> {
                        InlineNotice(
                            icon = Icons.Filled.Warning,
                            text = "All ${BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY} PIN extensions " +
                                "are used today. Instagram stays blocked until midnight.",
                            color = InkWarning,
                            container = InkWarningContainer,
                        )
                    }
                }

                if (sessionRemainingMs > 0L || isBlockingEffective) {
                    Spacer(Modifier.height(10.dp))
                }

                ActionRow(
                    title = "Daily allowance",
                    subtitle = "Currently ${formatLimit(limit.limitMs)} per day",
                    action = if (showLimitEditor) "Close" else "Change",
                    onClick = { showLimitEditor = !showLimitEditor },
                )

                AnimatedVisibility(
                    visible = showLimitEditor,
                    enter = fadeIn(tween(200)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(240)),
                ) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        AllowanceChips(
                            selected = selectedLimitMs,
                            onSelect = onSelectLimit,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onRequestSetLimit(selectedLimitMs) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkAccentContainer,
                                contentColor = InkAccent,
                            ),
                        ) {
                            Text("Save new limit", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Saving a new limit needs your PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTextTertiary,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }

                Hairline()

                ActionRow(
                    title = "Turn off the blocker",
                    subtitle = "Removes the rule until you set it again",
                    action = "Turn off",
                    destructive = true,
                    onClick = onRequestDisable,
                )

                Spacer(Modifier.height(18.dp))

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

                Spacer(Modifier.height(6.dp))

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
            }
        }

        if (!ready) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "The Start blocking button appears once both required permissions " +
                    "are active.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextTertiary,
            )
        }

        Spacer(Modifier.height(34.dp))

        Footer(
            ready = ready,
            notificationsGranted = permissionState.notificationsGranted,
            onRequestNotifications = onRequestNotifications,
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
                text = "Strict screen-time blocker",
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
                label = "PAUSED"
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
    limit: DailyLimit,
    usedTodayMs: Long,
    limitRemainingMs: Long,
    isBlockingEffective: Boolean,
    sessionRemainingMs: Long,
) {
    val accent = when {
        !permissionState.readyToBlock &&
            permissionState.accessibilityConnecting -> InkWarning
        !permissionState.readyToBlock -> InkDanger
        sessionRemainingMs > 0L -> InkWarning
        isBlockingEffective -> InkDanger
        limit.isEnabled -> InkSuccess
        else -> InkAccent
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
            detail = "Grant the missing permissions below to start protecting Instagram."
        }

        sessionRemainingMs > 0L -> {
            eyebrow = "PAUSED"
            value = formatCountdown(sessionRemainingMs)
            suffix = "left"
            detail = "Blocking resumes automatically when the pause ends."
        }

        isBlockingEffective -> {
            eyebrow = "BLOCKED"
            value = "Locked"
            suffix = "until midnight"
            detail = "Today's ${formatLimit(limit.limitMs)} allowance is used up."
        }

        limit.isEnabled -> {
            eyebrow = "PROTECTED"
            value = formatLimit(limitRemainingMs)
            suffix = "left today"
            detail = "Only time Instagram is really on screen counts."
        }

        else -> {
            eyebrow = "READY"
            value = "No limit yet"
            suffix = ""
            detail = "Choose a daily allowance below to arm the blocker."
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

            if (limit.isEnabled && limit.limitMs > 0L) {
                val progress by animateFloatAsState(
                    targetValue = limit.usedFraction(usedTodayMs),
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
                        text = "${formatLimit(usedTodayMs)} used",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTextTertiary,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatLimit(limit.limitMs)} per day",
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
                            .background(if (isSelected) InkAccentContainer else InkSurface)
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
private fun PartnerRow(
    partnerPhone: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSurface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shaylock_mark),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Accountability partner",
                style = MaterialTheme.typography.titleSmall,
                color = InkTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = partnerPhone ?: "Add the person who approves unlock codes.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = if (partnerPhone == null) "Add" else "Change",
            style = MaterialTheme.typography.labelLarge,
            color = InkAccent,
        )
    }
}

@Composable
private fun PartnerDialog(
    partnerPhone: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by remember(partnerPhone) { mutableStateOf(partnerPhone.orEmpty()) }
    val normalized = phone.trim().takeIf { it.isNotEmpty() }
    val isChange = partnerPhone != null

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(InkSurface)
                .padding(24.dp),
        ) {
            Text(
                text = if (isChange) "Change partner" else "Add accountability partner",
                style = MaterialTheme.typography.headlineSmall,
                color = InkTextPrimary,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Unlocks and settings changes need a one-time code sent to this number.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
            )

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("+1 555 123 4567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = InkAccent,
                    unfocusedBorderColor = InkHairline,
                    focusedLabelColor = InkAccent,
                    unfocusedLabelColor = InkTextTertiary,
                    focusedTextColor = InkTextPrimary,
                    unfocusedTextColor = InkTextPrimary,
                    cursorColor = InkAccent,
                    focusedContainerColor = InkSurfaceHigh,
                    unfocusedContainerColor = InkSurfaceHigh,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when {
                    phone.isNotBlank() && normalized == null ->
                        "Include a country code, for example +1 555 123 4567."
                    isChange ->
                        "Changing an existing partner needs their approval."
                    else ->
                        "The first partner can be saved directly. Later changes need a code."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (phone.isNotBlank() && normalized == null) InkDanger else InkTextTertiary,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = InkTextSecondary),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { normalized?.let(onSave) },
                    enabled = normalized != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                        disabledContainerColor = InkSurfaceHigh,
                        disabledContentColor = InkTextTertiary,
                    ),
                ) {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
            "Only the time Instagram is really on screen counts.",
            "When the allowance runs out, a strict lock screen appears.",
            "Your PIN opens Instagram for five minutes, up to " +
                "${BlockPolicy.MAX_PIN_EXTENSIONS_PER_DAY} times a day.",
            "Everything resets at midnight; the limit itself stays.",
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
