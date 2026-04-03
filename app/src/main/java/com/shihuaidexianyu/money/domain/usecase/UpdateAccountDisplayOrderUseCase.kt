package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.repository.AccountRepository

class UpdateAccountDisplayOrderUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(orderedAccountIds: List<Long>) {
        orderedAccountIds.forEachIndexed { index, accountId ->
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            if (!account.isArchived && account.displayOrder != index) {
                accountRepository.updateAccount(account.copy(displayOrder = index))
            }
        }
    }
}
