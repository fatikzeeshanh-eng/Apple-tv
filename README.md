# AppleTV Launcher for Android 4.4 KitKat 🍏📺

[![Made in Pakistan 🇵🇰](https://img.shields.io/badge/Made%20in-Pakistan%20%F0%9F%87%B5%F0%9F%87%B0-green)](#)
[![Built by an 11-year-old kid](https://img.shields.io/badge/Built%20by-an%2011%20year%20old%20kid-blueviolet)](#)
[![Android 4.4+](https://img.shields.io/badge/Android-4.4%2B%20KitKat%20%F0%9F%8D%AB-3DDC84)](#)

> 🇵🇰 **Made in Pakistan by an 11-year-old kid** 💖

A lightweight, remote-friendly Android TV home screen inspired by the Apple TV shelf layout, designed to run on older Android 4.4 (API 19) TV boxes.

## ✨ Features

- **Android 4.4 compatible:** `minSdk 19`, `targetSdk 19` — built for old KitKat TV boxes
- **HOME launcher:** choose it as your default home screen after installation
- **tvOS-style top tabs:** Featured | Apps
- **Featured shelf:** large glass cards with reflections for your top apps
- **D-pad / remote navigation:** focusable app grid with visible selection state
- **App grid:** discovers launchable apps, alphabetically sorted, with icon and name
- **Low overhead:** plain Android `Activity`; no external runtime dependencies
- **Background process cleanup:** best-effort cleanup on launcher start

> This is an independent launcher app inspired by a TV interface. It is not affiliated with or endorsed by Apple. It does not provide Apple TV+ streaming or other subscription content.

## 🔨 Build locally

Requires JDK 17 and Android SDK Platform 34 / Build Tools 34.

```bash
git clone https://github.com/fatikzeeshanh-eng/Apple-tv.git
cd Apple-tv
git checkout build-apk
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## 📦 Download the APK

Every push to `build-apk` runs the GitHub Actions workflow. Open **Actions → Build Android APK → latest successful run → Artifacts** and download `AppleTVLauncher-debug-apk`.

## 📲 Install

1. Transfer `app-debug.apk` to your Android 4.4 TV box (USB or direct download)
2. Install it and allow installation from unknown sources when prompted
3. Press **Home** and select **AppleTV Launcher** to make it your home screen

## 📝 About

This project was built by an 11-year-old developer from Pakistan 🇵🇰 as a way to give old Android 4.4 TV boxes a fresh, modern Apple TV-style home screen.
