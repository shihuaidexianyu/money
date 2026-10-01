package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.R
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.backup.BackupImportCoordinator
import com.shihuaidexianyu.money.data.backup.BackupFileReader
import com.shihuaidexianyu.money.data.backup.ImportHistoryWithRollbackEligibility
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.StagedImportPreview
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.data.export.ExportShareFile
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class SettingsUiState(
    val portableSettings: PortableSettings = PortableSettings(),
    val devicePreferences: DevicePreferences = DevicePreferences(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importHistory: List<ImportReceipt> = emptyList(),
    val rollbackEligibleReceiptId: String? = null,
    val availableSafetyReceiptIds: Set<String> = emptySet(),
    val pendingExport: ExportShareFile? = null,
    val pendingImportPreview: StagedImportPreview? = null,
    val isHistoryLoading: Boolean = false,
    val historyLoadFailed: Boolean = false,
)

private data class PendingBackupWork(
    val export: ExportShareFile? = null,
    val importPreview: StagedImportPreview? = null,
    val historyLoading: Boolean = false,
    val historyLoadFailed: Boolean = false,
)

internal suspend fun commitPortableSettingsMutation(
    mutation: suspend () -> Unit,
    refreshImportHistory: () -> Unit,
): Result<Unit> = try {
    mutation()
    refreshImportHistory()
    Result.success(Unit)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal suspend fun <T> rollbackAndRefreshImportHistory(
    rollback: suspend () -> T,
    refreshImportHistory: () -> Unit,
): Result<T> = try {
    val result = rollback()
    refreshImportHistory()
    Result.success(result)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    refreshImportHistory()
    Result.failure(error)
}

sealed interface SettingsEffect {
    data class ExportReady(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val saveToDocument: Boolean = true,
    ) : SettingsEffect

    data class ImportFinished(
        val receipt: ImportReceipt,
    ) : SettingsEffect, UiEffect.HasMessage {
        override val message: String = "导入完成"
    }

    data class RollbackFinished(
        val receipt: ImportReceipt,
    ) : SettingsEffect, UiEffect.HasMessage {
        override val message: String = "已撤销并生成新的保护快照"
    }

    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : SettingsEffect, UiEffect.HasMessage
}

class SettingsViewModel(
    private val portableSettingsRepository: PortableSettingsRepository,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val buildExportSnapshotUseCase: BuildExportSnapshotUseCase,
    private val exportJsonFileWriter: ExportJsonFileWriter,
    private val backupFileReader: BackupFileReader,
    private val backupImportCoordinator: BackupImportCoordinator,
    private val clockProvider: ClockProvider,
    private val forceRefreshNotificationPrivacy: suspend () -> Unit = {},
    private val onNotificationPrivacyChanging: (Boolean) -> Unit = {},
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val isExporting = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)
    private val importHistoryState = MutableStateFlow(emptyImportHistory())
    private val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var importHistoryLoadJob: Job? = null
    private val pendingWork = MutableStateFlow(PendingBackupWork(export = restoredExport()))

    val uiState: StateFlow<SettingsUiState> =
        combine(
            portableSettingsRepository.observe(),
            devicePreferencesRepository.observe(),
            isExporting,
            isImporting,
            importHistoryState,
        ) { portable, device, exporting, importing, history ->
            SettingsUiState(
                portableSettings = portable,
                devicePreferences = device,
                isExporting = exporting,
                isImporting = importing,
                importHistory = history.receipts,
                rollbackEligibleReceiptId = history.rollbackEligibleReceiptId,
                availableSafetyReceiptIds = history.availableSafetyReceiptIds,
            )
        }.combine(pendingWork) { state, pending ->
            state.copy(pendingExport = pending.export, pendingImportPreview = pending.importPreview,
                isHistoryLoading = pending.historyLoading, historyLoadFailed = pending.historyLoadFailed)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    init {
        refreshImportHistory()
        savedStateHandle.get<String>(IMPORT_STAGE_KEY)?.let { stageId -> restoreImportPreview(stageId) }
    }

    fun refreshImportHistory() {
        importHistoryLoadJob?.cancel()
        pendingWork.update { it.copy(historyLoading = true, historyLoadFailed = false) }
        importHistoryLoadJob = viewModelScope.launch {
            importHistoryState.value = try {
                withContext(Dispatchers.IO) {
                    backupImportCoordinator.historyWithRollbackEligibility()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                pendingWork.update { it.copy(historyLoadFailed = true) }
                importHistoryState.value.copy(rollbackEligibleReceiptId = null)
            }
            pendingWork.update { it.copy(historyLoading = false) }
        }
    }

    fun updateCurrencySymbol(symbol: String) {
        commitPortableSettingsChange {
            portableSettingsRepository.updateCurrencySymbol(symbol)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { devicePreferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        commitPortableSettingsChange {
            portableSettingsRepository.updateAmountColorMode(amountColorMode)
        }
    }

    fun updateRelockDelay(delay: AppRelockDelay) {
        viewModelScope.launch { devicePreferencesRepository.updateRelockDelay(delay) }
    }

    fun updateHideNotificationAmounts(enabled: Boolean) {
        viewModelScope.launch {
            onNotificationPrivacyChanging(enabled)
            devicePreferencesRepository.updateHideNotificationAmounts(enabled)
            forceRefreshNotificationPrivacy()
        }
    }

    fun updateHideRecentTasks(enabled: Boolean) {
        viewModelScope.launch { devicePreferencesRepository.updateHideRecentTasks(enabled) }
    }

    fun updateHideInAppAmounts(enabled: Boolean) {
        viewModelScope.launch { devicePreferencesRepository.updateHideInAppAmounts(enabled) }
    }

    fun exportData() = prepareExport(saveToDocument = true)
    fun shareData() = prepareExport(saveToDocument = false)
    fun exportSafetySnapshot(receiptId: String) = prepareExport(saveToDocument = true, receiptId = receiptId)

    private fun prepareExport(saveToDocument: Boolean, receiptId: String? = null) {
        if (isExporting.value || isImporting.value) return
        isExporting.value = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val exportedAt = clockProvider.nowMillis()
                    val snapshot = if (receiptId == null) buildExportSnapshotUseCase(exportedAt = exportedAt)
                        else backupImportCoordinator.readSafetySnapshot(receiptId)
                    exportJsonFileWriter.write(snapshot = snapshot, timestamp = exportedAt)
                }
                if (saveToDocument) setPendingExport(file)
                effects.emit(
                    SettingsEffect.ExportReady(
                        uri = file.uri,
                        fileName = file.fileName,
                        mimeType = file.mimeType,
                        saveToDocument = saveToDocument,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导出失败")))
            } finally {
                isExporting.value = false
            }
        }
    }

    fun resumeExport() {
        val file = pendingWork.value.export ?: return
        if (isExporting.value || isImporting.value) return
        effects.tryEmit(SettingsEffect.ExportReady(file.uri, file.fileName, file.mimeType))
    }

    fun saveExportToDocument(destination: Uri?) {
        if (isExporting.value || isImporting.value) return
        val source = pendingWork.value.export ?: return
        if (destination == null) {
            effects.tryEmit(SettingsEffect.ShowMessage("", R.string.settings_backup_cancelled))
            return
        }
        isExporting.value = true
        viewModelScope.launch {
            try {
                exportJsonFileWriter.saveToDocument(source.uri, destination)
                setPendingExport(null)
                effects.emit(SettingsEffect.ShowMessage("", R.string.settings_backup_saved))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                effects.emit(SettingsEffect.ShowMessage("", R.string.settings_backup_save_failed))
            } finally {
                isExporting.value = false
            }
        }
    }

    private fun setPendingExport(file: ExportShareFile?) {
        savedStateHandle[EXPORT_URI_KEY] = file?.uri?.toString()
        savedStateHandle[EXPORT_NAME_KEY] = file?.fileName
        pendingWork.update { it.copy(export = file) }
    }

    private fun restoredExport(): ExportShareFile? {
        val uri = savedStateHandle.get<String>(EXPORT_URI_KEY) ?: return null
        val name = savedStateHandle.get<String>(EXPORT_NAME_KEY) ?: return null
        return ExportShareFile(Uri.parse(uri), name)
    }

    fun dismissImportPreview() {
        if (isImporting.value) return
        savedStateHandle.remove<String>(IMPORT_STAGE_KEY)
        pendingWork.update { it.copy(importPreview = null) }
    }

    private fun restoreImportPreview(stageId: String) {
        isImporting.value = true
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { backupImportCoordinator.preview(stageId) }
                pendingWork.update { it.copy(importPreview = preview) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                savedStateHandle.remove<String>(IMPORT_STAGE_KEY)
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("待导入备份已不可用，请重新选择文件")))
            } finally {
                isImporting.value = false
            }
        }
    }

    fun previewImport(uri: Uri) {
        if (isImporting.value || isExporting.value) return
        isImporting.value = true
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val stage = backupFileReader.stage(uri, clockProvider.nowMillis())
                    backupImportCoordinator.preview(stage.id)
                }
                savedStateHandle[IMPORT_STAGE_KEY] = preview.stageId
                pendingWork.update { it.copy(importPreview = preview) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("无法读取备份文件")))
            } finally {
                isImporting.value = false
            }
        }
    }

    fun confirmImport(stageId: String) {
        if (isImporting.value || isExporting.value || pendingWork.value.importPreview?.stageId != stageId) return
        isImporting.value = true
        viewModelScope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) { backupImportCoordinator.confirm(stageId) }
                savedStateHandle.remove<String>(IMPORT_STAGE_KEY)
                pendingWork.update { it.copy(importPreview = null) }
                refreshImportHistory()
                effects.emit(SettingsEffect.ImportFinished(receipt))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导入失败")))
            } finally {
                isImporting.value = false
            }
        }
    }

    fun rollbackImport(receiptId: String) {
        if (isImporting.value || isExporting.value) return
        isImporting.value = true
        viewModelScope.launch {
            try {
                rollbackAndRefreshImportHistory(
                    rollback = { withContext(Dispatchers.IO) { backupImportCoordinator.rollback(receiptId) } },
                    refreshImportHistory = ::refreshImportHistory,
                ).onSuccess { effects.emit(SettingsEffect.RollbackFinished(it)) }
                    .onFailure { effects.emit(SettingsEffect.ShowMessage(it.userMessage("撤销导入失败"))) }
            } finally {
                isImporting.value = false
            }
        }
    }

    private fun commitPortableSettingsChange(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            commitPortableSettingsMutation(
                mutation = {
                    withContext(Dispatchers.IO) { mutation() }
                },
                refreshImportHistory = ::refreshImportHistory,
            ).onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("设置保存失败")))
            }
        }
    }

    private companion object {
        const val EXPORT_URI_KEY = "pending_backup_export_uri"
        const val EXPORT_NAME_KEY = "pending_backup_export_name"
        const val IMPORT_STAGE_KEY = "pending_backup_import_stage"
    }
}

private fun emptyImportHistory() = ImportHistoryWithRollbackEligibility(
    receipts = emptyList(),
    rollbackEligibleReceiptId = null,
)
