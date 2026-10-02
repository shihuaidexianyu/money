# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**`AGENTS.md` in the repo root is the detailed source of truth** (full package map, migration history, domain glossary, security notes). Read it when you need depth beyond this summary.

## Project

**Money** — an offline-first personal finance app for Android (Kotlin + Jetpack Compose, package `com.shihuaidexianyu.money`). It has no cloud backend. Legacy LAN infrastructure is retained for compatibility but service startup is disabled by `MinimalProductPolicy`. minSdk 31, target/compile SDK 36, Java 17.

Current app version: **2.6.6** (versionCode **150**).

**All user-facing strings are Chinese (Simplified); code, comments, and docs are English.**

## Commands

```bash
./gradlew assembleDebug                 # debug APK
./gradlew assembleRelease               # requires a release keystore; fails loudly otherwise
./gradlew test                          # all unit tests
./gradlew testDebugUnitTest             # faster: one variant only
./gradlew lintDebug
./gradlew connectedAndroidTest          # instrumented tests (device/emulator)
./gradlew :benchmark:connectedCheck     # macrobenchmarks (device/emulator)
./gradlew kspDebugKotlin                # export Room schema JSON after entity changes

# single test class / method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"
```

On Windows use `gradlew.bat` from PowerShell; `./gradlew` works from Git Bash. Run `./gradlew test` before submitting changes.

Release packaging goes through `scripts/build-release.ps1` (auto version bump, isolated `GRADLE_USER_HOME`/`ANDROID_USER_HOME`, `apksigner` signature verification, optional commit/push/tag):

```powershell
.\scripts\build-release.ps1 -RunTests -Commit -Push -Tag
```

The script expects `JAVA_HOME` at `C:\Program Files\Android\Android Studio\jbr` and verifies the APK signature so a debug-signed release can never ship unnoticed.

`gradle.properties` sets `android.disallowKotlinSourceSets=false` — a documented KSP-2.x-under-AGP-9.1 compatibility bridge. Configuration fails without it; only remove it as part of an atomic toolchain upgrade.

## Architecture

