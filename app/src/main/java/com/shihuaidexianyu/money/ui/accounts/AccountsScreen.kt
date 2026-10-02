package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.LocalAmountVisibility
import com.shihuaidexianyu.money.ui.common.MoneyAmountText
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.formatInAppAmount

data class AccountGroups(
    val normal: List<AccountListItemUiModel>,
    val hidden: List<AccountListItemUiModel>,
    val closed: List<AccountListItemUiModel>,
) {
    val all: List<AccountListItemUiModel> get() = normal + hidden + closed
}

fun accountGroups(
    openAccounts: List<AccountListItemUiModel>,
    closedAccounts: List<AccountListItemUiModel>,
): AccountGroups = AccountGroups(
    normal = openAccounts.filterNot(AccountListItemUiModel::isHidden),
    hidden = openAccounts.filter(AccountListItemUiModel::isHidden),
    closed = closedAccounts,
)

@Suppress("UNUSED_PARAMETER")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleClosedVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    onReorderAccounts: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBatchReconcile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleAmountVisibility: (() -> Unit)? = null,
) {
    val order = compareBy<AccountListItemUiModel> { it.createdAt }.thenBy { it.id }
    val groups = accountGroups(state.openAccounts.sortedWith(order), state.closedAccounts.sortedWith(order))
    val total = runCatching { state.openAccounts.map { it.balance }.ledgerSumExact() }.getOrNull()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val error = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.accounts_title)) },
            actions = {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
                TextButton(onClick = onCreateAccount, enabled = !state.isLoading) {
                    Text(stringResource(R.string.minimal_new_account))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MoneyDimens.screenHorizontalPadding, end = MoneyDimens.screenHorizontalPadding,
                top = 12.dp, bottom = MoneyDimens.bottomNavContentPadding,
            ),
        ) {
            val content = state.toAsyncContent(error)
            if (content is AsyncContent.Loading || content is AsyncContent.Error) {
                item {
                    AsyncContentRenderer(content = content, onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp), data = { _, _ -> })
                }
                return@LazyColumn
            }
            if (state.openAccounts.isNotEmpty()) {
                item(key = "total_balance") {
                    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.minimal_total_balance), Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            onToggleAmountVisibility?.let { toggle ->
                                TextButton(onClick = toggle) {
                                    Text(stringResource(if (LocalAmountVisibility.current == AmountVisibility.MASKED)
                                        R.string.minimal_show_amounts else R.string.minimal_hide_amounts))
                                }
                            }
                        }
                        if (total != null) {
                            MoneyAmountText(formatInAppAmount(total, state.settings), Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.displaySmall)
                        } else {
                            Text(stringResource(R.string.minimal_total_overflow), color = MaterialTheme.colorScheme.error)
                        }
                        if (groups.hidden.isNotEmpty()) {
                            Text(stringResource(R.string.minimal_total_includes_hidden),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.openAccounts.isEmpty()) {
                item {
                    MoneyEmptyStateCard(
                        title = stringResource(if (groups.closed.isEmpty()) R.string.accounts_none else R.string.accounts_no_open),
                        subtitle = stringResource(R.string.minimal_first_account_hint),
                    ) {
                        MoneyTonalButton(onClick = onCreateAccount) { Text(stringResource(R.string.accounts_create_first)) }
                    }
                }
            }
            items(groups.normal, key = { "open:${it.id}" }) { account ->
                AccountRow(account, state.settings, total ?: 0L, { onAccountClick(account.id) })
                MoneySectionDivider()
            }
            if (groups.hidden.isNotEmpty()) {
                item(key = "hidden_accounts") {
                    MoneyListRow(title = stringResource(R.string.accounts_hidden),
                        trailing = pluralStringResource(R.plurals.account_count, groups.hidden.size, groups.hidden.size),
                        showChevron = false, onClick = { showHidden = !showHidden },
                        accessory = { Text(stringResource(if (showHidden) R.string.action_collapse else R.string.action_view)) })
                }
                if (showHidden) items(groups.hidden, key = { "hidden:${it.id}" }) { account ->
                    AccountRow(account, state.settings, total ?: 0L, { onAccountClick(account.id) })
                }
            }
            // Invalid legacy closed balances must remain discoverable without expanding a group.
            items(groups.closed.filter { it.requiresReopenAndSettle }, key = { "issue:${it.id}" }) { account ->
                AccountRow(account, state.settings, total ?: 0L, { onAccountClick(account.id) })
            }
            val closed = groups.closed.filterNot { it.requiresReopenAndSettle }
            if (closed.isNotEmpty()) {
                item(key = "closed_accounts") {
                    MoneyListRow(title = stringResource(R.string.accounts_closed),
                        trailing = pluralStringResource(R.plurals.account_count, closed.size, closed.size),
                        showChevron = false, onClick = onToggleClosedVisibility,
                        accessory = { Text(stringResource(if (state.showClosed) R.string.action_collapse else R.string.action_view)) })
                }
                if (state.showClosed) items(closed, key = { "closed:${it.id}" }) { account ->
                    AccountRow(account, state.settings, total ?: 0L, { onAccountClick(account.id) })
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AccountRow(
    account: AccountListItemUiModel,
    currencySettings: PortableSettings,
    totalBalance: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = formatInAppAmount(account.balance, currencySettings)
    val status = when {
        account.requiresReopenAndSettle -> stringResource(R.string.account_status_reopen_settle)
        account.isClosed -> stringResource(R.string.account_status_closed)
        account.isHidden -> stringResource(R.string.account_status_hidden)
        else -> null
    }
    val description = listOfNotNull(account.name,
        stringResource(R.string.account_balance_semantics_format, amount), status).joinToString("，")
    val stacked = LocalDensity.current.fontScale > 1.3f || amount.length > 15
    Column(modifier.fillMaxWidth().testTag("account_row_${account.id}")
        .clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = 16.dp)
        .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(account.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!stacked) Text(amount, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        }
        if (stacked) MoneyAmountText(amount, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (account.requiresReopenAndSettle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
