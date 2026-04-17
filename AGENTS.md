# AGENTS.md

This file contains essential context for AI coding agents working on the **Money** Android project.

## Project Overview

**Money** is a personal finance tracking app for Android, built with Kotlin and Jetpack Compose.
It supports multi-account management, cash flow recording, transfers, balance updates, recurring reminders, history search, data export, and dark mode.

- **Package**: `com.shihuaidexianyu.money`
- **Application ID**: `com.shihuaidexianyu.money`
- **Version**: `1.0.32` (versionCode `33`)
- **Min SDK**: 31 (Android 12)
- **Target/Compile SDK**: 36
- **Language**: Kotlin 2.2.20
- **Java compatibility**: VERSION_17

## Technology Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose (BOM 2025.10.01) + Material 3 |
| Architecture | Clean Architecture (Domain / Data / UI) + MVVM |
| Database | Room 2.8.0 (SQLite) with KSP |
| Settings | DataStore Preferences 1.1.7 |
| Navigation | Navigation Compose 2.9.5 |
| DI | Manual (no Hilt/Dagger/Koin) via `MoneyAppContainer` |
| Background Work | WorkManager 2.10.5 |
| Testing | JUnit 4 + `kotlin.test` assertions |

## Build and Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"

# Run a single test method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

### Automated Release Script

A PowerShell release script is provided at `scripts/build-release.ps1`:

```powershell
# Bump patch version and build release APK
.\scripts\build-release.ps1

# Run tests, build, commit, and push
.\scripts\build-release.ps1 -RunTests -Commit -Push
```

The script:
- Auto-bumps `versionName` and `versionCode` in `app/build.gradle.kts` if not provided.
- Sets isolated `GRADLE_USER_HOME` and `ANDROID_USER_HOME` for reproducible release builds.
- Expects `JAVA_HOME` at `C:\Program Files\Android\Android Studio\jbr`.
- Signs release builds using `../timeline/keystore.properties` and the keystore referenced there.

## Project Structure

```
app/src/main/java/com/shihuaidexianyu/money/
├── MainActivity.kt              # Entry point
├── MoneyApplication.kt          # Application class
├── MoneyApp.kt                  # Root Compose app
├── MoneyAppContainer.kt         # Manual DI container
├── data/
│   ├── dao/                     # Room DAOs
│   ├── db/                      # MoneyDatabase, migrations, DataStore extensions
│   ├── debug/                   # DebugSampleDataSeeder
│   ├── entity/                  # Room entities
│   └── repository/              # Repository implementations + in-memory test variants
├── domain/
│   ├── model/                   # Enums, value objects, AppSettings
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business logic / use cases
├── navigation/                  # Nav graphs, destinations, ViewModel factories
├── ui/
│   ├── accounts/                # Accounts list, detail, create, edit, reorder
│   ├── balance/                 # Balance update, adjustment detail
│   ├── common/                  # Shared composables, UiEffect, Account picker
│   ├── history/                 # History & search screen
│   ├── home/                    # Home dashboard
│   ├── record/                  # Record cash flow / transfer
│   ├── reminder/                # Recurring reminders
│   ├── settings/                # App settings
│   ├── stats/                   # Statistics screens
│   └── theme/                   # Material 3 theming (light + dark)
└── util/                        # Formatters, parsers, time utilities
```

## Architecture Rules

### Clean Architecture Layers

1. **Domain** (`domain/`): Pure Kotlin. No Android framework dependencies.
   - `model/`: Enums like `AccountGroupType`, `CashFlowDirection`, `HomePeriod`, `ThemeMode`.
   - `repository/`: Interfaces only (`AccountRepository`, `TransactionRepository`, etc.).
   - `usecase/`: Single-responsibility business logic. Use cases accept repository interfaces via constructor.

2. **Data** (`data/`):
   - `entity/`: Room entities. Amounts are always stored as `Long` (cents/fen).
   - `dao/`: Room DAOs. Cash flow and transfer records use soft-delete (`deletedAt` field).
   - `repository/`: Concrete implementations plus `InMemory*` variants for unit tests.
   - `db/MoneyDatabase.kt`: Room database (current version = 4, `exportSchema = true`).

3. **UI** (`ui/`):
   - One package per feature.
   - Each screen has a paired `ViewModel` exposing a single `StateFlow<UiState>`.
   - Compose UI collects state and triggers events back to the ViewModel.

### Dependency Injection

