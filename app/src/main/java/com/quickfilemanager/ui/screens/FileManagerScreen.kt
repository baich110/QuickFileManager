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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.domain.model.OperationResult
import com.quickfilemanager.domain.model.StorageInfo
import com.quickfilemanager.ui.components.CreateFolderDialog
import com.quickfilemanager.ui.components.RenameDialog
import com.quickfilemanager.viewmodel.ClipboardAction
import com.quickfilemanager.viewmodel.FileCategory
import com.quickfilemanager.viewmodel.FileViewModel
import com.quickfilemanager.viewmodel.SortType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf("") }
    var renameNewName by remember { mutableStateOf("") }
    var showStoragePicker by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) { viewModel.operationResult.collect { when (it) { is OperationResult.Success -> snackbarHostState.showSnackbar(it.message); is OperationResult.Error -> snackbarHostState.showSnackbar(it.message); else -> {} } } }
    
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { if (showSearchBar) OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; viewModel.searchFiles(it) }, placeholder = { Text("搜索...") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { showSearchBar = false; searchQuery = ""; viewModel.loadFiles(uiState.currentPath) }) { Icon(Icons.Default.Close, null) } }) else Text(if (uiState.isSelectionMode) "已选择 ${uiState.selectedFiles.size} 项" else uiState.currentCategory.displayName) }, navigationIcon = { if (uiState.isSelectionMode) IconButton(onClick = { viewModel.exitSelectionMode() }) { Icon(Icons.Default.Close, null) } else IconButton(onClick = { viewModel.navigateUp() }) { Icon(Icons.Default.ArrowBack, null) } }, actions = { if (!showSearchBar && !uiState.isSelectionMode) { IconButton(onClick = { showSearchBar = true }) { Icon(Icons.Default.Search, null) }; IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }; IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, null) }; DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) { DropdownMenuItem(text = { Text("刷新") }, onClick = { viewModel.loadFiles(uiState.currentPath); showMoreMenu = false }, leadingIcon = { Icon(Icons.Default.Refresh, null) }); DropdownMenuItem(text = { Text("存储设备") }, onClick = { showStoragePicker = true; showMoreMenu = false }, leadingIcon = { Icon(Icons.Default.Storage, null) }) }; DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) { SortType.entries.forEach { s -> DropdownMenuItem(text = { Text(s.displayName) }, onClick = { viewModel.setSortType(s); showSortMenu = false }, leadingIcon = { if (uiState.sortType == s) Icon(Icons.Default.Check, null) else null }) } }; if (uiState.isSelectionMode) { IconButton(onClick = { viewModel.selectAll() }) { Icon(Icons.Default.SelectAll, null) }; IconButton(onClick = { viewModel.copyToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.ContentCopy, null) }; IconButton(onClick = { viewModel.cutToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.ContentCut, null) }; IconButton(onClick = { viewModel.deleteFiles(uiState.selectedFiles) }) { Icon(Icons.Default.Delete, null) } } })
    }, bottomBar = { NavigationBar { FileCategory.entries.filter { it != FileCategory.ROOT }.forEach { c -> NavigationBarItem(icon = { Icon(when (c) { FileCategory.ALL -> Icons.Default.Folder; FileCategory.INTERNAL -> Icons.Default.Storage; FileCategory.DOWNLOADS -> Icons.Default.Download; FileCategory.IMAGES -> Icons.Default.Image; FileCategory.VIDEOS -> Icons.Default.VideoFile; FileCategory.AUDIO -> Icons.Default.AudioFile; FileCategory.DOCUMENTS -> Icons.Default.Description; FileCategory.APKS -> Icons.Default.Android; FileCategory.ARCHIVES -> Icons.Default.Archive; else -> Icons.Default.Folder }, null) }, label = { Text(c.displayName, maxLines = 1) }, selected = uiState.currentCategory == c, onClick = { viewModel.selectCategory(c) }) } } }, floatingActionButton = { if (!uiState.isSelectionMode && !showSearchBar) { Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) { AnimatedVisibility(visible = uiState.clipboardAction != ClipboardAction.NONE, enter = slideInVertically { it }, exit = slideOutVertically { it }) { ExtendedFloatingActionButton(onClick = { viewModel.pasteFiles(uiState.currentPath) }, icon = { Icon(Icons.Default.ContentPaste, null) }, text = { Text(when (uiState.clipboardAction) { ClipboardAction.COPY -> "复制到此处"; ClipboardAction.CUT -> "移动到此处"; else -> "" }) }, containerColor = MaterialTheme.colorScheme.secondaryContainer) }; FloatingActionButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, null) } } } }) { paddingValues -> Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) { when { uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)); uiState.files.isEmpty() -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); Spacer(Modifier.height(16.dp)); Text("没有文件") }; else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 100.dp)) { items(items = uiState.files, key = { it.path }) { file -> FileItemRow(file = file, isSelected = uiState.selectedFiles.contains(file.path), isSelectionMode = uiState.isSelectionMode, isFavorite = uiState.favoritePaths.contains(file.path), onClick = { if (uiState.isSelectionMode) viewModel.toggleFileSelection(file.path) else if (file.isDirectory) viewModel.navigateToFolder(file.path) }, onLongClick = { viewModel.toggleSelectionMode(); viewModel.toggleFileSelection(file.path) }, onCopy = { viewModel.copyToClipboard(setOf(file.path)) }, onCut = { viewModel.cutToClipboard(setOf(file.path)) }, onDelete = { viewModel.deleteFiles(setOf(file.path)) }, onRename = { renameTarget = file.path; renameNewName = file.name; showRenameDialog = true }, onFavorite = { viewModel.toggleFavorite(file.path) }, modifier = Modifier.padding(vertical = 2.dp)) } } }; AnimatedVisibility(visible = uiState.isSelectionMode && uiState.selectedFiles.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) { Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { viewModel.selectAll() }) { Icon(Icons.Default.SelectAll, null) }; Text("全选", style = MaterialTheme.typography.bodySmall) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { viewModel.copyToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.ContentCopy, null) }; Text("复制", style = MaterialTheme.typography.bodySmall) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { viewModel.cutToClipboard(uiState.selectedFiles) }) { Icon(Icons.Default.ContentCut, null) }; Text("剪切", style = MaterialTheme.typography.bodySmall) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { viewModel.deleteFiles(uiState.selectedFiles) }) { Icon(Icons.Default.Delete, null) }; Text("删除", style = MaterialTheme.typography.bodySmall) } } } } } } }
    if (showRenameDialog) RenameDialog(currentName = renameTarget.substringAfterLast("/"), newName = renameNewName, onDismiss = { showRenameDialog = false }, onNameChange = { renameNewName = it }, onConfirm = { viewModel.rename(renameTarget, renameNewName); showRenameDialog = false })
    if (showCreateFolderDialog) CreateFolderDialog(onDismiss = { showCreateFolderDialog = false }, onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false })
    if (showStoragePicker) StoragePickerDialog(storageList = uiState.storageList, onStorageSelected = { path -> viewModel.navigateToFolder(path); showStoragePicker = false }, onDismiss = { showStoragePicker = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(file: FileItem, isSelected: Boolean, isSelectionMode: Boolean, isFavorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val iconColor = when { file.isDirectory -> Color(0xFFFFA726); file.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Color(0xFF42A5F5); file.extension in listOf("mp4", "avi", "mkv", "mov") -> Color(0xFFAB47BC); file.extension in listOf("mp3", "wav", "ogg", "flac") -> Color(0xFF66BB6A); else -> Color(0xFF78909C) }
    Card(modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file.extension), contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) { Text(text = file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (!file.isDirectory) Text(text = formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(text = dateFormat.format(Date(file.lastModified)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (isFavorite) Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFD700)) } }
            Box { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("复制") }, onClick = { onCopy(); showMenu = false }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                    DropdownMenuItem(text = { Text("剪切") }, onClick = { onCut(); showMenu = false }, leadingIcon = { Icon(Icons.Default.ContentCut, null) })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { onRename(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text(if (isFavorite) "取消收藏" else "收藏") }, onClick = { onFavorite(); showMenu = false }, leadingIcon = { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline, null) })
                    Divider()
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@Composable
fun StoragePickerDialog(storageList: List<StorageInfo>, onStorageSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择存储设备") }, text = { Column { storageList.forEach { storage -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (storage.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(12.dp)); Column { Text(storage.label); Text("${formatBytes(storage.freeSpace)} 可用", style = MaterialTheme.typography.bodySmall) } } } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

fun getFileIcon(extension: String): ImageVector = when { extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Default.Image; extension in listOf("mp4", "avi", "mkv", "mov", "wmv") -> Icons.Default.VideoFile; extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") -> Icons.Default.AudioFile; extension in listOf("pdf") -> Icons.Default.PictureAsPdf; extension in listOf("doc", "docx", "txt", "rtf") -> Icons.Default.Description; extension in listOf("xls", "xlsx", "csv") -> Icons.Default.TableChart; extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Archive; extension in listOf("apk") -> Icons.Default.Android; else -> Icons.Default.InsertDriveFile }

fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"; else -> "${bytes / (1024 * 1024 * 1024)} GB" }
