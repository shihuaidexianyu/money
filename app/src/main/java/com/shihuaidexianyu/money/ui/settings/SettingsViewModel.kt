package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AmountDisplayStyle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.WeekStart
import com.shihuaidexianyu.money.domain.usecase.ExportJsonResult
import com.shihuaidexianyu.money.domain.usecase.ExportJsonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val lastExportUri: Uri? = null,
    val lastExportFileName: String? = null,
    val lastExportRelativePath: String? = null,
    val lastExportedAt: Long? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val exportJsonUseCase: ExportJsonUseCase,
) : ViewModel() {
    private val exportState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        exportState,
    ) { settings, export ->
        export.copy(settings = settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun updateHomePeriod(period: HomePeriod) {
        viewModelScope.launch { settingsRepository.updateHomePeriod(period) }
    }

    fun updateWeekStart(weekStart: WeekStart) {
        viewModelScope.launch { settingsRepository.updateWeekStart(weekStart) }
    }

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch { settingsRepository.updateCurrencySymbol(symbol) }
    }

    fun updateAmountDisplayStyle(style: AmountDisplayStyle) {
        viewModelScope.launch { settingsRepository.updateAmountDisplayStyle(style) }
    }

    fun updateShowStaleMark(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowStaleMark(show) }
    }

    fun updateAccountSortMode(mode: AccountSortMode) {
        viewModelScope.launch { settingsRepository.updateAccountSortMode(mode) }
    }

    fun exportJson() {
        if (exportState.value.isExporting) return
        viewModelScope.launch {
            exportState.value = exportState.value.copy(
                isExporting = true,
                exportError = null,
            )
            runCatching { exportJsonUseCase() }
                .onSuccess { result ->
                    exportState.value = exportState.value.toSuccessState(result)
                }
                .onFailure { error ->
                    exportState.value = exportState.value.copy(
                        isExporting = false,
                        exportError = error.message ?: "导出失败，请稍后重试",
                    )
                }
        }
    }

    private fun SettingsUiState.toSuccessState(result: ExportJsonResult): SettingsUiState {
        return copy(
            isExporting = false,
            exportError = null,
            lastExportUri = result.uri,
            lastExportFileName = result.fileName,
            lastExportRelativePath = result.relativePath,
            lastExportedAt = result.exportedAt,
        )
    }
}
