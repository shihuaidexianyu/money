package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.HistoryViewportAnchor
import com.shihuaidexianyu.money.ui.history.HistoryScrollTarget
import com.shihuaidexianyu.money.ui.history.historyAnchorScrollTarget
import com.shihuaidexianyu.money.ui.history.historyDateGroups
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class HistoryDateGroupsTest {
    private val now = Instant.parse("2026-10-02T10:00:00Z").toEpochMilli()

    private fun record(id: Long, amount: Long, time: Long = now, kind: HistoryRecordKind = HistoryRecordKind.CASH_FLOW) =
        HistoryRecordUiModel(
            id = "row_$id", recordId = id, kind = kind, title = "流水 $id", subtitle = "账户",
            amount = amount, occurredAt = time, accountIds = setOf(1L), keywordSource = "",
        )

    @Test
    fun `date groups retain row order and sum cash flows only`() {
        val rows = listOf(record(1, 500), record(2, -200), record(3, 900, kind = HistoryRecordKind.TRANSFER),
            record(4, 700, kind = HistoryRecordKind.BALANCE_UPDATE), record(5, 400, now - 86_400_000))
        val groups = historyDateGroups(rows, ZoneOffset.UTC)
        assertEquals(listOf("2026-10-02", "2026-10-01"), groups.map { it.dateLabel })
        assertEquals(rows.take(4), groups.first().records)
        assertEquals(500L, groups.first().cashIncomeTotal)
        assertEquals(-200L, groups.first().cashExpenseTotal)
    }

    @Test
    fun `empty records have no placeholder date group`() {
        assertTrue(historyDateGroups(emptyList(), ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun `date boundaries use the supplied device zone`() {
        val lateUtc = Instant.parse("2026-10-01T23:30:00Z").toEpochMilli()
        assertEquals("2026-10-01", historyDateGroups(listOf(record(1, 100, lateUtc)), ZoneOffset.UTC).single().dateLabel)
        assertEquals("2026-10-02", historyDateGroups(listOf(record(1, 100, lateUtc)), ZoneId.of("Asia/Shanghai")).single().dateLabel)
    }

    @Test
    fun `income overflow is explicit and leaves expenses and rows intact`() {
        val rows = listOf(record(1, Long.MAX_VALUE), record(2, 1), record(3, -200))
        val group = historyDateGroups(rows, ZoneOffset.UTC).single()
        assertNull(group.cashIncomeTotal)
        assertEquals(-200L, group.cashExpenseTotal)
        assertEquals(rows, group.records)
    }

    @Test
    fun `expense overflow is explicit and cannot wrap to an income`() {
        val group = historyDateGroups(listOf(record(1, Long.MIN_VALUE), record(2, -1), record(3, 100)), ZoneOffset.UTC).single()
        assertNull(group.cashExpenseTotal)
        assertEquals(100L, group.cashIncomeTotal)
    }

    @Test
    fun `representable extreme values are not clamped`() {
        val group = historyDateGroups(listOf(record(1, Long.MAX_VALUE), record(2, -Long.MAX_VALUE)), ZoneOffset.UTC).single()
        assertEquals(Long.MAX_VALUE, group.cashIncomeTotal)
        assertEquals(-Long.MAX_VALUE, group.cashExpenseTotal)
    }

    @Test
    fun `refresh preserves the exact record offset even when a preceding row is inserted`() {
        val groups = historyDateGroups(listOf(record(9, 1), record(1, 1), record(2, 1), record(3, 1, now - 86_400_000)), ZoneOffset.UTC)
        val anchor = HistoryViewportAnchor("history_record_row_2", 37, "2026-10-02")
        assertEquals(HistoryScrollTarget(4, 37), historyAnchorScrollTarget(anchor, groups))
    }

    @Test
    fun `removed record falls back to its date header not a different row offset`() {
        val groups = historyDateGroups(listOf(record(1, 1), record(3, 1, now - 86_400_000)), ZoneOffset.UTC)
        assertEquals(HistoryScrollTarget(1), historyAnchorScrollTarget(HistoryViewportAnchor("history_record_row_2", 37, "2026-10-02"), groups))
    }

    @Test
    fun `missing record and date do not force a jump to the top`() {
        val groups = historyDateGroups(listOf(record(3, 1, now - 86_400_000)), ZoneOffset.UTC)
        assertNull(historyAnchorScrollTarget(HistoryViewportAnchor("history_record_row_2", 37, "2026-10-02"), groups))
    }

    @Test
    fun `controls anchor remains at the top and header keeps its offset`() {
        val groups = historyDateGroups(listOf(record(1, 1)), ZoneOffset.UTC)
        assertEquals(HistoryScrollTarget(0, 5), historyAnchorScrollTarget(HistoryViewportAnchor("history_controls", 5, null), groups))
        assertEquals(HistoryScrollTarget(1, 8), historyAnchorScrollTarget(HistoryViewportAnchor("history_date_2026-10-02", 8, "2026-10-02"), groups))
    }
}
