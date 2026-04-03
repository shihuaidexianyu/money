package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActiveAccounts(): Flow<List<AccountEntity>> {
        return accounts.map(::activeAccounts)
    }

    override fun observeArchivedAccounts(): Flow<List<AccountEntity>> {
        return accounts.map(::archivedAccounts)
    }

    override suspend fun queryActiveAccounts(): List<AccountEntity> {
        return activeAccounts(accounts.value)
    }

    override suspend fun queryArchivedAccounts(): List<AccountEntity> {
        return archivedAccounts(accounts.value)
    }

    override suspend fun getAccountById(id: Long): AccountEntity? {
        return accounts.value.firstOrNull { it.id == id }
    }

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        val normalizedName = name.trim()
        return accounts.value.none {
            !it.isArchived && it.name == normalizedName && (excludeId < 0 || it.id != excludeId)
        }
    }

    override suspend fun createAccount(account: AccountEntity): Long {
        val id = nextId++
        accounts.value = accounts.value + account.copy(id = id)
        return id
    }

    override suspend fun updateAccount(account: AccountEntity) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == account.id) account else existing
        }
    }

    override suspend fun archiveAccount(accountId: Long, archivedAt: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) {
                existing.copy(isArchived = true, archivedAt = archivedAt)
            } else {
                existing
            }
        }
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(lastUsedAt = timestamp) else existing
        }
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(lastBalanceUpdateAt = timestamp) else existing
        }
    }

    override suspend fun nextDisplayOrder(): Int {
        return (accounts.value.filterNot(AccountEntity::isArchived).maxOfOrNull { it.displayOrder } ?: -1) + 1
    }

    private fun activeAccounts(list: List<AccountEntity>): List<AccountEntity> {
        return list.filterNot(AccountEntity::isArchived)
            .sortedWith(compareBy<AccountEntity> { it.displayOrder }.thenBy { it.createdAt })
    }

    private fun archivedAccounts(list: List<AccountEntity>): List<AccountEntity> {
        return list.filter(AccountEntity::isArchived)
            .sortedWith(compareByDescending<AccountEntity> { it.archivedAt }.thenByDescending { it.createdAt })
    }
}

