package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActiveAccounts(): Flow<List<AccountEntity>> {
        return accounts.map { list -> list.filterNot(AccountEntity::isArchived) }
    }

    override fun observeArchivedAccounts(): Flow<List<AccountEntity>> {
        return accounts.map { list -> list.filter(AccountEntity::isArchived) }
    }

    override suspend fun queryActiveAccounts(): List<AccountEntity> {
        return accounts.value.filterNot(AccountEntity::isArchived)
    }

    override suspend fun queryArchivedAccounts(): List<AccountEntity> {
        return accounts.value.filter(AccountEntity::isArchived)
    }

    override suspend fun getAccountById(id: Long): AccountEntity? {
        return accounts.value.firstOrNull { it.id == id }
    }

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        return accounts.value.none {
            !it.isArchived && it.name == name && (excludeId < 0 || it.id != excludeId)
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

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(lastBalanceUpdateAt = timestamp) else existing
        }
    }

    override suspend fun nextDisplayOrder(): Int {
        return (accounts.value.maxOfOrNull { it.displayOrder } ?: -1) + 1
    }
}
