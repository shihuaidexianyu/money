# Money

A personal finance tracking app for Android, built with Kotlin and Jetpack Compose.

## Features

- **Multi-account management** — Custom-ordered active accounts plus archived read-only accounts
- **Cash flow recording** — Track inflow and outflow with purpose tagging
- **Transfers** — Record money movements between accounts
- **Balance updates** — Periodic balance snapshots with automatic delta calculation
- **Reminders** — In-app due reminders for manual payments and subscriptions
- **History & search** — Filter by account, date range, amount, and keyword
- **Dark mode** — Full dark theme support following system settings
- **Data export** — Manually share a JSON export from Settings
- **Privacy-first storage** — Offline-only app data; Android automatic cloud backup is disabled

## Tech Stack

- **Language**: Kotlin 2.2.20
- **UI**: Jetpack Compose BOM 2025.10.01 + Material 3
- **Architecture**: Clean Architecture (Domain / Data / UI) + MVVM
- **Database**: Room 2.8.0 (SQLite), schema version 7
- **Settings**: DataStore Preferences 1.1.7
- **Navigation**: Navigation Compose 2.9.5
- **DI**: Manual dependency injection via `MoneyAppContainer`
- **Min SDK**: 31 (Android 12)
- **Target/Compile SDK**: 36

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

Release builds can also be produced with `scripts/build-release.ps1`; it bumps the app version by default and expects Android Studio's JBR at `C:\Program Files\Android\Android Studio\jbr`.

## Project Structure

```
app/src/main/java/com/shihuaidexianyu/money/
├── domain/          # Business logic (use cases, repository interfaces, models)
├── data/            # Room entities, DAOs, repository implementations
├── ui/              # Compose screens and ViewModels by feature
├── navigation/      # Navigation graph definitions
└── util/            # Formatting and helper utilities
```

## License

All rights reserved.
