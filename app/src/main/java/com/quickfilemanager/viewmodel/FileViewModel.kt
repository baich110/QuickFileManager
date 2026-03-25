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
 * 文件分类枚举
 */
enum class FileCategory(val displayName: String, val icon: String) {
    ALL("全部", "folder"),
    INTERNAL("内部存储", "storage"),
    DOWNLOADS("下载", "download"),
    IMAGES("图片", "image"),
    VIDEOS("视频", "video"),
    AUDIO("音频", "audio"),
    DOCUMENTS("文档", "document"),
    APKS("安装包", "android"),
    ARCHIVES("压缩包", "archive")
}

/**
 * 排序类型枚举
 */
enum class SortType(val displayName: String) {
    NAME_ASC("名称 ↑"),
    NAME_DESC("名称 ↓"),
    SIZE_ASC("大小 ↑"),
    SIZE_DESC("大小 ↓"),
    DATE_ASC("日期 ↑"),
    DATE_DESC("日期 ↓")
}

/**
 * 剪贴板操作类型
 */
enum class ClipboardAction { NONE, COPY, CUT }

data class FileUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<FileItem> = emptyList(),
    val allFiles: List<FileItem> = emptyList(), // 原始文件列表（分类过滤前）
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val storageList: List<StorageInfo> = emptyList(),
    val isSelectionMode: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val operationProgress: OperationProgress? = null,
    
    // 新增功能
    val currentCategory: FileCategory = FileCategory.ALL,
    val sortType: SortType = SortType.NAME_ASC,
    val clipboardAction: ClipboardAction = ClipboardAction.NONE,
    val clipboardFiles: Set<String> = emptySet(),
    val clipboardTargetPath: String = "",
    val favoritePaths: Set<String> = emptySet(),
    
    // 设置相关
    val showHiddenFiles: Boolean = false,
    val sortAscending: Boolean = true,
    
    // 用户协议相关
    val hasAcceptedAgreement: Boolean = false
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
                    val sortedFiles = sortFiles(files)
                    _uiState.value = _uiState.value.copy(
                        files = sortedFiles,
                        allFiles = sortedFiles,
                        currentPath = path,
                        isLoading = false,
                        currentCategory = FileCategory.INTERNAL
                    )
                    applyCategoryFilter()
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

    /**
     * 切换分类
     */
    fun selectCategory(category: FileCategory) {
        _uiState.value = _uiState.value.copy(currentCategory = category)
        applyCategoryFilter()
    }

    /**
     * 根据分类过滤文件
     */
    private fun applyCategoryFilter() {
        val allFiles = _uiState.value.allFiles
        val category = _uiState.value.currentCategory
        
        val filteredFiles = when (category) {
            FileCategory.ALL -> allFiles
            FileCategory.INTERNAL -> allFiles
            FileCategory.DOWNLOADS -> allFiles.filter { 
                it.path.contains("/Download", ignoreCase = true) || 
                it.path.contains("/download", ignoreCase = true)
            }
            FileCategory.IMAGES -> allFiles.filter { 
                it.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
            }
            FileCategory.VIDEOS -> allFiles.filter { 
                it.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm")
            }
            FileCategory.AUDIO -> allFiles.filter { 
                it.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "amr")
            }
            FileCategory.DOCUMENTS -> allFiles.filter { 
                it.extension in listOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "odt")
            }
            FileCategory.APKS -> allFiles.filter { it.extension == "apk" }
            FileCategory.ARCHIVES -> allFiles.filter { 
                it.extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2")
            }
        }
        
        _uiState.value = _uiState.value.copy(files = sortFiles(filteredFiles))
    }

    /**
     * 排序文件
     */
    private fun sortFiles(files: List<FileItem>): List<FileItem> {
        val sortType = _uiState.value.sortType
        return when (sortType) {
            SortType.NAME_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortType.NAME_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.name.lowercase().compareTo(it.name) }))
            SortType.SIZE_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            SortType.SIZE_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
            SortType.DATE_ASC -> files.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            SortType.DATE_DESC -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
        }
    }

    /**
     * 设置排序方式
     */
    fun setSortType(sortType: SortType) {
        _uiState.value = _uiState.value.copy(sortType = sortType)
        _uiState.value = _uiState.value.copy(
            files = sortFiles(_uiState.value.files),
            allFiles = sortFiles(_uiState.value.allFiles)
        )
    }

    /**
     * 复制文件到剪贴板
     */
    fun copyToClipboard(paths: Set<String>) {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.COPY,
            clipboardFiles = paths,
            clipboardTargetPath = ""
        )
        exitSelectionMode()
    }

    /**
     * 剪切文件到剪贴板
     */
    fun cutToClipboard(paths: Set<String>) {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.CUT,
            clipboardFiles = paths,
            clipboardTargetPath = ""
        )
        exitSelectionMode()
    }

    /**
     * 粘贴文件
     */
    fun pasteFiles(targetPath: String) {
        viewModelScope.launch {
            val clipboardFiles = _uiState.value.clipboardFiles
            val action = _uiState.value.clipboardAction
            
            clipboardFiles.forEach { sourcePath ->
                when (action) {
                    ClipboardAction.COPY -> {
                        repository.copy(sourcePath, targetPath).collect { result ->
                            _operationResult.emit(result)
                        }
                    }
                    ClipboardAction.CUT -> {
                        val result = repository.move(sourcePath, targetPath)
                        _operationResult.emit(result)
                    }
                    ClipboardAction.NONE -> { /* 无操作 */ }
                }
            }
            
            // 清空剪贴板
            clearClipboard()
            loadFiles(_uiState.value.currentPath)
        }
    }

    /**
     * 清空剪贴板
     */
    fun clearClipboard() {
        _uiState.value = _uiState.value.copy(
            clipboardAction = ClipboardAction.NONE,
            clipboardFiles = emptySet(),
            clipboardTargetPath = ""
        )
    }

    /**
     * 切换收藏状态
     */
    fun toggleFavorite(path: String) {
        val currentFavorites = _uiState.value.favoritePaths
        val newFavorites = if (currentFavorites.contains(path)) {
            currentFavorites - path
        } else {
            currentFavorites + path
        }
        _uiState.value = _uiState.value.copy(favoritePaths = newFavorites)
    }

    /**
     * 检查是否有剪贴板内容
     */
    fun hasClipboardContent(): Boolean = _uiState.value.clipboardAction != ClipboardAction.NONE

    /**
     * 接受用户协议
     */
    fun acceptAgreement() {
        _uiState.value = _uiState.value.copy(hasAcceptedAgreement = true)
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
