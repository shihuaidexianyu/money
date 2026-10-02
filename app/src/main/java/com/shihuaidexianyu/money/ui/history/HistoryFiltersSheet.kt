package com.shihuaidexianyu.money.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.HistoryBusinessSemantic
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.Instant
import java.time.ZoneId

internal fun HistoryFilterState.forMinimalLedger() = copy(
    excludeKeyword = "", selectedRecordTypes = emptySet(), minAmountText = "", maxAmountText = "",
    amountDirectionFilter = AmountDirectionFilter.ALL, businessSemantic = HistoryBusinessSemantic.ALL,
)

internal fun HistoryUiState.filterDraft() = HistoryFilterState(
    excludeKeyword = excludeKeyword, selectedRecordTypes = selectedRecordTypes,
    selectedAccountId = selectedAccountId, dateStartAt = dateStartAt, dateEndAt = dateEndAt,
    minAmountText = minAmountText, maxAmountText = maxAmountText,
    amountDirectionFilter = amountDirectionFilter, businessSemantic = businessSemantic,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HistoryFiltersSheet(
    initialFilters: HistoryFilterState,
    accounts: List<AccountOptionUiModel>,
    lockedAccountId: Long?,
    onApply: (HistoryFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initialFilters.forMinimalLedger().let {
        it.copy(selectedAccountId = lockedAccountId ?: it.selectedAccountId)
    }) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var accountPicker by rememberSaveable { mutableStateOf(false) }
    var dateField by rememberSaveable { mutableStateOf<String?>(null) }
    if (accountPicker && lockedAccountId == null) {
        AccountPickerDialog(
            title = stringResource(R.string.history_filter_account), accounts = accounts,
            selectedAccountId = draft.selectedAccountId,
            noSelectionLabel = stringResource(R.string.history_all_accounts),
            onDismiss = { accountPicker = false },
            onPick = { draft = draft.copy(selectedAccountId = it); accountPicker = false },
            onClearSelection = { draft = draft.copy(selectedAccountId = null); accountPicker = false },
        )
    }
    dateField?.let { field ->
        MoneyDatePickerDialogHost(
            initialSelectedDateMillis = if (field == "start") draft.dateStartAt else
                draft.dateEndAt?.let(DateTimeTextFormatter::startOfDisplayedEndDateMillis),
            onDismiss = { dateField = null },
            onConfirm = { millis ->
                val start = if (field == "start") millis?.let(DateTimeTextFormatter::startOfDayMillis) else draft.dateStartAt
                val end = if (field == "end") millis?.let(DateTimeTextFormatter::endExclusiveOfDayMillis) else draft.dateEndAt
                val normalized = normalizeHistoryDateRange(start, end)
                draft = draft.copy(dateStartAt = normalized.first, dateEndAt = normalized.second)
                dateField = null
            },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoneySectionHeader(stringResource(R.string.history_filter))
                Text(stringResource(R.string.history_filter_editor_hint), style = MaterialTheme.typography.bodySmall)
                if (lockedAccountId == null) {
                    MoneySelectionField(
                        label = stringResource(R.string.accounts_title),
                        value = accounts.firstOrNull { it.id == draft.selectedAccountId }?.name
                            ?: stringResource(R.string.history_all_accounts),
                        onClick = { accountPicker = true },
                    )
                }
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val endToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val month = TimeRangeCalculator.currentMonthRange(zone, now)
                val ranges = listOf(
                    Triple(R.string.history_today, today.atStartOfDay(zone).toInstant().toEpochMilli(), endToday),
                    Triple(R.string.history_last_seven_days, today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), endToday),
                    Triple(R.string.history_this_month, month.startInclusive, month.endExclusive),
                    Triple(R.string.history_unlimited, null, null),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranges.forEach { (label, start, end) ->
                        FilterChip(
                            selected = draft.dateStartAt == start && draft.dateEndAt == end,
                            onClick = { draft = draft.copy(dateStartAt = start, dateEndAt = end) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                MoneySelectionField(stringResource(R.string.history_start_date),
                    draft.dateStartAt?.let(DateTimeTextFormatter::formatDateOnly) ?: stringResource(R.string.history_unlimited),
                    onClick = { dateField = "start" })
                MoneySelectionField(stringResource(R.string.history_end_date),
                    historyEndDateFieldText(draft.dateEndAt, unlimitedLabel = stringResource(R.string.history_unlimited)),
                    onClick = { dateField = "end" })
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { draft = HistoryFilterState(selectedAccountId = lockedAccountId) }) {
                    Text(stringResource(R.string.history_reset_draft))
                }
                Button(onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onApply(draft.forMinimalLedger().copy(selectedAccountId = lockedAccountId ?: draft.selectedAccountId))
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_apply_filters))
                }
            }
        }
    }
}
