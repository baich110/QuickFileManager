package com.quickfilemanager.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.domain.model.OperationResult
import com.quickfilemanager.domain.model.StorageInfo
import com.quickfilemanager.viewmodel.ClipboardAction
import com.quickfilemanager.viewmodel.FileViewModel
import com.quickfilemanager.viewmodel.SortType
import com.quickfilemanager.viewmodel.ViewMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    var showStoragePickerDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf("") }
    var renameNewName by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var infoFileItem by remember { mutableStateOf<FileItem?>(null) }
    var showPermissionGuide by remember { mutableStateOf(false) }
    var permissionStep by remember { mutableIntStateOf(0) }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) permissionStep = 2 else permissionStep = 1
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) { viewModel.loadFiles(uiState.currentPath) }
    }
    val manageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            viewModel.loadFiles(uiState.currentPath)
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        if (!hasPermission) {
            permissionStep = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 1 else 2
            showPermissionGuide = true
        } else viewModel.loadFiles(uiState.currentPath)
    }
    LaunchedEffect(Unit) {
        viewModel.operationResult.collect { result ->
            when (result) {
                is OperationResult.Success -> snackbarHostState.showSnackbar(result.message)
                is OperationResult.Error -> snackbarHostState.showSnackbar(result.message)
                is OperationResult.Progress -> { }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        Column {
            if (uiState.tabs.size > 1) {
                ScrollableTabRow(selectedTabIndex = uiState.currentTabIndex, edgePadding = 8.dp) {
                    uiState.tabs.forEachIndexed { index, tab ->
                        Tab(selected = index == uiState.currentTabIndex, onClick = { viewModel.switchToTab(index) },
                            text = { Text(tab.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { if (uiState.tabs.size > 1) IconButton(onClick = { viewModel.closeTab(index) }, modifier = Modifier.size(18.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) } })
                    }
                    Tab(selected = false, onClick = { viewModel.addNewTab() }, icon = { Icon(Icons.Default.Add, null) })
                }
            }
            TopAppBar(title = {
                if (showSearchBar) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; viewModel.searchFiles(it) },
                        placeholder = { Text("搜索...") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { IconButton(onClick = { showSearchBar = false; searchQuery = ""; viewModel.loadFiles(uiState.currentPath) }) { Icon(Icons.Default.Close, null) } })
                } else {
                    Text(text = if (uiState.isSelectionMode) "已选择 ${uiState.selectedFiles.size} 项" else if (uiState.tabs.size == 1) uiState.tabs.first().name.ifEmpty { "文件管理器" } else "")
                }
            }, navigationIcon = {
                if (uiState.isSelectionMode) IconButton(onClick = { viewModel.exitSelectionMode() }) { Icon(Icons.Default.Close, "取消") }
                else Row {
                    IconButton(onClick = { viewModel.navigateUp() }) { Icon(Icons.Default.ArrowBack, "返回") }
                    if (uiState.tabs.size == 1) IconButton(onClick = { viewModel.addNewTab() }) { Icon(Icons.Default.Tab, "标签") }
                }
            }, actions = {
                if (!showSearchBar && !uiState.isSelectionMode) {
                    IconButton(onClick = { showSearchBar = true }) { Icon(Icons.Default.Search, "搜索") }
                    IconButton(onClick = { showSortDialog = true }) { Icon(Icons.Default.Sort, "排序") }
                    IconButton(onClick = { viewModel.setViewMode(if (uiState.currentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) }) { Icon(if (uiState.currentViewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, "视图") }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(if (uiState.showHiddenFiles) "隐藏文件" else "显示文件") }, onClick = { viewModel.toggleHiddenFiles(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                        DropdownMenuItem(text = { Text("存储设备") }, onClick = { showStoragePickerDialog = true; showMenu = false }, leadingIcon = { Icon(Icons.Default.Storage, null) })
                    }
                }
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.selectAll() }) { Icon(Icons.Default.SelectAll, "全选") }
                    IconButton(onClick = { viewModel.copyToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.ContentCopy, "复制") }
                    IconButton(onClick = { viewModel.cutToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.Cut, "剪切") }
                    IconButton(onClick = { viewModel.deleteFiles(uiState.selectedFiles) }) { Icon(Icons.Default.Delete, "删除") }
                }
            })
        }
    }, floatingActionButton = {
        if (!uiState.isSelectionMode && !showSearchBar) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(visible = uiState.clipboardAction != ClipboardAction.NONE, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    ExtendedFloatingActionButton(onClick = { viewModel.pasteFromClipboard() },
                        icon = { Icon(Icons.Default.ContentPaste, null) },
                        text = { Text(when (uiState.clipboardAction) { ClipboardAction.COPY -> "粘贴 ${uiState.clipboardPaths.size} 项"; ClipboardAction.CUT -> "移动 ${uiState.clipboardPaths.size} 项"; else -> "粘贴" }) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer)
                }
                FloatingActionButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, "新建") }
            }
        }
    }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else if (uiState.files.isEmpty()) Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp)); Text("此文件夹为空", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 100.dp)) {
                    items(items = uiState.files, key = { it.path }) { file -> FileItemRow(file = file, isSelected = uiState.selectedFiles.contains(file.path), isSelectionMode = uiState.isSelectionMode,
                        isFavorite = viewModel.isFavorite(file.path), onClick = { if (uiState.isSelectionMode) viewModel.toggleFileSelection(file.path) else if (file.isDirectory) viewModel.navigateToFolder(file.path) },
                        onLongClick = { if (!uiState.isSelectionMode) viewModel.toggleSelectionMode(); viewModel.toggleFileSelection(file.path) },
                        onCopy = { viewModel.copyToClipboard(setOf(file.path)) }, onCut = { viewModel.cutToClipboard(setOf(file.path)) }, onDelete = { viewModel.deleteFiles(setOf(file.path)) },
                        onRename = { renameTarget = file.path; renameNewName = file.name; showRenameDialog = true }, onInfo = { infoFileItem = file; showFileInfoDialog = true }, onFavorite = { viewModel.toggleFavorite(file.path) },
                        modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
            if (!uiState.isSelectionMode && uiState.storageList.isNotEmpty() && !showSearchBar) StorageIndicator(storageInfo = uiState.storageList.first(), modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            if (!uiState.isSelectionMode && !showSearchBar) Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(text = uiState.currentPath, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            }
            if (uiState.isOperationInProgress && uiState.operationProgress != null) Card(modifier = Modifier.align(Alignment.Center).padding(32.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("正在复制: ${uiState.operationProgress!!.fileName}"); Text("${uiState.operationProgress!!.current}/${uiState.operationProgress!!.total}", style = MaterialTheme.typography.bodySmall) } }
        }
    }
    if (showPermissionGuide) PermissionGuideDialog(currentStep = permissionStep, onStepChange = { permissionStep = it }, onRequestMediaPermission = { mediaLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)) }, onRequestLegacyPermission = { legacyLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)) }, onRequestManagePermission = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${context.packageName}") }; manageLauncher.launch(intent) } }, onDismiss = { showPermissionGuide = false })
    if (showCreateFolderDialog) CreateFolderDialog(onDismiss = { showCreateFolderDialog = false }, onConfirm = { viewModel.createFolder(it); showCreateFolderDialog = false })
    if (showRenameDialog) RenameDialog(currentName = renameTarget.substringAfterLast("/"), newName = renameNewName, onDismiss = { showRenameDialog = false }, onNameChange = { renameNewName = it }, onConfirm = { viewModel.rename(renameTarget, it); showRenameDialog = false })
    if (showSortDialog) SortDialog(currentSort = uiState.sortType, onSortSelected = { viewModel.setSortType(it); showSortDialog = false }, onDismiss = { showSortDialog = false })
    if (showFileInfoDialog && infoFileItem != null) FileInfoDialog(file = infoFileItem!!, isFavorite = viewModel.isFavorite(infoFileItem!!.path), onFavoriteToggle = { viewModel.toggleFavorite(infoFileItem!!.path) }, onDismiss = { showFileInfoDialog = false; infoFileItem = null })
    if (showStoragePickerDialog) StoragePickerDialog(storageList = uiState.storageList, onStorageSelected = { viewModel.navigateToFolder(it); showStoragePickerDialog = false }, onDismiss = { showStoragePickerDialog = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(file: FileItem, isSelected: Boolean, isSelectionMode: Boolean, isFavorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onInfo: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val iconColor = when { file.isDirectory -> Color(0xFFFFA726); file.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Color(0xFF42A5F5); file.extension in listOf("mp4", "avi", "mkv", "mov") -> Color(0xFFAB47BC); file.extension in listOf("mp3", "wav", "ogg", "flac") -> Color(0xFF66BB6A); else -> Color(0xFF78909C) }
    Card(modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file.extension), contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) { Text(text = file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (!file.isDirectory) Text(text = formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(text = dateFormat.format(Date(file.lastModified)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (isFavorite) Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFD700)) } }
            Box { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("打开") }, onClick = { onClick(); showMenu = false }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                    if (!isSelectionMode) { DropdownMenuItem(text = { Text("复制") }, onClick = { onCopy(); showMenu = false }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }); DropdownMenuItem(text = { Text("剪切") }, onClick = { onCut(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Cut, null) }); DropdownMenuItem(text = { Text("重命名") }, onClick = { onRename(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) }) }
                    DropdownMenuItem(text = { Text(if (isFavorite) "取消收藏" else "收藏") }, onClick = { onFavorite(); showMenu = false }, leadingIcon = { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline, null) })
                    DropdownMenuItem(text = { Text("详情") }, onClick = { onInfo(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Info, null) })
                    if (!isSelectionMode) { Divider(); DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }) }
                }
            }
        }
    }
}

