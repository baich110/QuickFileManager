package com.quickfilemanager.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickfilemanager.domain.model.OperationResult
import com.quickfilemanager.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf("") }
    var renameNewName by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 权限引导相关
    var showPermissionGuide by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(false) }
    var permissionStep by remember { mutableIntStateOf(0) } // 0: 初始, 1: 媒体权限, 2: 文件权限

    // Android 13+ 媒体权限
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            permissionStep = 2
        } else {
            permissionStep = 1
        }
    }

    // 传统存储权限 (Android 10-12)
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            hasStoragePermission = true
            showPermissionGuide = false
            viewModel.loadFiles(uiState.currentPath)
        }
    }

    // MANAGE_EXTERNAL_STORAGE 权限 (Android 11+)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasStoragePermission) {
            showPermissionGuide = false
            viewModel.loadFiles(uiState.currentPath)
        }
    }

    // 检查权限
    LaunchedEffect(Unit) {
        hasStoragePermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            else -> true
        }
        
        if (!hasStoragePermission) {
            permissionStep = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 1 else 2
            showPermissionGuide = true
        } else {
            viewModel.loadFiles(uiState.currentPath)
        }
    }
    
    // 权限引导对话框
    if (showPermissionGuide) {
        PermissionGuideDialog(
            currentStep = permissionStep,
            onStepChange = { permissionStep = it },
            onRequestMediaPermission = {
                mediaPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                )
            },
            onRequestLegacyPermission = {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            },
            onRequestManagePermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                }
            },
            onDismiss = { showPermissionGuide = false }
        )
    }

    // 监听操作结果
    LaunchedEffect(Unit) {
        viewModel.operationResult.collect { result ->
            when (result) {
                is OperationResult.Success -> {
                    snackbarHostState.showSnackbar(result.message)
                }
                is OperationResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
                is OperationResult.Progress -> { /* 处理进度 */ }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchFiles(it)
                            },
                            placeholder = { Text("搜索文件...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (uiState.isSelectionMode) 
                                "已选择 ${uiState.selectedFiles.size} 项"
                            else 
                                "文件管理器"
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "取消选择")
                        }
                    } else {
                        IconButton(
                            onClick = { 
                                if (!viewModel.navigateUp()) {
                                    // 已在根目录
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, "返回上级")
                        }
                    }
                },
                actions = {
                    if (showSearchBar) {
                        IconButton(onClick = {
                            showSearchBar = false
                            searchQuery = ""
                            viewModel.loadFiles(uiState.currentPath)
                        }) {
                            Icon(Icons.Default.Close, "关闭搜索")
                        }
                    } else if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "全选")
                        }
                        IconButton(onClick = { 
                            viewModel.deleteFiles(uiState.selectedFiles)
                        }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    } else {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(Icons.Default.Checklist, "多选")
                        }
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "新建文件夹")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.files.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "此文件夹为空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.files,
                        key = { it.path }
                    ) { file ->
                        FileListItem(
                            file = file,
                            isSelected = uiState.selectedFiles.contains(file.path),
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleFileSelection(file.path)
                                } else if (file.isDirectory) {
                                    viewModel.navigateToFolder(file.path)
                                }
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                }
                                viewModel.toggleFileSelection(file.path)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 存储信息显示
            if (!uiState.isSelectionMode && uiState.storageList.isNotEmpty()) {
                StorageIndicator(
                    storageInfo = uiState.storageList.first(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            // 当前路径显示
            if (!uiState.isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = uiState.currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
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

    // 重命名对话框
    if (showRenameDialog) {
        RenameDialog(
            currentName = renameTarget,
            newName = renameNewName,
            onDismiss = { showRenameDialog = false },
            onNameChange = { renameNewName = it },
            onConfirm = {
                viewModel.rename(renameTarget, renameNewName)
                showRenameDialog = false
            }
        )
    }
}

@Composable
fun StorageIndicator(
    storageInfo: com.quickfilemanager.domain.model.StorageInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = storageInfo.label,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${formatBytes(storageInfo.freeSpace)} 可用",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = storageInfo.usedPercentage / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("文件夹名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    newName: String,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            Column {
                Text(
                    text = "当前名称: $currentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = onNameChange,
                    label = { Text("新名称") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = newName.isNotBlank() && newName != currentName
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * 权限引导对话框 - 多步骤
 */
@Composable
fun PermissionGuideDialog(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onRequestMediaPermission: () -> Unit,
    onRequestLegacyPermission: () -> Unit,
    onRequestManagePermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when (currentStep) {
                        1 -> Icons.Default.PhotoLibrary
                        2 -> Icons.Default.Folder
                        else -> Icons.Default.Security
                    },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (currentStep) {
                        1 -> "需要访问媒体文件"
                        2 -> "需要文件访问权限"
                        else -> "需要存储权限"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = when (currentStep) {
                        1 -> "查看和管理你的照片、视频、音乐"
                        2 -> "这个文件管理器需要全面访问权限才能正常工作"
                        else -> "需要访问设备的存储空间"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 权限说明卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    when (currentStep) {
                        1 -> {
                            PermissionFeature(
                                icon = Icons.Default.Image,
                                title = "照片和视频",
                                desc = "浏览、复制、移动、删除相册中的媒体文件"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PermissionFeature(
                                icon = Icons.Default.MusicNote,
                                title = "音乐文件",
                                desc = "管理手机里的音乐和音频"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PermissionFeature(
                                icon = Icons.Default.Download,
                                title = "下载内容",
                                desc = "访问下载文件夹中的文件"
                            )
                        }
                        2 -> {
                            PermissionFeature(
                                icon = Icons.Default.Folder,
                                title = "所有文件",
                                desc = "访问和管理手机存储中的所有文件"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PermissionFeature(
                                icon = Icons.Default.Edit,
                                title = "读写权限",
                                desc = "创建、修改、删除文件和文件夹"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PermissionFeature(
                                icon = Icons.Default.Warning,
                                title = "注意",
                                desc = "删除系统文件可能影响应用运行，请谨慎操作"
                            )
                        }
                        else -> {
                            PermissionFeature(
                                icon = Icons.Default.Storage,
                                title = "存储访问",
                                desc = "访问设备的内部和外部存储"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentStep == 1) {
                                onDismiss()
                            } else {
                                onStepChange(1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (currentStep == 1) "稍后再说" else "上一步")
                    }
                    
                    Button(
                        onClick = {
                            when (currentStep) {
                                1 -> onRequestMediaPermission()
                                2 -> onRequestManagePermission()
                                else -> onRequestLegacyPermission()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("授权")
                    }
                }
                
                // 跳过按钮（仅步骤1显示）
                if (currentStep == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onStepChange(2) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("跳过，授权所有文件权限")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 导入 FileListItem
@Suppress("ComposableNaming")
@Composable
private fun FileListItem(
    file: com.quickfilemanager.domain.model.FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.quickfilemanager.ui.components.FileListItem(
        file = file,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    )
}
