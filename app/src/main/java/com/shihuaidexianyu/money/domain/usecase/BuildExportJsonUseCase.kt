package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

private const val EXPORT_SCHEMA_VERSION = 1
private const val EXPORT_DATABASE_VERSION = 7

class BuildExportJsonUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(exportedAt: Long = System.currentTimeMillis()): String {
        val accounts = (accountRepository.queryActiveAccounts() + accountRepository.queryArchivedAccounts())
            .sortedBy { it.id }
        return jsonObject(
            "metadata" to jsonObject(
                "schemaVersion" to jsonNumber(EXPORT_SCHEMA_VERSION),
                "databaseVersion" to jsonNumber(EXPORT_DATABASE_VERSION),
                "exportedAt" to jsonNumber(exportedAt),
            ),
            "settings" to settingsRepository.observeSettings().first().toJson(),
            "accounts" to jsonArray(accounts.map { it.toJson() }),
            "cashFlowRecords" to jsonArray(
                transactionRepository.queryAllCashFlowRecords()
                    .sortedBy { it.id }
                    .map { it.toJson() },
            ),
            "transferRecords" to jsonArray(
                transactionRepository.queryAllTransferRecords()
                    .sortedBy { it.id }
                    .map { it.toJson() },
            ),
            "balanceUpdateRecords" to jsonArray(
                transactionRepository.queryAllBalanceUpdateRecords()
                    .sortedBy { it.id }
                    .map { it.toJson() },
            ),
            "balanceAdjustmentRecords" to jsonArray(
                transactionRepository.queryAllBalanceAdjustmentRecords()
                    .sortedBy { it.id }
                    .map { it.toJson() },
            ),
            "recurringReminders" to jsonArray(
                recurringReminderRepository.queryAll()
                    .sortedBy { it.id }
                    .map { it.toJson() },
            ),
            "accountReminderConfigs" to jsonArray(
                accounts.map { account ->
                    jsonObject(
                        "accountId" to jsonNumber(account.id),
                        "config" to accountReminderSettingsRepository.getReminderConfig(account.id).toJson(),
                    )
                },
            ),
        )
    }
}

private fun Account.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "name" to jsonString(name),
    "initialBalance" to jsonNumber(initialBalance),
    "createdAt" to jsonNumber(createdAt),
    "archivedAt" to jsonNullableNumber(archivedAt),
    "isArchived" to jsonBoolean(isArchived),
    "lastUsedAt" to jsonNullableNumber(lastUsedAt),
    "lastBalanceUpdateAt" to jsonNullableNumber(lastBalanceUpdateAt),
    "displayOrder" to jsonNumber(displayOrder),
    "colorName" to jsonString(colorName),
)

private fun CashFlowRecord.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "accountId" to jsonNumber(accountId),
    "direction" to jsonString(direction),
    "amount" to jsonNumber(amount),
    "purpose" to jsonString(purpose),
    "occurredAt" to jsonNumber(occurredAt),
    "createdAt" to jsonNumber(createdAt),
    "updatedAt" to jsonNumber(updatedAt),
    "isDeleted" to jsonBoolean(isDeleted),
)

private fun TransferRecord.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "fromAccountId" to jsonNumber(fromAccountId),
    "toAccountId" to jsonNumber(toAccountId),
    "amount" to jsonNumber(amount),
    "note" to jsonString(note),
    "occurredAt" to jsonNumber(occurredAt),
    "createdAt" to jsonNumber(createdAt),
    "updatedAt" to jsonNumber(updatedAt),
    "isDeleted" to jsonBoolean(isDeleted),
)

private fun BalanceUpdateRecord.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "accountId" to jsonNumber(accountId),
    "actualBalance" to jsonNumber(actualBalance),
    "systemBalanceBeforeUpdate" to jsonNumber(systemBalanceBeforeUpdate),
    "delta" to jsonNumber(delta),
    "occurredAt" to jsonNumber(occurredAt),
    "createdAt" to jsonNumber(createdAt),
)

private fun BalanceAdjustmentRecord.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "accountId" to jsonNumber(accountId),
    "delta" to jsonNumber(delta),
    "sourceUpdateRecordId" to jsonNumber(sourceUpdateRecordId),
    "occurredAt" to jsonNumber(occurredAt),
    "createdAt" to jsonNumber(createdAt),
)

private fun RecurringReminder.toJson(): String = jsonObject(
    "id" to jsonNumber(id),
    "name" to jsonString(name),
    "type" to jsonString(type),
    "accountId" to jsonNumber(accountId),
    "direction" to jsonString(direction),
    "amount" to jsonNumber(amount),
    "periodType" to jsonString(periodType),
    "periodValue" to jsonNumber(periodValue),
    "periodMonth" to jsonNullableNumber(periodMonth),
    "isEnabled" to jsonBoolean(isEnabled),
    "nextDueAt" to jsonNumber(nextDueAt),
    "lastConfirmedAt" to jsonNullableNumber(lastConfirmedAt),
    "createdAt" to jsonNumber(createdAt),
    "updatedAt" to jsonNumber(updatedAt),
)

private fun AppSettings.toJson(): String = jsonObject(
    "homePeriod" to jsonString(homePeriod.value),
    "currencySymbol" to jsonString(currencySymbol),
    "showStaleMark" to jsonBoolean(showStaleMark),
    "themeMode" to jsonString(themeMode.value),
    "amountColorMode" to jsonString(amountColorMode.value),
    "lastHistoryKeyword" to jsonString(lastHistoryKeyword),
    "lastHistoryAccountId" to jsonNumber(lastHistoryAccountId),
    "lastHistoryDateStartAt" to jsonNumber(lastHistoryDateStartAt),
    "lastHistoryDateEndAt" to jsonNumber(lastHistoryDateEndAt),
    "lastHistoryMinAmountText" to jsonString(lastHistoryMinAmountText),
    "lastHistoryMaxAmountText" to jsonString(lastHistoryMaxAmountText),
    "lastHistoryAmountDirection" to jsonString(lastHistoryAmountDirection),
)

private fun BalanceUpdateReminderConfig.toJson(): String = jsonObject(
    "weekday" to jsonString(weekday.value),
    "hour" to jsonNumber(hour),
    "minute" to jsonNumber(minute),
)

private fun jsonObject(vararg fields: Pair<String, String>): String {
    return fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
        "${jsonString(key)}:$value"
    }
}

private fun jsonArray(values: List<String>): String {
    return values.joinToString(separator = ",", prefix = "[", postfix = "]")
}

private fun jsonString(value: String): String {
    val escaped = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
    return "\"$escaped\""
}

private fun jsonBoolean(value: Boolean): String = value.toString()

private fun jsonNumber(value: Long): String = value.toString()

private fun jsonNumber(value: Int): String = value.toString()

private fun jsonNullableNumber(value: Long?): String = value?.toString() ?: "null"

private fun jsonNullableNumber(value: Int?): String = value?.toString() ?: "null"
