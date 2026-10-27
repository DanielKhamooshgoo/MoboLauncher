# Mobo Launcher (prototype 0.1.0)

Kotlin + Jetpack Compose launcher prototype for an Android TV box. Original design; no Apple assets.

## Status (read first)
- **Not compiled. Not tested.** Written without an Android SDK or network access to Google Maven.
- Dependency versions (AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, Compose BOM 2024.06.00,
  Gradle 8.7, activity-compose 1.9.0, core-ktx 1.13.1) are chosen from memory as a compatible set
  and were **not verified** against Google Maven. Expect to adjust them if sync fails.
- Nothing here has been run on the Mobo box or in an emulator.

## What it does
- Wallpaper (4 bundled gradients or a user image via the system document picker), light/dark theme.
- App grid of apps with a TV or regular launcher entry; Dock of favorites; Settings screen.
- D-pad / OK / mouse hover and click / keyboard focus. Animation intensity: Off, Low, Normal.
- Preferences in SharedPreferences. No global Android settings are changed. No extra permissions.
- Package visibility: manifest `<queries>` for MAIN + LEANBACK_LAUNCHER and MAIN + LAUNCHER only.
- **Not** a default Home app: the HOME category is deliberately absent.
- Back at the home screen exits the app (normal app behavior).

## Build (on your computer)
1. Install JDK 17 and Android Studio (or the Android SDK + Gradle 8.7).
2. Open this folder in Android Studio and let Gradle sync. Fix version errors if any.
3. If building from a terminal, generate the wrapper once: `gradle wrapper --gradle-version 8.7`,
   then `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).
4. Output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, installable).

## Test in an emulator first
Create an Android TV (1080p) AVD with an Android 12 (API 31) image, run the app from Android Studio.
Check: grid loads, D-pad focus, OK launches an app, Back exits, Settings, wallpaper, Dock toggles.

## Physical box (only after explicit approval)
Not done. Before any install: agree on the method, expected behavior, how to exit (Back),
how to uninstall (Settings > Apps > Mobo Launcher > Uninstall, or `adb uninstall app.mobolauncher`),
and recovery. The original launcher stays untouched.

## Known limitations
- No splash screen, no blur/translucency beyond simple alpha panels.
- Pointer/air-mouse behavior, focus restoration and performance are unverified on the real device.
- Document picker may be missing on some TV firmware; a toast is shown if so.
- Default-launcher setup, auto-start, boot logo/animation are out of scope.
