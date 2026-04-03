package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeActiveAccounts(): Flow<List<AccountEntity>>
    fun observeArchivedAccounts(): Flow<List<AccountEntity>>
    suspend fun queryActiveAccounts(): List<AccountEntity>
    suspend fun queryArchivedAccounts(): List<AccountEntity>
    suspend fun getAccountById(id: Long): AccountEntity?
    suspend fun isActiveNameAvailable(name: String, excludeId: Long = -1): Boolean
    suspend fun createAccount(account: AccountEntity): Long
    suspend fun updateAccount(account: AccountEntity)
    suspend fun archiveAccount(accountId: Long, archivedAt: Long)
    suspend fun updateLastUsedAt(accountId: Long, timestamp: Long)
    suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long)
    suspend fun nextDisplayOrder(): Int
}