All dependencies are wired manually in `MoneyAppContainer`. ViewModels are created via `moneyViewModelFactory` in `navigation/NavigationViewModels.kt`. Do **not** introduce Hilt, Dagger, or Koin without explicit approval.

### Mutation Side-Effect Pattern

After any data mutation, use cases must refresh derived state:
- Create/Update/Delete transactions call `RefreshAccountActivityStateUseCase`.
- Balance updates and adjustments call `RefreshAccountActivityStateUseCase`.

## Code Style Guidelines

- **Kotlin code style**: Official (set in `gradle.properties`).
- **Formatting**: 4-space indentation.
- **Naming**: Standard Kotlin conventions (`PascalCase` for types, `camelCase` for functions/variables).
- **Imports**: Prefer explicit imports; avoid wildcard imports.
- **Coroutines**: Use `viewModelScope` in ViewModels. Use `runBlocking` only in tests or initialization (e.g., `LegacyMoneyStoreImporter`).
- **User-facing strings**: All UI text is in **Chinese (Simplified)**. Keep it consistent.
- **Amount handling**: Never use `Float`/`Double` for money. Always use `Long` representing the smallest currency unit (cents / fen).

## Testing Instructions

### Unit Tests

Located in `app/src/test/java/com/shihuaidexianyu/money/`.

- Use `InMemoryAccountRepository`, `InMemoryTransactionRepository`, `InMemoryAccountReminderSettingsRepository`, and `InMemoryRecurringReminderRepository` for fast, hermetic tests.
- Wrap suspend calls in `runBlocking`.
- Assertions use `kotlin.test.assertEquals` and JUnit 4 (`@Test`).

### Instrumented Tests

Located in `app/src/androidTest/`. Currently minimal (`ExampleInstrumentedTest`).

### Running Tests

Always run unit tests before submitting changes:

```bash
./gradlew test
```

## Database Migrations

Room schema is exported to `app/schemas/`. Current database version is **4**.

Existing migrations:
- `1 → 2`: Re-created index on `balance_adjustment_records.sourceUpdateRecordId`.
- `2 → 3`: Added `recurring_reminders` table.
- `3 → 4`: Dropped `investment_settlements` table and related indexes.

When modifying entities:
1. Bump `@Database(version = ...)`.
2. Add a `Migration` object in `MoneyDatabase.kt`.
3. Register it in `addMigrations(...)`.
4. Ensure the exported schema JSON is updated (run `./gradlew kspDebugKotlin` or build).

## Key Domain Concepts

- **Account groups**: `PAYMENT` (支付类), `BANK` (银行类), `INVESTMENT` (投资类).
- **Transaction types**:
  - `CashFlow`: Inflow / outflow with purpose tagging.
  - `Transfer`: Between two accounts.
  - `BalanceUpdate`: Snapshot of actual balance; stores the delta directly on the record.
  - `BalanceAdjustment`: Manual correction only; no longer auto-generated from balance updates.
- **Balance calculation**: Starts from the latest `BalanceUpdate.actualBalance` (or `initialBalance`), then applies all subsequent non-deleted cash flows, transfers, and adjustments.
- **Reminders**: Recurring reminders with `MONTHLY`, `YEARLY`, or `CUSTOM_DAYS` periods. Stored in `RecurringReminderEntity`.

## Security Considerations

- **No networking**: The app is entirely offline. No API keys, tokens, or remote endpoints.
- **Data export**: `ExportJsonUseCase` writes a JSON dump to app-private storage and triggers a share intent. Ensure exported JSON does not leak to unintended apps.
- **Backup**: `AndroidManifest.xml` enables `allowBackup="true"` with `data_extraction_rules.xml` and `backup_rules.xml`. Be mindful of what is backed up to Google cloud.
- **Signing**: Release builds are signed with a keystore located outside the repo (`../timeline/`). Never commit keystore files or `keystore.properties`.
- **Debug data**: `DebugSampleDataSeeder` seeds sample data only when `ApplicationInfo.FLAG_DEBUGGABLE` is true.

## Common Pitfalls for Agents

- Do **not** use `Float`/`Double` for monetary amounts. Use `Long` (cents).
- Do **not** add new DI frameworks. Use `MoneyAppContainer`.
- When deleting cash flow / transfer records, perform soft-delete (`deletedAt = now`) rather than hard-delete.
- After any mutation use case, ensure `RefreshAccountActivityStateUseCase` is invoked.
- All new UI strings must be in Chinese (Simplified).
- If changing Room entities, always provide a migration and update the schema export.
