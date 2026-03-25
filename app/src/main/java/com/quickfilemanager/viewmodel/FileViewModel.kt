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

enum class SortType { NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC }
enum class ClipboardAction { NONE, COPY, CUT }
enum class FileCategory { ALL, INTERNAL, DOWNLOADS, IMAGES, VIDEOS, AUDIO, DOCUMENTS, APKS, ARCHIVES, ROOT }

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
    val sortType: SortType = SortType.NAME_ASC,
    val showHiddenFiles: Boolean = false,
    val favoritePaths: Set<String> = emptySet(),
    val clipboardAction: ClipboardAction = ClipboardAction.NONE,
    val clipboardPaths: Set<String> = emptySet(),
    val clipboardTargetPath: String = "",
    val currentCategory: FileCategory = FileCategory.ALL,
    val showSystemFiles: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val language: String = "zh",
    val sortAscending: Boolean = true
)

data class OperationProgress(val current: Int, val total: Int, val fileName: String)

@HiltViewModel
class FileViewModel @Inject constructor(private val repository: FileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FileUiState())
    val uiState: StateFlow<FileUiState> = _uiState.asStateFlow()
    private val _operationResult = MutableSharedFlow<OperationResult>()
    val operationResult = _operationResult.asSharedFlow()

    init { loadStorageList(); loadFiles(_uiState.value.currentPath) }

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
                onSuccess = { files -> _uiState.value = _uiState.value.copy(files = filterAndSortFiles(files), currentPath = path, isLoading = false) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "加载失败", isLoading = false) }
            )
        }
    }

    fun loadCategory(category: FileCategory) {
        val path = when (category) {
            FileCategory.ALL -> Environment.getExternalStorageDirectory().absolutePath
            FileCategory.INTERNAL -> Environment.getExternalStorageDirectory().absolutePath
            FileCategory.DOWNLOADS -> Environment.getExternalStorageDirectory().absolutePath + "/Download"
            FileCategory.IMAGES -> Environment.getExternalStorageDirectory().absolutePath + "/DCIM"
            FileCategory.VIDEOS -> Environment.getExternalStorageDirectory().absolutePath + "/Movies"
            FileCategory.AUDIO -> Environment.getExternalStorageDirectory().absolutePath + "/Music"
            FileCategory.DOCUMENTS -> Environment.getExternalStorageDirectory().absolutePath + "/Documents"
            FileCategory.APKS -> Environment.getExternalStorageDirectory().absolutePath + "/Download"
            FileCategory.ARCHIVES -> Environment.getExternalStorageDirectory().absolutePath + "/Download"
            FileCategory.ROOT -> "/"
        }
        _uiState.value = _uiState.value.copy(currentCategory = category)
        loadFiles(path)
    }

    fun refreshCurrentPath() { loadFiles(_uiState.value.currentPath) }

    private fun filterAndSortFiles(files: List<FileItem>): List<FileItem> {
        val state = _uiState.value
        var filtered = if (state.showHiddenFiles) files else files.filter { !it.name.startsWith(".") }
        
        // 分类过滤
        if (state.currentCategory == FileCategory.IMAGES) filtered = filtered.filter { it.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") }
        else if (state.currentCategory == FileCategory.VIDEOS) filtered = filtered.filter { it.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv") }
        else if (state.currentCategory == FileCategory.AUDIO) filtered = filtered.filter { it.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") }
        else if (state.currentCategory == FileCategory.DOCUMENTS) filtered = filtered.filter { it.extension in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx") }
        else if (state.currentCategory == FileCategory.APKS) filtered = filtered.filter { it.extension == "apk" }
        else if (state.currentCategory == FileCategory.ARCHIVES) filtered = filtered.filter { it.extension in listOf("zip", "rar", "7z", "tar", "gz") }
        
        val dirFirst = compareBy<FileItem, Boolean> { !it.isDirectory }
        val sortBy = when (state.sortType) {
            SortType.NAME_ASC -> compareBy { it.name.lowercase() }
            SortType.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortType.SIZE_ASC -> compareBy { it.size }
            SortType.SIZE_DESC -> compareByDescending { it.size }
            SortType.DATE_ASC -> compareBy { it.lastModified }
            SortType.DATE_DESC -> compareByDescending { it.lastModified }
        }
        return filtered.sortedWith(dirFirst.then(sortBy))
    }

    fun setSortType(sortType: SortType) { _uiState.value = _uiState.value.copy(sortType = sortType); loadFiles(_uiState.value.currentPath) }
    fun toggleHiddenFiles() { _uiState.value = _uiState.value.copy(showHiddenFiles = !_uiState.value.showHiddenFiles); loadFiles(_uiState.value.currentPath) }
    fun toggleShowSystemFiles() { _uiState.value = _uiState.value.copy(showSystemFiles = !_uiState.value.showSystemFiles); loadFiles(_uiState.value.currentPath) }

    fun navigateToFolder(path: String) { loadFiles(path); exitSelectionMode() }
    fun navigateUp(): Boolean {
        val parentPath = repository.getParentPath(_uiState.value.currentPath)
        return if (parentPath != null && !repository.isRootPath(_uiState.value.currentPath)) { loadFiles(parentPath); exitSelectionMode(); true } else false
    }

    fun toggleSelectionMode() { _uiState.value = _uiState.value.copy(isSelectionMode = !_uiState.value.isSelectionMode, selectedFiles = emptySet()) }
    fun exitSelectionMode() { _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedFiles = emptySet()) }
    fun toggleFileSelection(path: String) {
        val current = _uiState.value.selectedFiles
        _uiState.value = _uiState.value.copy(selectedFiles = if (current.contains(path)) current - path else current + path)
    }
    fun selectAll() { _uiState.value = _uiState.value.copy(selectedFiles = _uiState.value.files.map { it.path }.toSet()) }
    fun deselectAll() { _uiState.value = _uiState.value.copy(selectedFiles = emptySet()) }

    fun toggleFavorite(path: String) {
        val favorites = _uiState.value.favoritePaths.toMutableSet()
        if (favorites.contains(path)) favorites.remove(path) else favorites.add(path)
        _uiState.value = _uiState.value.copy(favoritePaths = favorites)
    }
    fun isFavorite(path: String) = _uiState.value.favoritePaths.contains(path)

    fun copyToClipboard(paths: Set<String>) { _uiState.value = _uiState.value.copy(clipboardAction = ClipboardAction.COPY, clipboardPaths = paths, clipboardTargetPath = _uiState.value.currentPath) }
    fun cutToClipboard(paths: Set<String>) { _uiState.value = _uiState.value.copy(clipboardAction = ClipboardAction.CUT, clipboardPaths = paths, clipboardTargetPath = _uiState.value.currentPath) }
    fun pasteFromClipboard(targetPath: String? = null) {
        val state = _uiState.value
        if (state.clipboardAction == ClipboardAction.NONE || state.clipboardPaths.isEmpty()) return
        val destination = targetPath ?: state.currentPath
        viewModelScope.launch {
            if (state.clipboardAction == ClipboardAction.COPY) copyFiles(state.clipboardPaths, destination)
            else { state.clipboardPaths.forEach { _operationResult.emit(repository.move(it, destination)) }; loadFiles(_uiState.value.currentPath) }
            _uiState.value = _uiState.value.copy(clipboardAction = ClipboardAction.NONE, clipboardPaths = emptySet())
        }
    }
    fun clearClipboard() { _uiState.value = _uiState.value.copy(clipboardAction = ClipboardAction.NONE, clipboardPaths = emptySet()) }

    fun deleteFiles(paths: Set<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                repository.delete(path).collect { result ->
                    _operationResult.emit(result)
                    if (result is OperationResult.Success) loadFiles(_uiState.value.currentPath)
                }
            }
            exitSelectionMode()
        }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val result = repository.rename(path, newName)
            _operationResult.emit(result)
            if (result is OperationResult.Success) loadFiles(_uiState.value.currentPath)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = repository.createFolder(_uiState.value.currentPath, name)
            _operationResult.emit(result)
            if (result is OperationResult.Success) loadFiles(_uiState.value.currentPath)
        }
    }

    fun copyFiles(sourcePaths: Set<String>, targetPath: String) {
        viewModelScope.launch {
            sourcePaths.forEach { sourcePath ->
                repository.copy(sourcePath, targetPath).collect { result ->
                    when (result) {
                        is OperationResult.Progress -> _uiState.value = _uiState.value.copy(isOperationInProgress = true, operationProgress = OperationProgress(result.current, result.total, result.fileName))
                        is OperationResult.Success, is OperationResult.Error -> { _uiState.value = _uiState.value.copy(isOperationInProgress = false, operationProgress = null); _operationResult.emit(result) }
                    }
                }
            }
            exitSelectionMode()
        }
    }

    fun searchFiles(query: String) {
        if (query.isBlank()) { loadFiles(_uiState.value.currentPath); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val filtered = _uiState.value.files.filter { it.name.contains(query, ignoreCase = true) }
            _uiState.value = _uiState.value.copy(files = filtered, isLoading = false)
        }
    }

    fun setFirstLaunchComplete() { _uiState.value = _uiState.value.copy(isFirstLaunch = false) }
    fun setLanguage(lang: String) { _uiState.value = _uiState.value.copy(language = lang) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
