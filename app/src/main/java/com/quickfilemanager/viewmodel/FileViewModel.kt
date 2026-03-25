package com.quickfilemanager.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quickfilemanager.data.repository.FileRepository
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.domain.model.OperationResult
import com.quickfilemanager.domain.model.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val storageList: List<StorageInfo> = emptyList(),
    val isSelectionMode: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val operationProgress: OperationProgress? = null
)

data class OperationProgress(
    val current: Int,
    val total: Int,
    val fileName: String
)

@HiltViewModel
class FileViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileUiState())
    val uiState: StateFlow<FileUiState> = _uiState.asStateFlow()

    private val _operationResult = MutableSharedFlow<OperationResult>()
    val operationResult = _operationResult.asSharedFlow()

    init {
        loadStorageList()
        loadFiles(_uiState.value.currentPath)
    }

    fun loadStorageList() {
        viewModelScope.launch {
            val storageList = repository.getStorageList()
            _uiState.value = _uiState.value.copy(storageList = storageList)
        }
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.listFiles(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        files = files,
                        currentPath = path,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "加载失败",
                        isLoading = false
                    )
                }
            )
        }
    }

    fun navigateToFolder(path: String) {
        loadFiles(path)
        exitSelectionMode()
    }

    fun navigateUp(): Boolean {
        val currentPath = _uiState.value.currentPath
        val parentPath = repository.getParentPath(currentPath)
        return if (parentPath != null && !repository.isRootPath(currentPath)) {
            loadFiles(parentPath)
            exitSelectionMode()
            true
        } else {
            false
        }
    }

    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !_uiState.value.isSelectionMode,
            selectedFiles = emptySet()
        )
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedFiles = emptySet()
        )
    }

    fun toggleFileSelection(path: String) {
        val currentSelected = _uiState.value.selectedFiles
        val newSelected = if (currentSelected.contains(path)) {
            currentSelected - path
        } else {
            currentSelected + path
        }
        _uiState.value = _uiState.value.copy(selectedFiles = newSelected)
    }

    fun selectAll() {
        val allPaths = _uiState.value.files.map { it.path }.toSet()
        _uiState.value = _uiState.value.copy(selectedFiles = allPaths)
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet())
    }

    fun deleteFiles(paths: Set<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                repository.delete(path).collect { result ->
                    _operationResult.emit(result)
                    if (result is OperationResult.Success) {
                        loadFiles(_uiState.value.currentPath)
                    }
                }
            }
            exitSelectionMode()
        }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val result = repository.rename(path, newName)
            _operationResult.emit(result)
            if (result is OperationResult.Success) {
                loadFiles(_uiState.value.currentPath)
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = repository.createFolder(_uiState.value.currentPath, name)
            _operationResult.emit(result)
            if (result is OperationResult.Success) {
                loadFiles(_uiState.value.currentPath)
            }
        }
    }

    fun copyFiles(sourcePaths: Set<String>, targetPath: String) {
        viewModelScope.launch {
            sourcePaths.forEach { sourcePath ->
                repository.copy(sourcePath, targetPath).collect { result ->
                    when (result) {
                        is OperationResult.Progress -> {
                            _uiState.value = _uiState.value.copy(
                                isOperationInProgress = true,
                                operationProgress = OperationProgress(
                                    result.current,
                                    result.total,
                                    result.fileName
                                )
                            )
                        }
                        is OperationResult.Success, is OperationResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isOperationInProgress = false,
                                operationProgress = null
                            )
                            _operationResult.emit(result)
                        }
                    }
                }
            }
            exitSelectionMode()
        }
    }

    fun moveFiles(sourcePaths: Set<String>, targetPath: String) {
        viewModelScope.launch {
            sourcePaths.forEach { sourcePath ->
                val result = repository.move(sourcePath, targetPath)
                _operationResult.emit(result)
            }
            loadFiles(_uiState.value.currentPath)
            exitSelectionMode()
        }
    }

    fun searchFiles(query: String) {
        if (query.isBlank()) {
            loadFiles(_uiState.value.currentPath)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val currentFiles = _uiState.value.files
            val filteredFiles = currentFiles.filter {
                it.name.contains(query, ignoreCase = true)
            }
            _uiState.value = _uiState.value.copy(
                files = filteredFiles,
                isLoading = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
