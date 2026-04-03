package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
) : AccountRepository {
    override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActiveAccounts()

    override fun observeArchivedAccounts(): Flow<List<AccountEntity>> = accountDao.observeArchivedAccounts()

    override suspend fun queryActiveAccounts(): List<AccountEntity> = accountDao.queryActiveAccounts()

    override suspend fun queryArchivedAccounts(): List<AccountEntity> = accountDao.queryArchivedAccounts()

    override suspend fun getAccountById(id: Long): AccountEntity? = accountDao.queryById(id)

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        return accountDao.countActiveAccountsByName(name.trim(), excludeId) == 0
    }

    override suspend fun createAccount(account: AccountEntity): Long = accountDao.insert(account)

    override suspend fun updateAccount(account: AccountEntity) = accountDao.update(account)

    override suspend fun archiveAccount(accountId: Long, archivedAt: Long) {
        accountDao.archiveAccount(accountId, archivedAt)
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        accountDao.updateLastUsedAt(accountId, timestamp)
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?) {
        accountDao.updateLastBalanceUpdateAt(accountId, timestamp)
    }

    override suspend fun nextDisplayOrder(): Int = accountDao.nextDisplayOrder()
}

