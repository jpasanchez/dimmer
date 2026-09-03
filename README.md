# Dimmer

A full-screen dimming overlay for Android, toggled from a Quick Settings tile.
Aims to reproduce the way the Android 16 notification shade makes the content
behind it effectively vanish.

Zero external dependencies — no AndroidX, no Compose, no Material.

## The central finding

An ordinary `TYPE_APPLICATION_OVERLAY` window **cannot** dim past 80%.

Android 12 added tapjacking protection: overlay windows are untrusted, and one
that sets `FLAG_NOT_TOUCHABLE` is capped at
`InputManager.getMaximumObscuringOpacityForTouch()` — 0.8 by default. Setting a
higher alpha changes nothing. Measured on a Pixel 10: exactly 20.0% content
throughput on every channel regardless of requested alpha.

Covering the screen opaquely *while letting touches through* is precisely what
that protection exists to prevent.

`TYPE_ACCESSIBILITY_OVERLAY` is a **trusted** window and is exempt. Same
approach, measured 1.2% throughput. This is why every third-party dimmer app
asks for accessibility access — it is not a convenience, it is the only route.

## Environment

| | |
|---|---|
| IDE | Android Studio Quail 3 on Windows 11 |
| Build | AGP 9.3.2, Gradle version catalog (`gradle/libs.versions.toml`) |
| Kotlin | AGP 9 **built-in Kotlin**. Do NOT apply `org.jetbrains.kotlin.android` — incompatible with the AGP 9 DSL. |
| SDK | `compileSdk` / `targetSdk` = 36, `minSdk` = 34 |
| Device | Pixel 10, Android 16, USB debugging |
| git | WSL 2; credentials via Git Credential Manager on the Windows side |

Build from Studio (Ctrl+F9), not WSL — WSL has no JDK, and installing one gives
you two Gradle daemons fighting over the same `build/` directory.

`compileSdk` uses AGP 9 block syntax: `compileSdk { version = release(36) }`.
Staying on 36 rather than 37 is deliberate — it matches the device the scrim
reference was measured on.

## Architecture

| File | Role |
|---|---|
| `DimmerAccessibilityService` | Owns the scrim. Being an accessibility service is what defeats the opacity cap. The system keeps it bound, so no foreground service is needed. |
| `DimmerOverlay` | One `View` with animated alpha, added as `TYPE_ACCESSIBILITY_OVERLAY`. Must be created from the service context — WindowManager rejects that window type from anyone else. |
| `DimmerTile` | `TileService`. The trigger. |
| `MainActivity` | Setup and the alpha slider. |

`FLAG_NOT_TOUCHABLE` is what makes it usable — touches pass to the app beneath.
`FLAG_LAYOUT_NO_LIMITS` plus `fitInsetsTypes = 0` extends it over the bars and
cutout. SystemUI's own bar content still draws above, so the clock and gesture
pill stay undimmed; that matches the real shade and is not adjustable.

Blur was removed. At ~98% opacity almost nothing survives to blur, so
`FLAG_BLUR_BEHIND` bought nothing and cost a capability check.

## Setup on device

1. Run from Studio.
2. Settings → Accessibility → Downloaded apps → Dimmer → on.
   If Android says "Restricted setting", clear the sideload gate first:
   App info → ⋮ → **Allow restricted settings**, or
   `adb shell appops set dev.dimmer ACCESS_RESTRICTED_SETTINGS allow`
3. In the app: **Add Quick Settings tile**.
4. Pull down the shade, tap the tile.

**Escape hatch.** If the screen is stuck dark and you cannot navigate:

```
adb shell settings put secure enabled_accessibility_services ""
```

## Measured values

Calibration solves `result = scrim × a + background × (1 − a)`. Two known
backgrounds give two equations and a unique solution per channel.

Native Android 16 shade, fully expanded, measured over two backgrounds:

**scrim ≈ `(231, 188, 181)`, alpha ≈ 0.98**

Predictions land within 1–2 levels on all six channel samples.

That colour comes from the wallpaper's Monet palette, so it drifts when the
wallpaper changes. Deriving it from `android.R.color.system_*` at runtime is
the open task.

To re-measure: screenshot the target effect over a pure white full-screen image
and again over pure black, sample the same pixel in both, then per channel
`a = 1 − (white − black) / 255` and `scrim = black / a`. Solid fields are
deliberate — blurring a uniform colour returns the same colour, so blur drops
out and you solve for dim alone.

## Known limits

- Overlays are force-hidden over permission dialogs and parts of Settings.
- DRM/secure video surfaces may flicker or go black underneath.
- Screenshots capture the scrim.
- Panel output is unchanged — a black film, not real dimming. No OLED power
  saving, and it cannot go below the panel's minimum brightness.
- Accessibility overlay z-order versus the notification shade is **untested**.
  If the scrim covers the shade, the tile is unreachable while dimming is on.

## Next

- Derive the scrim colour from the Monet palette instead of hardcoding.
- Track the real shade rather than a binary toggle: watch `TYPE_WINDOWS_CHANGED`
  for the SystemUI window and mirror its expansion fraction.
- Per-app rules keyed off the foreground package.
- Warmth/tint via `ColorMatrixColorFilter`.

## Parked

- **Windows network profile is set to Public.** Noticed during adb firewall
  setup; the Allow dialog offered no private option, so it was cancelled. USB
  debugging is unaffected. Settings → Network & internet → (connection) →
  Network profile type → Private. Also blocks wireless debugging.
- **Android 17 update.** Held on 16 until the scrim reference was measured —
  now done, so the update is safe. Re-measure afterward and compare. Nothing in
  Android 17's behaviour changes affects overlays, tiles, or accessibility
  services.
- **Android Studio Quail 4.** Deferred; IDE updates often prompt an AGP upgrade
  that rewrites build files.
- **Claude Code.** Planned for v3. Will need a `CLAUDE.md` carrying the
  constraints in this README.
