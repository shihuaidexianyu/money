package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isExporting: Boolean = false,
)

sealed interface SettingsEffect {
    data class ExportReady(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
    ) : SettingsEffect

    data class ShowMessage(
        override val message: String,
    ) : SettingsEffect, UiEffect.HasMessage
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val buildExportJsonUseCase: BuildExportJsonUseCase,
    private val exportJsonFileWriter: ExportJsonFileWriter,
) : ViewModel() {
    private val isExporting = MutableStateFlow(false)
    private val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.observeSettings(),
            isExporting,
        ) { settings, exporting ->
            SettingsUiState(settings = settings, isExporting = exporting)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    fun updateHomePeriod(period: HomePeriod) {
        viewModelScope.launch { settingsRepository.updateHomePeriod(period) }
    }

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch { settingsRepository.updateCurrencySymbol(symbol) }
    }

    fun updateShowStaleMark(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowStaleMark(show) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(themeMode) }
    }

    fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        viewModelScope.launch { settingsRepository.updateAmountColorMode(amountColorMode) }
    }

    fun exportData() {
        if (isExporting.value) return
        viewModelScope.launch {
            isExporting.value = true
            runCatching {
                val exportedAt = System.currentTimeMillis()
                val json = buildExportJsonUseCase(exportedAt = exportedAt)
                exportJsonFileWriter.write(json = json, timestamp = exportedAt)
            }.onSuccess { file ->
                effects.emit(
                    SettingsEffect.ExportReady(
                        uri = file.uri,
                        fileName = file.fileName,
                        mimeType = file.mimeType,
                    ),
                )
            }.onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导出失败")))
            }
            isExporting.value = false
        }
    }
}
