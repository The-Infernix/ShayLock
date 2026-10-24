package com.example.shaylock.ui.theme

import androidx.compose.ui.graphics.Color

/* ---------------------------------------------------------------------------------------------
 * ShayLock ink palette.
 *
 * Dark-only by design: a near-black gradient with warm graphite surfaces. Accents are used
 * sparingly so status colour always carries meaning.
 * ------------------------------------------------------------------------------------------- */

val InkTop = Color(0xFF08080A)
val InkBottom = Color(0xFF0B0B0F)

val InkSurface = Color(0xFF16161A)
val InkSurfaceHigh = Color(0xFF1F1F25)
val InkSurfacePressed = Color(0xFF292930)
val InkHairline = Color(0xFF2A2A32)
val InkHairlineSoft = Color(0xFF1F1F24)

val InkTextPrimary = Color(0xFFF5F5F5)
val InkTextSecondary = Color(0xFF9A9AA5)
val InkTextTertiary = Color(0xFF6C6C76)

val InkAccent = Color(0xFF7C7CFF)
val InkAccentStrong = Color(0xFF6668FF)
val InkAccentContainer = Color(0xFF1C1C2C)
val InkAccentSoft = Color(0xFFE3E4FF)
val InkOnAccent = Color(0xFF0A0A12)

val InkSuccess = Color(0xFF5BD69A)
val InkSuccessContainer = Color(0xFF11251C)
val InkOnSuccessContainer = Color(0xFFB8F5D6)

val InkWarning = Color(0xFFF2C879)
val InkWarningContainer = Color(0xFF2A2113)
val InkOnWarningContainer = Color(0xFFF9E4B7)

val InkDanger = Color(0xFFFF7A7A)
val InkDangerContainer = Color(0xFF2A1416)
val InkOnDangerContainer = Color(0xFFFFD9D9)

val InkScrim = Color(0xFF000000)

/* ---------------------------------------------------------------------------------------------
 * Lock-screen aliases. The overlay intentionally shares the same ink palette as the dashboard.
 * ------------------------------------------------------------------------------------------- */

val OverlayBackground = InkTop
val OverlayBackgroundAlt = InkBottom
val OverlaySurface = InkSurface
val OverlaySurfaceHigh = InkSurfaceHigh
val OverlaySurfacePressed = InkSurfacePressed
val OverlayBorder = InkHairline

val OverlayTextPrimary = InkTextPrimary
val OverlayTextSecondary = InkTextSecondary
val OverlayTextTertiary = InkTextTertiary

val OverlayAccent = InkAccent
val OverlayError = InkDanger
val OverlayErrorContainer = InkDangerContainer
val OverlaySuccess = InkSuccess
val OverlayWarning = InkWarning
