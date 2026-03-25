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

/**
 * 排序方式
 */
enum class SortType {
    NAME_ASC, NAME_DESC,
    SIZE_ASC, SIZE_DESC,
    DATE_ASC, DATE_DESC
}

/**
 * 剪贴板操作类型
 */
enum class ClipboardAction {
    NONE, COPY, CUT
}

/**
 * 文件UI状态
 */
data class FileUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val storageList: List<StorageInfo> = emptyList(),
    val isSelectionMode: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val operationProgress: OperationProgress? = null,
    
    // 新增功能
    val sortType: SortType = SortType.NAME_ASC,
    val showHiddenFiles: Boolean = false,
    val favoritePaths: Set<String> = emptySet(),
    val clipboardAction: ClipboardAction = ClipboardAction.NONE,
    val clipboardPaths: Set<String> = emptySet(),
    val clipboardTargetPath: String = "",
    val tabs: List<FileTab> = listOf(FileTab(Environment.getExternalStorageDirectory().absolutePath)),
    val currentTabIndex: Int = 0,
    val currentViewMode: ViewMode = ViewMode.LIST,
    val selectedFileInfo: FileItem? = null
)

/**
 * 文件标签页
 */
data class FileTab(
    val path: String,
    val name: String = ""
) {
    companion object {
        fun fromPath(path: String): FileTab {
            val name = path.substringAfterLast("/").ifEmpty { "内部存储" }
            return FileTab(path, name)
        }
    }
}

/**
 * 视图模式
 */
enum class ViewMode {
    LIST, GRID
}

/**
 * 操作进度
 */
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
                    val filteredFiles = filterFiles(files)
                    val sortedFiles = sortFiles(filteredFiles)
                    _uiState.value = _uiState.value.copy(
                        files = sortedFiles,
                        currentPath = path,
                        isLoading = false
                    )
                    // 更新当前标签
                    updateCurrentTab(path)
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

    private fun filterFiles(files: List<FileItem>): List<FileItem> {
        val state = _uiState.value
        return if (state.showHiddenFiles) {
            files
        } else {
            files.filter { !it.name.startsWith(".") }
        }
    }

    private fun sortFiles(files: List<FileItem>): List<FileItem> {
        val sortType = _uiState.value.sortType
        return when (sortType) {
            SortType.NAME_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortType.NAME_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.name.lowercase().compareTo(it.name.lowercase()) }))
            SortType.SIZE_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            SortType.SIZE_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
            SortType.DATE_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            SortType.DATE_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
        }
    }

    fun setSortType(sortType: SortType) {
        _uiState.value = _uiState.value.copy(sortType = sortType)
        loadFiles(_uiState.value.currentPath)
    }

    fun toggleHiddenFiles() {
        _uiState.value = _uiState.value.copy(showHiddenFiles = !_uiState.value.showHiddenFiles)
        loadFiles(_uiState.value.currentPath)
    }

    fun setViewMode(viewMode: ViewMode) {
        _uiState.value = _uiState.value.copy(currentViewMode = viewMode)
    }

    // 标签页管理
    fun addNewTab(path: String = Environment.getExternalStorageDirectory().absolutePath) {
        val tabs = _uiState.value.tabs.toMutableList()
        val newTab = FileTab.fromPath(path)
        if (!tabs.any { it.path == path }) {
            tabs.add(newTab)
            _uiState.value = _uiState.value.copy(
                tabs = tabs,
                currentTabIndex = tabs.size - 1
            )
            loadFiles(path)
        } else {
            val index = tabs.indexOfFirst { it.path == path }
            _uiState.value = _uiState.value.copy(currentTabIndex = index)
            loadFiles(path)
        }
    }

    fun switchToTab(index: Int) {
        if (index in _uiState.value.tabs.indices) {
            val tab = _uiState.value.tabs[index]
            _uiState.value = _uiState.value.copy(currentTabIndex = index)
            loadFiles(tab.path)
        }
    }

    fun closeTab(index: Int) {
        val tabs = _uiState.value.tabs.toMutableList()
        if (tabs.size > 1 && index in tabs.indices) {
            tabs.removeAt(index)
            val newIndex = if (_uiState.value.currentTabIndex >= tabs.size) {
                tabs.size - 1
            } else {
                _uiState.value.currentTabIndex
            }
            _uiState.value = _uiState.value.copy(
                tabs = tabs,
                currentTabIndex = newIndex
            )
            loadFiles(tabs[newIndex].path)
        }
    }

    private fun updateCurrentTab(path: String) {
        val tabs = _uiState.value.tabs.toMutableList()
        val index = _uiState.value.currentTabIndex
        if (index in tabs.indices) {
            tabs[index] = FileTab.fromPath(path)
            _uiState.value = _uiState.value.copy(tabs = tabs)
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

    // 选择模式
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

    // 收藏功能
    fun toggleFavorite(path: String) {
        val favorites = _uiState.value.favoritePaths.toMutableSet()
        if (favorites.contains(path)) {
            favorites.remove(path)
        } else {
            favorites.add(path)
        }
        _uiState.value = _uiState.value.copy(favoritePaths = favorites)
    }

    fun isFavorite(path: String): Boolean {
        return _uiState.value.favoritePaths.contains(path)
    }

    // 剪贴板操作
    fun copyToClipboard(paths: Set<String>) {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.COPY,
            clipboardPaths = paths,
            clipboardTargetPath = _uiState.value.currentPath
        )
    }

    fun cutToClipboard(paths: Set<String>) {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.CUT,
            clipboardPaths = paths,
            clipboardTargetPath = _uiState.value.currentPath
        )
    }

    fun pasteFromClipboard(targetPath: String? = null) {
        val state = _uiState.value
        if (state.clipboardAction == ClipboardAction.NONE || state.clipboardPaths.isEmpty()) {
            return
        }

        val destination = targetPath ?: state.currentPath

        viewModelScope.launch {
            if (state.clipboardAction == ClipboardAction.COPY) {
                copyFiles(state.clipboardPaths, destination)
            } else {
                state.clipboardPaths.forEach { path ->
                    val result = repository.move(path, destination)
                    _operationResult.emit(result)
                }
                loadFiles(_uiState.value.currentPath)
            }
            
            // 清空剪贴板
            _uiState.value = _uiState.value.copy(
                clipboardAction = ClipboardAction.NONE,
                clipboardPaths = emptySet()
            )
        }
    }

    fun clearClipboard() {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.NONE,
            clipboardPaths = emptySet()
        )
    }

    // 文件操作
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

    // 文件详情
    fun showFileInfo(file: FileItem?) {
        _uiState.value = _uiState.value.copy(selectedFileInfo = file)
    }

    fun clearFileInfo() {
        _uiState.value = _uiState.value.copy(selectedFileInfo = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