Two Gradle modules: `:app` and `:benchmark` (self-instrumenting `com.android.test`; only the matching `benchmark` test variant is enabled, targeting `:app`'s non-debuggable R8/resource-optimized variant configured after `release` and signed with the debug key). Fixture benchmarks replace test ledger data: use only a disposable emulator/device profile. Scenarios include history transitions and real flings, not repeated clicks on an already selected tab.

Clean Architecture + MVVM under `app/src/main/java/com/shihuaidexianyu/money/`:

- **`domain/`** — pure Kotlin, no Android deps. `repository/` holds interfaces only; `usecase/` holds single-responsibility use cases plus shared calculators/projectors/policies (`LedgerBalanceCalculator`, `HomeProjector`, `ReminderNextDueCalculator`); `model/backup/` holds the `@Serializable` snapshot DTOs.
- **`data/`** — Room entities/DAOs, repository impls, `db/MoneyDatabase.kt`, `backup/` (JSON codec + staged import + safety snapshots), `export/`, `migration/` (startup legacy-store upgrade).
- **`ui/`** — one package per feature; each screen has a paired ViewModel exposing a single `StateFlow<UiState>`.
- The minimal product has exactly two top-level pages: accounts (start destination) and activity. Use text-first, icon-free controls. New accounts ask only for name and opening balance. Icons, manual ordering, account-kind setup, the dashboard, batch reconciliation, advanced filters, reminders, and LAN/AI tools have no active entry. Keep primary form actions in `MoneyFormPage.footer`; successful reconciliation returns to its origin with a snackbar. Retired routes redirect to accounts.
- Activity uses icon-free rows with time-only metadata, sticky date headers, and an expandable detail sheet for balance evidence. Keep top-level chrome padding inside destinations through `LocalTopLevelContentPadding` so navigation transitions do not resize the NavHost. Consume applied system/chrome padding before child IME padding to avoid reserving it twice when the keyboard opens.
- Paint opaque backgrounds and clip transitioning pages. Peer tabs switch directly; incoming ledger targets wait for the tap guard AND actual transition completion. Read animation values in drawing/layer phases. History refresh preserves a surviving row's key/pixel offset (date fallback only if removed); rows stay opaque, grouped totals are cached, and overflow cannot crash or wrap.
- Ledger creation uses full-screen type/account/amount/confirmation steps (`LedgerEntryScreen` + one `LedgerEntryViewModel` with a SavedStateHandle draft). Existing shortcuts/reminder/reconciliation routes reuse the flow, skipping only explicit context. Every account list/picker uses stable creation-time/id ordering, never silent selection. Only final confirmation writes; signed/zero reconciliation and investment P&L remain intact. Editing is unchanged; the former batch shortcut enters single-account reconciliation.
- **`navigation/`, `notification/`, `util/`** — routes and nav graphs, WorkManager-backed notification sync, formatters/parsers.
- **`lan/`** — temporary foreground LAN server, NSD advertising, confirmed pairing with persistent device credentials, framed JSON protocol and router. AI ledger writes must go through `AiJournaledLedgerUseCase`, which atomically records them in the persistent LIFO Journal.

### Dependency injection is manual — do not add Hilt/Dagger/Koin

`MoneyAppContainer` delegates to `di/DataGraph.kt` (database, repositories, file writers, notification publisher, startup migration backend) and `di/UseCaseGraph.kt` (40+ use cases). ViewModels are constructed via `moneyViewModelFactory` in `navigation/NavigationViewModels.kt`.

### Startup gating

`MoneyApplication.onCreate` creates notification channels, builds the container, then on a background scope runs `StartupMigrationCoordinator.runMigration()` and waits for `StartupMigrationState.Ready` before cancelling retired notification work and seeding debug sample data. **Never touch the ledger before `Ready`** — use `withReadyLedgerAccess`. Debug sample data is seeded only when `ApplicationInfo.FLAG_DEBUGGABLE` is true.

### Ledger invariants

- **Money is always `Long` in the smallest currency unit (cents/fen).** Never `Float`/`Double`.
- Four record types: `CashFlow`, `Transfer`, `BalanceUpdate` (reconciliation), `BalanceAdjustment` (manual correction). All use `deletedAt` soft deletion and unique `operationId`s — never hard-delete; restore through the matching use case.
- Balance = `initialBalance + inflow - outflow + transferIn - transferOut + manualAdjustment + reconciliationDelta`; zero before the account's opening. A `BalanceUpdate` stores `actualBalance`/`systemBalanceBeforeUpdate` only as evidence — **its `delta` is fixed**. Editing older records must not rewrite later reconciliation deltas, and balances must not be re-anchored on the latest reconciliation.
- After any mutation, call `RefreshAccountActivityStateUseCase` for the affected accounts.
- Closed accounts are read-only; go through the lifecycle use case rather than the repository to reopen.
- **Account kind** is `FUNDING` (default) or `INVESTMENT`. Ledger arithmetic is unchanged, but a reconciliation delta on an investment account is presented as investment P&L at read time; reclassifying an account retroactively reinterprets its whole history.

### External entry points

App shortcuts and notification deep links are normalized into `AppLaunchRequest`s and routed through the launch queue in `ui/launch/`.

### Settings are split in two

`PortableSettings` live in the Room `portable_settings` table and travel with backups. `DevicePreferences` live in DataStore (biometric lock, in-app/notification amount masks, recents hiding) and never leave the device. There is no single `SettingsRepository`.

### Notification refresh

`MinimalProductPolicy.remindersEnabled = false`: the sync requester is a no-op, workers exit without publishing, and startup cancels periodic/immediate/legacy work and old reminder/balance notifications. Legacy reminder data remains backup-compatible. The retained notification contracts must not be mistaken for active product behavior.

## Code style

- 4-space indentation, official Kotlin code style.
- Explicit imports; avoid wildcard imports.
- `viewModelScope` in ViewModels; `runBlocking` only in tests or initialization.
- All UI strings in Chinese (Simplified).
- Amounts as `Long` (cents/fen); never `Float`/`Double`.

## Database migrations

Room schema version **21**, exported to `app/schemas/` (bundled as androidTest assets). Version 20 added sync v1 (`sync_dataset` + `sync_change_log` + batch journal items); version 21 adds the device-local `paired_lan_device` table (credential hashes for `session.device.v1`). When changing entities:

1. Bump `MONEY_DATABASE_VERSION` in `MoneyDatabase.kt`.
2. Add the `Migration` object there and register it in `MONEY_DATABASE_MIGRATIONS`.
3. Build (or `./gradlew kspDebugKotlin`) so the schema JSON is exported.
4. Extend `MoneyDatabaseMigrationTest` in androidTest.

## Testing

- Unit tests: `app/src/test/` (~124 classes). Use the `InMemory*` repository variants for hermetic tests; JUnit 4 + `kotlin.test` assertions, Turbine + coroutines-test for flows, `runBlocking` for suspend calls.
- Instrumented tests: `app/src/androidTest/` — Room migration and repository contract tests, Compose UI tests (home, navigation, pickers, accessibility semantics, large-text reachability), notification and intent-pipeline tests.

## Security-relevant behavior

- Backups export **unencrypted** JSON to app-private cache, shared only via a `FileProvider` URI under `cache/exports/`; the UI must keep warning users to store it somewhere trusted.
- Import stages the URI into private cache, validates/previews the same bytes, writes a verified safety snapshot under `filesDir/pre_import_backups/`, then replaces portable data in one Room transaction, with durable receipts enabling rollback.
- Release signing reads `signing/keystore.properties` (gitignored, as is all of `signing/`), falling back to `../timeline/keystore.properties`. Never commit keystores.
- `allowBackup="false"` — the app deliberately does not use Android cloud/device-transfer backup.
- Biometric app lock and amount privacy masking (in-app and notifications independently) live in `DevicePreferences` and the `ui/lock/` / privacy gateways.
- `MinimalProductPolicy.computerConnectionEnabled = false` prevents LAN service startup; its UI route redirects to accounts. Retained legacy infrastructure: the LAN service lasts at most six hours and advertises itself via NSD (`_moneylink._tcp.`). Pairing is a phone-side confirmation (`session.pair.begin`/`poll`) that issues an ephemeral session token plus a persistent device credential — the phone stores only its SHA-256 hash in `paired_lan_device`, the plaintext is delivered once, and `session.resume` silently restores sessions; the eight-digit code is the manual fallback. Its protocol is plaintext trusted-LAN-only. There is no per-write approval; safety comes from session-level write permission, idempotent request IDs, atomic Journal insertion (batched record writes enter as one undoable unit), semantic conflict detection, and strict LIFO undo through existing use cases.
