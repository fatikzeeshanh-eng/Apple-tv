# AppleTV Launcher for Android 4.4 KitKat

A lightweight, remote-friendly Android TV home screen inspired by the Apple TV shelf layout, designed to run on older Android 4.4 (API 19) TV boxes.

## Features

- **Android 4.4 compatible:** `minSdk 19`, `targetSdk 19`
- **HOME launcher:** choose it as your default home screen after installation
- **D-pad / remote navigation:** focusable app grid with visible selection state
- **App grid:** discovers launchable apps, alphabetically sorted, with icon and name
- **Low overhead:** plain Android `Activity`; no external runtime dependencies
- **Background process cleanup:** optional best-effort cleanup on launcher start (Android/system policy may limit which processes can be stopped)

> This is an independent launcher app inspired by a TV interface. It is not affiliated with or endorsed by Apple. It does not provide Apple TV+ streaming or other subscription content.

## Build locally

Requires JDK 17 and Android SDK Platform 34 / Build Tools 34.

```bash
git clone https://github.com/fatikzeeshanh-eng/Apple-tv.git
cd Apple-tv
git checkout build-apk
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download the APK

Every push to `build-apk` runs the GitHub Actions workflow. Open **Actions → Build Android APK → latest successful run → Artifacts** and download `AppleTVLauncher-debug-apk`.

## Install

Transfer `app-debug.apk` to your Android 4.4 TV box and install it. If prompted, allow installation from unknown sources. Press Home and select **AppleTV Launcher** to make it the home screen.

## Android 4.4 note

This app's minimum SDK is API 19. The GitHub Actions workflow uses a current Android Gradle Plugin to compile against a modern SDK while retaining KitKat runtime compatibility. Requires a compatible Android 4.4 device with Google/Android app package APIs available.
