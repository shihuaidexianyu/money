package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.data.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ReorderAccountItemUiModel(
    val id: Long,
    val name: String,
    val groupType: AccountGroupType,
)

data class ReorderAccountsUiState(
    val isLoading: Boolean = true,
    val isManualSortMode: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val accounts: List<ReorderAccountItemUiModel> = emptyList(),
)

sealed interface ReorderAccountsEvent {
    data object Saved : ReorderAccountsEvent
}

class ReorderAccountsViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val updateAccountDisplayOrderUseCase: UpdateAccountDisplayOrderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReorderAccountsUiState())
    val uiState: StateFlow<ReorderAccountsUiState> = _uiState.asStateFlow()

    private val events = Channel<ReorderAccountsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.observeSettings().first()
            val accounts = accountRepository.queryActiveAccounts()
                .sortedBy { it.displayOrder }
                .map {
                    ReorderAccountItemUiModel(
                        id = it.id,
                        name = it.name,
                        groupType = AccountGroupType.fromValue(it.groupType),
                    )
                }
            _uiState.value = ReorderAccountsUiState(
                isLoading = false,
                isManualSortMode = settings.accountSortMode == AccountSortMode.MANUAL,
                accounts = accounts,
            )
        }
    }

    fun moveUp(accountId: Long) {
        val items = _uiState.value.accounts.toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index <= 0) return
        val item = items.removeAt(index)
        items.add(index - 1, item)
        _uiState.value = _uiState.value.copy(accounts = items)
    }

    fun moveDown(accountId: Long) {
        val items = _uiState.value.accounts.toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index < 0 || index >= items.lastIndex) return
        val item = items.removeAt(index)
        items.add(index + 1, item)
        _uiState.value = _uiState.value.copy(accounts = items)
    }

    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            runCatching {
                updateAccountDisplayOrderUseCase(_uiState.value.accounts.map { it.id })
            }.onSuccess {
                events.send(ReorderAccountsEvent.Saved)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "保存排序失败",
                )
            }
        }
    }
}
