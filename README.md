# Dimmer

A system-wide scrim overlay for Android, toggled from a Quick Settings tile.
Replicates the dim-and-blur that Android applies behind the notification shade.

Zero external dependencies — no AndroidX, no Compose, no Material.

## Environment

| | |
|---|---|
| IDE | Android Studio Quail 3 on Windows 11 |
| Build | AGP 9.3.2, Gradle version catalog (`gradle/libs.versions.toml`) |
| Kotlin | AGP 9 **built-in Kotlin**. Do NOT apply `org.jetbrains.kotlin.android` — it is incompatible with the AGP 9 DSL and will fail the build. |
| SDK | `compileSdk` / `targetSdk` = 36, `minSdk` = 34 |
| Device | Pixel 10, Android 16, USB debugging |
| git | WSL 2, credentials via Git Credential Manager on the Windows side |

Build from Studio (Ctrl+F9), not from WSL — WSL has no JDK, and installing one
would give you two Gradle daemons fighting over the same `build/` directory.

`compileSdk` uses AGP 9's block syntax, not the old assignment:

```kotlin
compileSdk { version = release(36) }
```

Staying on 36 rather than 37 is deliberate: it matches the device the scrim
reference was measured on.

## How it works

| Piece | Role |
|---|---|
| `DimmerOverlay` | One `View`, animated alpha, added to `WindowManager` as `TYPE_APPLICATION_OVERLAY`. This is the whole effect. |
| `DimmerService` | Foreground service keeping the process (and the view) alive. `specialUse` type, required on API 34+. |
| `DimmerTile` | `TileService`. The trigger. |
| `MainActivity` | Permission grant plus live sliders for alpha and blur. |

`FLAG_NOT_TOUCHABLE` makes it usable — touches pass through to the app
underneath. `FLAG_LAYOUT_NO_LIMITS` plus `fitInsetsTypes = 0` extends the
window over the bars and cutout.

Note that `TYPE_APPLICATION_OVERLAY` sits *below* core system UI. The scrim
covers the status and nav bar regions, but the clock, status icons and gesture
pill draw above it and stay undimmed. That matches the real shade, and it is a
hard ceiling rather than something you can flag your way past.

Blur uses `FLAG_BLUR_BEHIND` + `blurBehindRadius` (API 31+), gated on
`WindowManager.isCrossWindowBlurEnabled()` — false under battery saver.

## Setup on device

1. Run from Studio. App installs and opens.
2. Tap **Grant "Display over other apps"** → enable in Settings.
3. Tap **Add Quick Settings tile** → confirm the prompt.
4. Pull down the shade, tap the tile.

No signing key, no Play Console, no privacy policy. Personal sideload only.

## Matching the real scrim — NOT YET DONE

`scrimColor` and `scrimAlpha` in `DimmerOverlay.kt` are placeholders. Two
reasons you cannot just read the real values off:

- Since Android 12 the shade scrim is tinted from the Monet/dynamic-colour
  palette, not pure `#000000`. It shifts with your wallpaper.
- Alpha animates against shade expansion fraction, so the settled value is one
  point on a curve.

Solve for them instead. The scrim composites as
`result = scrim × a + background × (1 − a)`, so two known backgrounds give two
equations and a unique solution:

1. Make a pure white and a pure black full-screen PNG. Put both on the phone.
2. Clear all notifications — fewer notifications means more visible scrim below
   the shade panel.
3. Open the white image fullscreen. Pull the shade fully open and let it
   settle. Screenshot (Power + Volume Down).
4. Repeat with the black image. Same app, same wallpaper, same shade state.
5. Sample the same pixel in the dimmed region of both, well below the panel and
   away from any soft edge.
6. Per channel: `a = 1 − (white − black) / 255`, then `scrim = black / a`.

Solid fields are deliberate: blurring a uniform colour returns the same colour,
so blur drops out of the measurement and you are solving for dim alone. Tune
`blurRadiusPx` separately by eye.

Do this on the physical device with your actual wallpaper. Keep both
screenshots as the Android 16 reference.

For the animation curve, AOSP's `ScrimController` and `ScrimState` in
`frameworks/base/packages/SystemUI` are the reference implementation.

## Known limits (by design, not fixable)

- The overlay is force-hidden over permission dialogs and parts of Settings.
  Tapjacking protection, tightened in Android 12.
- Any app holding `HIDE_OVERLAY_WINDOWS` can suppress it.
- DRM/secure video surfaces may flicker or go black underneath.
- Screenshots capture the scrim.
- Panel output is unchanged — a black film, not real dimming. No OLED power
  saving, and it cannot go below the panel's minimum brightness.

## Next steps once the MVP feels right

- Track the real shade instead of a binary toggle: `AccessibilityService`
  watching `TYPE_WINDOWS_CHANGED` for the SystemUI window, mirroring its
  expansion fraction.
- Per-app rules keyed off the foreground package.
- Schedule or ambient-light-driven alpha via `SensorManager`.
- Warmth/tint: swap the solid background for a `ColorMatrixColorFilter`.

## Parked — revisit after the MVP

- **Windows network profile is set to Public.** Noticed during adb firewall
  setup; the Allow dialog offered no private option, so it was cancelled. USB
  debugging is unaffected. Fix at Settings > Network & internet > (connection) >
  Network profile type > Private. Also blocks wireless debugging until changed.
- **Android 17 update.** Holding on Android 16 until the scrim reference above
  is measured and recorded. Once `scrimColor` and `scrimAlpha` are filled in,
  the update is safe — re-measure afterward and compare. Nothing in Android 17's
  behaviour changes affects overlays, blur, tiles or foreground services.
- **Android Studio Quail 4.** Deferred until there is a committed green build to
  compare against; IDE updates often prompt an AGP upgrade that rewrites build
  files.
- **Claude Code.** Planned for v2. Will need a `CLAUDE.md` carrying the
  constraints in this README.
