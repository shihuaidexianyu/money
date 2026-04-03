package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost

internal enum class AccountSettingsPicker {
    ACCOUNT_TYPE,
    REMINDER_WEEKDAY,
    REMINDER_TIME,
}

@Composable
internal fun AccountSettingsPickerDialog(
    picker: AccountSettingsPicker?,
    groupType: AccountGroupType,
    reminderConfig: BalanceUpdateReminderConfig,
    onDismiss: () -> Unit,
    onGroupTypeSelected: (AccountGroupType) -> Unit,
    onReminderWeekdaySelected: (BalanceUpdateReminderWeekday) -> Unit,
    onReminderTimeSelected: (Int, Int) -> Unit,
) {
    when (picker) {
        AccountSettingsPicker.ACCOUNT_TYPE -> {
            MoneyChoiceDialog(
                title = "账户类型",
                options = AccountGroupType.entries,
                selected = groupType,
                label = { it.displayName },
                onSelect = {
                    onGroupTypeSelected(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.REMINDER_WEEKDAY -> {
            MoneyChoiceDialog(
                title = "每周提醒日",
                options = BalanceUpdateReminderWeekday.entries,
                selected = reminderConfig.weekday,
                label = { it.displayName },
                onSelect = {
                    onReminderWeekdaySelected(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.REMINDER_TIME -> {
            val initialTimeMillis = java.time.LocalDate.now()
                .atTime(reminderConfig.hour, reminderConfig.minute)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            MoneyTimePickerDialogHost(
                initialTimeMillis = initialTimeMillis,
                onDismiss = onDismiss,
                onConfirm = { hour, minute ->
                    onReminderTimeSelected(hour, minute)
                    onDismiss()
                },
            )
        }

        null -> Unit
    }
}

@Composable
internal fun AccountTypeReminderFields(
    groupType: AccountGroupType,
    reminderConfig: BalanceUpdateReminderConfig,
    onAccountTypeClick: () -> Unit,
    onReminderWeekdayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneySelectionField(
        label = "账户类型",
        value = groupType.displayName,
        modifier = Modifier.clickable(onClick = onAccountTypeClick),
    )
    MoneySelectionField(
        label = "每周提醒日",
        value = reminderConfig.weekday.displayName,
        subtitle = "到了提醒时间后未更新会标记为待更新",
        modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
    )
    MoneySelectionField(
        label = "提醒时间",
        value = reminderConfig.timeText,
        modifier = Modifier.clickable(onClick = onReminderTimeClick),
    )
}

@Composable
internal fun AccountTypeReminderListSection(
    groupType: AccountGroupType,
    reminderConfig: BalanceUpdateReminderConfig,
    onAccountTypeClick: () -> Unit,
    onReminderWeekdayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneyListSection {
        MoneyListRow(
            title = "账户类型",
            trailing = groupType.displayName,
            modifier = Modifier.clickable(onClick = onAccountTypeClick),
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "每周提醒日",
            subtitle = "到了提醒时间后未更新会标记为待更新",
            trailing = reminderConfig.weekday.displayName,
            modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "提醒时间",
            trailing = reminderConfig.timeText,
            modifier = Modifier.clickable(onClick = onReminderTimeClick),
        )
    }
}
