package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType

class UpdateAccountOrderingUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val updateAccountDisplayOrderUseCase: UpdateAccountDisplayOrderUseCase,
) {
    suspend operator fun invoke(
        groupOrder: List<AccountGroupType>,
        orderedAccountIds: List<Long>,
    ) {
        val normalizedGroupOrder = AccountGroupType.normalizeOrder(groupOrder)
        val previousAccountIds = accountRepository.queryActiveAccounts()
            .sortedBy { it.displayOrder }
            .map { it.id }

        updateAccountDisplayOrderUseCase(orderedAccountIds)

        runCatching {
            settingsRepository.updateAccountGroupOrder(normalizedGroupOrder)
        }.onFailure { error ->
            runCatching { updateAccountDisplayOrderUseCase(previousAccountIds) }
                .onFailure { rollbackError -> error.addSuppressed(rollbackError) }
            throw error
        }
    }
}

