# LiftingLoop

**A minimalist native Android app for strength training, cardio, and nutrition tracking.**

Plan your workouts, log every set, and share your progress.

[English](README.md) · [简体中文](README.zh-CN.md)

LiftingLoop is a personal fitness journal designed by DAVID DAI and developed with the help of OpenAI Codex through vibe coding. Built with Kotlin, Jetpack Compose, and Room, it runs on **Android 8.0+**. The current source version is **beta1.1.1 (versionCode 98)**.

## Features

- **Strength training, set by set.** Create routines or start a free workout. Manage exercises, bodyweight movements, added weight, and individual set targets, including drop sets. Exercise settings are remembered for future workouts.
- **Adjust, undo, and resume.** Change weight, reps, sets, and rest times during a workout. Session progress and recent undo history are saved locally so you can return after an interruption.
- **Dedicated cardio flows.** Continuous and interval sessions with timers, pause controls, and confirmation of actual duration and distance before saving.
- **Flexible scheduling.** Keep workout routines separate from your training schedule. Repeat by day or week, and choose whether to pick up where you left off or follow today's plan.
- **A calendar of your progress.** Review daily workout details, weekly training days, and training-volume heatmaps. View and edit past records.
- **Food and nutrition logging.** Search a reference food catalogue, adjust portions, create foods and tags, and review calories and macronutrient energy shares. Missing nutrition values are explicitly marked.
- **Photo workout posters.** Take or choose a photo, move and zoom it, adjust the text layout, then save or share your results using Android's share sheet.
- **A considered native interface.** Mint training accents, warm orange nutrition accents, a dark workout screen, glass home controls, and a rest-time water animation. Reduced-motion and reduced-transparency options are included.
- **Local core records.** Training and food records are stored on your device. Exercise tutorial videos and optional experimental Xiaomi weight sync require a network connection.

The interface is currently primarily in Chinese. This English README introduces the project to international users; a complete English UI translation is not yet included.

## A note about open gym

The following is the author's personal statement, translated from Chinese:

> Some features of this project may resemble those in open gym, another popular project on GitHub. But I swear I did not know about open gym when I started vibe coding this app. open gym is a better app. When I discovered the project a few days ago, I was amazed—it had taken the features I had imagined to their fullest potential.

Original statement:

> 本项目可能部分功能与GitHub上很火的另一个项目open gym有雷同，但是我发誓我在开始vibe coding 这个app的时候还不知道open gym。open gym是一款更优秀的软件，在前几天我看到open gym这个项目的时候都震惊了。ta简直把我想象中的功能做到了极致。

## Install

Check this repository's [Releases](https://github.com/www2733659262-source/LiftingLoop-Workout-Log/releases) for published APKs. If no release is available, build from source below. APKs are excluded from the source Git history.

Earlier local APKs were published under the FitLog name. Their metadata is retained in `releases/`. The source now uses the LiftingLoop display name; the application ID remains `com.fitlog.app` to preserve installation and data compatibility. Updating an existing installation also requires the same signing key. Existing local packages use a debug signing key, which is not included in the repository.

## Build

Requirements: **JDK 17**, **Android SDK 34**, and **Gradle 8.7**.

1. Clone this repository and open it in Android Studio, or use a local Gradle installation.
2. Configure your Android SDK through Android Studio or an untracked `local.properties` file containing `sdk.dir`.
3. This repository does not yet contain a complete Gradle Wrapper. Install Gradle 8.7 and put `gradle` on your PATH.
4. Run:

```sh
gradle :app:assembleDebug :app:lintDebug --console=plain
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The first build needs network access to download dependencies. Dependency versions are listed in [libs.versions.toml](gradle/libs.versions.toml).

A private debug key can be supplied at `.local/debug.keystore`; otherwise Android's default debug signing configuration is used. Keys, local SDK configuration, caches, and build outputs are excluded by `.gitignore`.

## Source layout

| Directory | Contents |
| --- | --- |
| `app/src/main/java/com/fitlog/app/ui` | Compose screens, workout interactions, and visual components |
| `app/src/main/java/com/fitlog/app/domain` | Workout, cardio, scheduling, and nutrition logic |
| `app/src/main/java/com/fitlog/app/data` | Room database, local storage, and optional network integrations |
| `checks` | Domain, migration, and catalogue checks |
| `docs` | Feature notes, design history, and third-party sources, primarily in Chinese |

## Current limitations

- Xiaomi S200 cloud weight sync is experimental and has not completed end-to-end validation with a real account and device.
- The food catalogue includes historical menu items, estimated nutrition, and missing values. It is not a complete or current brand menu.
- Tutorial playback depends on third-party services and the original creators' resources.
- Automatic food recognition is not provided. Use the reference catalogue or enter foods manually.
- This is beta software. Device compatibility, interaction, and recovery behavior need continued testing.

## Contributing

Issues and pull requests are welcome. For bug reports, include your Android version, app version, reproduction steps, and expected behavior. Remove private records and credentials before posting screenshots or logs.

Existing checks are collected in [checks/verify.ps1](checks/verify.ps1). That script expects a local sibling `toolchain` directory and is not a portable CI setup.

Feature notes: [Cardio](docs/CARDIO.md) · [Workout sharing](docs/WORKOUT-SHARING.md) · [Exercise management](docs/EXERCISE-MANAGEMENT.md) · [Scheduling](docs/SCHEDULE-CATCH-UP.md) · [Food sources](docs/FOOD-CATALOG.md) · [Xiaomi sync](docs/MIJIA-SYNC.md).

Earlier development notes are preserved in [Development history](docs/DEVELOPMENT-HISTORY.md); they describe historical builds and local testing, rather than guarantees for the current version.

## License and credits

Original project code is licensed under the [Apache License 2.0](LICENSE). Third-party code and assets retain their respective rights. See [Third-party notices](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE). Some reference illustrations do not yet have fully documented redistribution permissions; the code license does not grant rights to all images, brands, or videos.

Design by **DAVID DAI** · Development assistance: **OpenAI Codex** · Design assistance: **Autodesk AutoCAD 2022**
