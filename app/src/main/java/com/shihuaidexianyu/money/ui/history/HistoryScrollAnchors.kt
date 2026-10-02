package com.shihuaidexianyu.money.ui.history

/**
 * Scroll-anchor index math for the history LazyColumn.
 *
 * Item layout in `HistoryScreen`: one leading controls item (filter chips and the filter
 * summary), then per date group a sticky date header followed by individually keyed records.
 * The search field lives above the list and does not participate in these indices.
 */
internal const val HISTORY_HEADER_ITEM_COUNT = 1

internal data class HistoryViewportAnchor(
    val itemKey: String?,
    val scrollOffset: Int,
    val dateLabel: String?,
)

internal data class HistoryScrollTarget(val index: Int, val offset: Int = 0)

/** Preserve the exact row/offset when it survives; a date header is only a fallback. */
internal fun historyAnchorScrollTarget(
    anchor: HistoryViewportAnchor,
    groups: List<HistoryDateGroup>,
): HistoryScrollTarget? {
    if (anchor.itemKey == "history_controls") return HistoryScrollTarget(0, anchor.scrollOffset)
    var index = HISTORY_HEADER_ITEM_COUNT
    var dateFallback: Int? = null
    groups.forEach { group ->
        if (group.dateLabel == anchor.dateLabel) dateFallback = index
        if (anchor.itemKey == "history_date_${group.dateLabel}") {
            return HistoryScrollTarget(index, anchor.scrollOffset)
        }
        index++
        group.records.forEach { record ->
            if (anchor.itemKey == "history_record_${record.id}") {
                return HistoryScrollTarget(index, anchor.scrollOffset)
            }
            index++
        }
    }
    return dateFallback?.let { HistoryScrollTarget(it) }
}

/**
 * Index of the sticky date-header item for [anchorDateLabel], or null when the anchor is absent
 * from the loaded page (the mutation removed that day, or the first page does not reach it).
 * [recordDateLabels] carries one formatted date label per loaded record, in list order, so the
 * the offset includes both preceding date headers and preceding record rows.
 */
internal fun historyAnchorScrollIndex(
    anchorDateLabel: String?,
    recordDateLabels: List<String>,
): Int? {
    if (anchorDateLabel == null) return null
    val groupIndex = recordDateLabels.distinct().indexOf(anchorDateLabel)
    if (groupIndex < 0) return null
    return HISTORY_HEADER_ITEM_COUNT + groupIndex + recordDateLabels.indexOf(anchorDateLabel)
}

/** Select the balance belonging to the scoped account, including receiving-side transfers. */
internal fun historyAccountBalanceAfter(record: HistoryRecordUiModel, accountId: Long): Long? = when {
    accountId !in record.accountIds -> null
    record.primaryAccountId == accountId -> record.balanceAfter
    record.kind != HistoryRecordKind.TRANSFER -> record.balanceAfter
    record.primaryAccountId != null -> record.relatedBalanceAfter
    else -> null
}
