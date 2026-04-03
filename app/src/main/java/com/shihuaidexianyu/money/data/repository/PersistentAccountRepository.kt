package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PersistentAccountRepository(
    private val store: PersistentMoneyStore,
) : AccountRepository {
    override fun observeActiveAccounts(): Flow<List<AccountEntity>> {
        return store.snapshot.map { snapshot ->
            snapshot.accounts.filterNot(AccountEntity::isArchived)
        }
    }

    override fun observeArchivedAccounts(): Flow<List<AccountEntity>> {
        return store.snapshot.map { snapshot ->
            snapshot.accounts.filter(AccountEntity::isArchived)
        }
    }

    override suspend fun queryActiveAccounts(): List<AccountEntity> {
        return store.snapshot.value.accounts.filterNot(AccountEntity::isArchived)
    }

    override suspend fun queryArchivedAccounts(): List<AccountEntity> {
        return store.snapshot.value.accounts.filter(AccountEntity::isArchived)
    }

    override suspend fun getAccountById(id: Long): AccountEntity? {
        return store.snapshot.value.accounts.firstOrNull { it.id == id }
    }

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        return store.snapshot.value.accounts.none {
            !it.isArchived && it.name == name && (excludeId < 0 || it.id != excludeId)
        }
    }

    override suspend fun createAccount(account: AccountEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextAccountId
            snapshot.copy(
                nextAccountId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                accounts = snapshot.accounts + account.copy(id = id),
            )
        }
        return updated.nextAccountId - 1
    }

    override suspend fun updateAccount(account: AccountEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                accounts = snapshot.accounts.map { existing ->
                    if (existing.id == account.id) account else existing
                },
            )
        }
    }

    override suspend fun archiveAccount(accountId: Long, archivedAt: Long) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                accounts = snapshot.accounts.map { existing ->
                    if (existing.id == accountId) {
                        existing.copy(isArchived = true, archivedAt = archivedAt)
                    } else {
                        existing
                    }
                },
            )
        }
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                accounts = snapshot.accounts.map { existing ->
                    if (existing.id == accountId) existing.copy(lastUsedAt = timestamp) else existing
                },
            )
        }
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                accounts = snapshot.accounts.map { existing ->
                    if (existing.id == accountId) existing.copy(lastBalanceUpdateAt = timestamp) else existing
                },
            )
        }
    }

    override suspend fun nextDisplayOrder(): Int {
        return (store.snapshot.value.accounts.maxOfOrNull { it.displayOrder } ?: -1) + 1
    }
}