fun getFileIcon(extension: String): ImageVector = when { extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Default.Image; extension in listOf("mp4", "avi", "mkv", "mov", "wmv") -> Icons.Default.VideoFile; extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") -> Icons.Default.AudioFile; extension in listOf("pdf") -> Icons.Default.PictureAsPdf; extension in listOf("doc", "docx", "txt", "rtf") -> Icons.Default.Description; extension in listOf("xls", "xlsx", "csv") -> Icons.Default.TableChart; extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Archive; extension in listOf("apk") -> Icons.Default.Android; extension in listOf("html", "css", "js", "xml", "json") -> Icons.Default.Code; else -> Icons.Default.InsertDriveFile }

@Composable
fun StorageIndicator(storageInfo: StorageInfo, modifier: Modifier = Modifier) { Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))) { Column(modifier = Modifier.padding(12.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(text = storageInfo.label, style = MaterialTheme.typography.labelMedium); Text(text = "${formatBytes(storageInfo.freeSpace)} 可用", style = MaterialTheme.typography.labelMedium) }; Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { storageInfo.usedPercentage / 100f }, modifier = Modifier.fillMaxWidth()) } } }

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var folderName by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("新建文件夹") }, text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("文件夹名称") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onConfirm(folderName) }, enabled = folderName.isNotBlank()) { Text("创建") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable
fun RenameDialog(currentName: String, newName: String, onDismiss: () -> Unit, onNameChange: (String) -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("重命名") }, text = { Column { Text("当前: $currentName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = newName, onValueChange = onNameChange, label = { Text("新名称") }, singleLine = true) } }, confirmButton = { TextButton(onClick = onConfirm, enabled = newName.isNotBlank() && newName != currentName) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable
fun SortDialog(currentSort: SortType, onSortSelected: (SortType) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("排序方式") }, text = { Column { SortType.entries.forEach { sort -> Row(modifier = Modifier.fillMaxWidth().clickable { onSortSelected(sort) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentSort == sort, onClick = { onSortSelected(sort) }); Spacer(Modifier.width(8.dp)); Text(when (sort) { SortType.NAME_ASC -> "名称 (A-Z)"; SortType.NAME_DESC -> "名称 (Z-A)"; SortType.SIZE_ASC -> "大小 (从小到大)"; SortType.SIZE_DESC -> "大小 (从大到小)"; SortType.DATE_ASC -> "日期 (旧到新)"; SortType.DATE_DESC -> "日期 (新到旧)" }) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable
fun FileInfoDialog(file: FileItem, isFavorite: Boolean, onFavoriteToggle: () -> Unit, onDismiss: () -> Unit) { val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }; AlertDialog(onDismissRequest = onDismiss, title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(file.name, modifier = Modifier.weight(1f)); IconButton(onClick = onFavoriteToggle) { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline, "收藏", tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface) } } }, text = { Column { InfoRow("类型", if (file.isDirectory) "文件夹" else "文件"); InfoRow("大小", if (file.isDirectory) "-" else formatBytes(file.size)); InfoRow("修改时间", dateFormat.format(Date(file.lastModified))); InfoRow("路径", file.path) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }) }

@Composable
fun InfoRow(label: String, value: String) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("$label:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) } }

@Composable
fun StoragePickerDialog(storageList: List<StorageInfo>, onStorageSelected: (String) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("选择存储设备") }, text = { Column { storageList.forEach { storage -> Card(modifier = Modifier.fillMaxWidth().clickable { onStorageSelected(storage.path) }.padding(vertical = 4.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (storage.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(12.dp)); Column { Text(storage.label); Text("${formatBytes(storage.freeSpace)} 可用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable
fun PermissionGuideDialog(currentStep: Int, onStepChange: (Int) -> Unit, onRequestMediaPermission: () -> Unit, onRequestLegacyPermission: () -> Unit, onRequestManagePermission: () -> Unit, onDismiss: () -> Unit) { Dialog(onDismissRequest = onDismiss) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = when (currentStep) { 1 -> Icons.Default.PhotoLibrary; 2 -> Icons.Default.Folder; else -> Icons.Default.Security }, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(text = when (currentStep) { 1 -> "需要访问媒体文件"; 2 -> "需要文件访问权限"; else -> "需要存储权限" }, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); Text(text = when (currentStep) { 1 -> "查看和管理你的照片、视频、音乐"; 2 -> "需要全面访问权限才能正常工作"; else -> "需要访问设备的存储空间" }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = { if (currentStep == 1) onDismiss() else onStepChange(1) }, modifier = Modifier.weight(1f)) { Text(if (currentStep == 1) "稍后" else "上一步") }; Button(onClick = { when (currentStep) { 1 -> onRequestMediaPermission(); 2 -> onRequestManagePermission(); else -> onRequestLegacyPermission() } }, modifier = Modifier.weight(1f)) { Text("授权") } }; if (currentStep == 1) { Spacer(Modifier.height(8.dp)); TextButton(onClick = { onStepChange(2) }, modifier = Modifier.fillMaxWidth()) { Text("跳过，授权所有文件权限") } } } } } }

private fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"; else -> "${bytes / (1024 * 1024 * 1024)} GB" }
