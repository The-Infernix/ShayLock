# ShayLock - Project History & Context

This document captures the full history of development up to this point to preserve context.

## Project Overview
ShayLock is an Android app (Kotlin, Jetpack Compose, Material 3) that strictly limits Instagram usage. 
- Blocks `com.instagram.android` and `com.instagram.lite`
- Uses AccessibilityService to track usage only while app is in foreground
- Full-screen overlay lock when daily allowance exhausted
- Uses hardcoded PIN (2310) for unlocks/extensions
- 3 PIN-based extensions per day (each unlock/pause consumes 1)
- Allowances: 10s (test), 15min, 30min, 1h

## Key Architecture
- `core/BlockPolicy.kt` - PIN (2310), blocked packages, durations, allowance options
- `core/BlockController.kt` - Main state, SharedPreferences persistence, extension counting
- `core/DailyLimit.kt` - Allowance tracking, midnight rollover
- `service/BlockerAccessibilityService.kt` - Watches window changes, charges usage
- `service/OverlayService.kt`/`OverlayWindowHost.kt` - Lock screen overlay
- `ui/screens/SetupScreen.kt` - Setup/dashboard UI
- `ui/screens/OverlayScreen.kt` - Lock screen UI

## Development History

### Phase 1: Initial Build
- Built prototype with basic blocking, daily allowance
- Added hardcoded PIN system
- Accessibility service + overlay

### Phase 2: Testing & Fixes
- Added 10 second option for testing
- First setup didn't require PIN
- Fixed counting bug (Reels weren't being charged properly)
- Started UI polish

### Phase 3: Accountability Partner System (Major Rewrite)
- Removed hardcoded PIN
- Implemented WhatsApp/SMS deep-linking for one-time codes
- Partner approval required for all protected actions
- Added service resilience, Instagram-style dark UI
- This was a big security-focused rewrite

### Phase 4: Revert to Simpler Model
- Compile errors with accountability system
- Reverted back to hardcoded PIN `2310`
- Removed accountability UI/code
- Switched to using "ShayLock Secure Instagram Blocker Logo.png" as app logo
- Removed shield icon
- Limited allowance options to max 1 hour
- Removed "reset today's usage" option
- Limited PIN extensions to 3 per day

### Current State (as of last commit)
- PIN: Hardcoded "2310" (4 digits) in BlockPolicy
- Extensions: 3 per day shared by 5-min unlock and 15-min pause
- Allowances: 10s, 15m, 30m, 1h
- No accountability partner system
- No reset usage option
- Logo used as app icon
- UI polished per UI_POLISH_PLAN.md

## Open Questions / TODO
- Security: Same PIN for everything is bypassable (can turn off blocker or raise limit instantly)
- Options discussed:
  1. User-set 6-digit PIN + throttling + delayed changes (10min for allowances, 24h for turning off) - RECOMMENDED
  2. Separate unlock PIN from off-device recovery key
  3. Real partner/backend approval
- README.md is stale (still describes accountability partner flow)

## Files of Interest
- Session logs: session-ses_ea18.md, session-ses_e984.md, session-ses_e97d.md, session-ses_e963.md
- Full chat history: freebuff-chat-2026-10-23T18-13-20.371Z.md
- UI plan: UI_POLISH_PLAN.md

## Last User Request
"well first let's push everything into git hub and also write md file like opencode.md so that we can have a knowldege what had hapeened until now so that we don't get to waste the context window"
