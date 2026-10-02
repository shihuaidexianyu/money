package com.shihuaidexianyu.money.ui.history

import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.ZoneId

internal data class HistoryDateGroup(
    val dateLabel: String,
    val records: List<HistoryRecordUiModel>,
    /** Null means overflow, never a truncated or wrapped amount. */
    val cashIncomeTotal: Long?,
    val cashExpenseTotal: Long?,
)

/** Build once per committed record list, not on every search/scroll/loading recomposition. */
internal fun historyDateGroups(
    records: List<HistoryRecordUiModel>,
    zoneId: ZoneId,
): List<HistoryDateGroup> = records
    .groupBy { DateTimeTextFormatter.formatDateOnly(it.occurredAt, zoneId) }
    .map { (dateLabel, dayRecords) ->
        var income: Long? = 0L
        var expense: Long? = 0L
        dayRecords.forEach { record ->
            if (record.kind == HistoryRecordKind.CASH_FLOW) {
                if (record.amount > 0L) income = income?.addForDisplay(record.amount)
                if (record.amount < 0L) expense = expense?.addForDisplay(record.amount)
            }
        }
        HistoryDateGroup(dateLabel, dayRecords, income, expense)
    }

private fun Long.addForDisplay(value: Long): Long? = try {
    Math.addExact(this, value)
} catch (_: ArithmeticException) {
    null
}
