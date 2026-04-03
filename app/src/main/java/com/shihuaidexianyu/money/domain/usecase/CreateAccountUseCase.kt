package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) {
    suspend operator fun invoke(
        name: String,
        groupType: AccountGroupType,
        initialBalance: Long,
        balanceUpdateReminderDays: Int = DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
        createdAt: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "账户名称不能为空" }
        require(accountRepository.isActiveNameAvailable(normalizedName)) { "已存在同名账户" }
        require(balanceUpdateReminderDays > 0) { "提醒周期必须大于 0 天" }

        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = normalizedName,
                groupType = groupType.value,
                initialBalance = initialBalance,
                createdAt = createdAt,
                lastUsedAt = createdAt,
                displayOrder = accountRepository.nextDisplayOrder(),
            ),
        )
        accountReminderSettingsRepository.updateReminderDays(accountId, balanceUpdateReminderDays)
        return accountId
    }
}
