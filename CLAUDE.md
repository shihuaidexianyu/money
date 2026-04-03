# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"

# Run a single test method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## Architecture

This is a single-module Android app (`:app`) using **Clean Architecture** with **MVVM** and **Jetpack Compose**.

### Language & UI

- Kotlin, Jetpack Compose with Material 3
- All user-facing strings are in Chinese (Simplified)
- Amounts are stored as `Long` (cents/fen), not floating point

### Layers

**Domain** (`domain/`):
- `model/` — Enums and value objects (`AccountGroupType`, `CashFlowDirection`, `HomePeriod`, `ThemeMode`, etc.)
- `repository/` — Interfaces: `AccountRepository`, `TransactionRepository`, `SettingsRepository`, `AccountReminderSettingsRepository`
- `usecase/` — Business logic. Use cases take repository interfaces as constructor params. Key patterns:
  - Mutation use cases (Create/Update/Delete) call `RefreshAccountActivityStateUseCase` after modifying data
  - Update/Delete use cases call `RecalculateInvestmentSettlementsUseCase` to keep settlements consistent
  - `CalculateCurrentBalanceUseCase` computes balance from initial balance + records since last balance update

**Data** (`data/`):
- `entity/` — Room entities (stored amounts in Long/cents)
- `dao/` — Room DAOs. Soft-delete pattern on cash flow and transfer records (`deletedAt` field)
- `db/` — `MoneyDatabase` (Room, version 1), `LegacyMoneyStoreImporter` for migration from prior format
- `repository/` — Implementations including `InMemory*` variants used in unit tests

**UI** (`ui/`):
- Screens organized by feature: `home/`, `history/`, `accounts/`, `balance/`, `record/`, `settings/`
- Each feature screen has a paired ViewModel
- `common/` — Shared composables
- `theme/` — Material 3 theming with dark mode support

**Navigation** (`navigation/`):
- `MoneyDestination` sealed class defines all routes (4 top-level tabs: Home, History, Accounts, Settings)
- Split into sub-graphs: `TopLevelNavGraph`, `AccountsNavGraph`, `RecordNavGraph`, `BalanceNavGraph`
- Route helpers use companion functions like `MoneyDestination.accountDetailRoute(id)`

### Dependency Injection

Manual DI via `MoneyAppContainer` — no Hilt/Dagger/Koin. All repositories and use cases are wired in this class. ViewModels are created using `moneyViewModelFactory` helper from `NavigationViewModels.kt`.

### Testing

Unit tests use `InMemoryAccountRepository`, `InMemoryTransactionRepository`, and `InMemoryAccountReminderSettingsRepository` — in-memory implementations of the domain repository interfaces. Tests run with `runBlocking` and JUnit 4 + `kotlin.test` assertions.

### Key Domain Concepts

- **Account groups**: Payment, Bank, Investment — each with different behavior (investment accounts track settlements)
- **Transaction types**: CashFlow (inflow/outflow), Transfer (between accounts), BalanceUpdate (periodic balance snapshots), BalanceAdjustment (corrections), InvestmentSettlement (realized gains/losses)
- **Balance calculation**: Starts from the latest balance update (or initial balance if none), then applies subsequent cash flows, transfers, and adjustments
- **Settings**: Stored via DataStore Preferences (`SettingsRepositoryImpl`), not Room
