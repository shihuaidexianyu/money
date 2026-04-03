package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository

class UpdateAccountDisplayOrderUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(orderedAccountIds: List<Long>) {
        val activeAccounts = accountRepository.queryActiveAccounts()
        val activeAccountIds = activeAccounts.map { it.id }

        require(orderedAccountIds.size == orderedAccountIds.distinct().size) { "账户顺序不能包含重复项" }
        require(orderedAccountIds.toSet() == activeAccountIds.toSet()) { "账户顺序必须覆盖全部活跃账户" }

        val accountById = activeAccounts.associateBy { it.id }
        orderedAccountIds.forEachIndexed { index, accountId ->
            val account = requireNotNull(accountById[accountId]) { "账户不存在" }
            if (!account.isArchived && account.displayOrder != index) {
                accountRepository.updateAccount(account.copy(displayOrder = index))
            }
        }
    }
}

