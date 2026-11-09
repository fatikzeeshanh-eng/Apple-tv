# AppleTV Launcher for Android 4.4 KitKat 🍏📺

[![Made in Pakistan 🇵🇰](https://img.shields.io/badge/Made%20in-Pakistan%20%F0%9F%87%B5%F0%9F%87%B0-green)](#)
[![Built by an 11-year-old kid](https://img.shields.io/badge/Built%20by-an%2011%20year%20old%20kid-blueviolet)](#)
[![Android 4.4+](https://img.shields.io/badge/Android-4.4%2B%20KitKat%20%F0%9F%8D%AB-3DDC84)](#)
[![Download APK](https://img.shields.io/badge/Download-APK-red)](https://github.com/fatikzeeshanh-eng/Apple-tv/releases)

> 🇵🇰 **Made in Pakistan by an 11-year-old kid** 💖

A lightweight, remote-friendly Android TV home screen inspired by the Apple TV shelf layout, designed to run on older Android 4.4 (API 19) TV boxes.

## 📥 Install the APK (no GitHub account needed)

1. Go to the **[Releases page](https://github.com/fatikzeeshanh-eng/Apple-tv/releases)**
2. Download **`AppleTV-Launcher-v1.2.apk`** under Assets
3. Copy it to your Android 4.4 TV box (USB drive or direct download)
4. Open it with a file manager and install — allow **Unknown sources** when asked
5. Press **Home** and pick **AppleTV Launcher** as your home screen 🍏

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
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## 🤖 CI builds

Every push to `build-apk` builds the APK automatically with GitHub Actions and publishes it as a GitHub Release, so the download link above always has the latest version.

## 📝 About

This project was built by an 11-year-old developer from Pakistan 🇵🇰 as a way to give old Android 4.4 TV boxes a fresh, modern Apple TV-style home screen.
