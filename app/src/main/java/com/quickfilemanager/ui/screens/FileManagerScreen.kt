package com.quickfilemanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.ui.components.FileListItem
import com.quickfilemanager.ui.components.RenameDialog
import com.quickfilemanager.ui.components.CreateFolderDialog
import com.quickfilemanager.ui.components.OperationResultDialog
import com.quickfilemanager.viewmodel.ClipboardAction
import com.quickfilemanager.viewmodel.FileCategory
import com.quickfilemanager.viewmodel.FileViewModel
import com.quickfilemanager.viewmodel.SortType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultIsError by remember { mutableStateOf(false) }
    
    // 监控操作结果
    LaunchedEffect(Unit) {
        viewModel.operationResult.collect { result ->
            when (result) {
                is com.quickfilemanager.domain.model.OperationResult.Success -> {
                    resultMessage = result.message
                    resultIsError = false
                    showResultDialog = true
                }
                is com.quickfilemanager.domain.model.OperationResult.Error -> {
                    resultMessage = result.message
                    resultIsError = true
                    showResultDialog = true
                }
                is com.quickfilemanager.domain.model.OperationResult.Progress -> { /* 进度更新 */ }
            }
        }
    }
    
    // 搜索功能
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            viewModel.searchFiles(searchQuery)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索文件...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (uiState.isSelectionMode) "已选择 ${uiState.selectedFiles.size} 项" 
                                   else uiState.currentCategory.displayName,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                        }
                    } else if (viewModel.navigateUp()) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    }
                },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { 
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) {
                            searchQuery = ""
                            viewModel.loadFiles(uiState.currentPath)
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    
                    // 更多菜单
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("刷新") },
                            onClick = {
                                viewModel.loadFiles(uiState.currentPath)
                                showMoreMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.showHiddenFiles) "隐藏文件" else "显示文件") },
                            onClick = {
                                viewModel.loadFiles(uiState.currentPath)
                                showMoreMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Visibility, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("排序") },
                            onClick = {
                                showSortMenu = true
                                showMoreMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Sort, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("新建文件夹") },
                            onClick = {
                                showCreateFolderDialog = true
                                showMoreMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }
                        )
                    }
                    
                    // 排序子菜单
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortType.entries.forEach { sortType ->
                            DropdownMenuItem(
                                text = { Text(sortType.displayName) },
                                onClick = {
                                    viewModel.setSortType(sortType)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (uiState.sortType == sortType) {
                                        Icon(Icons.Default.Check, null)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 底部分类导航栏
            NavigationBar {
                FileCategory.entries.filter { 
                    it != FileCategory.ROOT 
                }.forEach { category ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                imageVector = when (category) {
                                    FileCategory.ALL -> Icons.Default.Folder
                                    FileCategory.INTERNAL -> Icons.Default.Storage
                                    FileCategory.DOWNLOADS -> Icons.Default.Download
                                    FileCategory.IMAGES -> Icons.Default.Image
                                    FileCategory.VIDEOS -> Icons.Default.VideoFile
                                    FileCategory.AUDIO -> Icons.Default.AudioFile
                                    FileCategory.DOCUMENTS -> Icons.Default.Description
                                    FileCategory.APKS -> Icons.Default.Android
                                    FileCategory.ARCHIVES -> Icons.Default.Archive
                                },
                                contentDescription = category.displayName
                            )
                        },
                        label = { Text(category.displayName, maxLines = 1) },
                        selected = uiState.currentCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 粘贴按钮（当剪贴板有内容时显示）
                AnimatedVisibility(
                    visible = uiState.clipboardAction != ClipboardAction.NONE,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.pasteFiles(uiState.currentPath) },
                        icon = { Icon(Icons.Default.ContentPaste, "粘贴") },
                        text = { 
                            Text(
                                when (uiState.clipboardAction) {
                                    ClipboardAction.COPY -> "复制到此处"
                                    ClipboardAction.CUT -> "移动到此处"
                                    ClipboardAction.NONE -> ""
                                }
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                
                // 新建文件夹按钮
                if (!uiState.isSelectionMode) {
                    FloatingActionButton(
                        onClick = { showCreateFolderDialog = true }
                    ) {
                        Icon(Icons.Default.CreateNewFolder, "新建文件夹")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "发生错误",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadFiles(uiState.currentPath) }) {
                            Text("重试")
                        }
                    }
                }
                uiState.files.isEmpty() -> {
                    Text(
                        text = "没有文件",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = uiState.files,
                            key = { it.path }
                        ) { file ->
                            FileListItem(
                                file = file,
                                isSelected = uiState.selectedFiles.contains(file.path),
                                isSelectionMode = uiState.isSelectionMode,
                                isFavorite = uiState.favoritePaths.contains(file.path),
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleFileSelection(file.path)
                                    } else if (file.isDirectory) {
                                        viewModel.navigateToFolder(file.path)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelectionMode()
                                    viewModel.toggleFileSelection(file.path)
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(file.path) },
                                onCopyClick = { viewModel.copyToClipboard(setOf(file.path)) },
                                onCutClick = { viewModel.cutToClipboard(setOf(file.path)) },
                                onDeleteClick = { viewModel.deleteFiles(setOf(file.path)) },
                                onRenameClick = { showRenameDialog = true }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            
            // 批量操作栏
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedFiles.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, "全选")
                            }
                            Text("全选", style = MaterialTheme.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { 
                                viewModel.copyToClipboard(uiState.selectedFiles)
                            }) {
                                Icon(Icons.Default.ContentCopy, "复制")
                            }
                            Text("复制", style = MaterialTheme.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { 
                                viewModel.cutToClipboard(uiState.selectedFiles)
                            }) {
                                Icon(Icons.Default.ContentCut, "剪切")
                            }
                            Text("剪切", style = MaterialTheme.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { 
                                viewModel.deleteFiles(uiState.selectedFiles)
                            }) {
                                Icon(Icons.Default.Delete, "删除")
                            }
                            Text("删除", style = MaterialTheme.bodySmall)
                        }
                    }
                }
            }
        }
    }
    
    // 重命名对话框
    if (showRenameDialog && uiState.selectedFiles.size == 1) {
        RenameDialog(
            currentName = uiState.files.find { it.path == uiState.selectedFiles.first() }?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                uiState.selectedFiles.firstOrNull()?.let { path ->
                    viewModel.rename(path, newName)
                }
                showRenameDialog = false
            }
        )
    }
    
    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }
    
    // 操作结果对话框
    if (showResultDialog) {
        OperationResultDialog(
            message = resultMessage,
            isError = resultIsError,
            onDismiss = { showResultDialog = false }
        )
    }
}
