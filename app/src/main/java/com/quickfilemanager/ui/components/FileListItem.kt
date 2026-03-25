package com.quickfilemanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(file.lastModified))
    
    // 无障碍描述
    val semanticsDesc = if (isSelectionMode) {
        if (isSelected) "${file.accessibleName}, 已选中, 点击取消选择"
        else "${file.accessibleName}, 未选中, 点击选择"
    } else {
        file.accessibleName
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsDesc }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else 
            Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择框（选择模式时显示）
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null, // 点击由父组件处理
                    modifier = Modifier.semantics { 
                        role = Role.Checkbox 
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 文件类型图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getFileTypeColor(file).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileTypeIcon(file),
                    contentDescription = null, // 图标装饰性
                    tint = getFileTypeColor(file),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!file.isDirectory) {
                        Text(
                            text = file.formatSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 右箭头（文件夹显示）
            if (file.isDirectory && !isSelectionMode) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 根据文件类型获取图标
 */
@Composable
fun getFileTypeIcon(file: FileItem): ImageVector {
    return when {
        file.isDirectory -> Icons.Default.Folder
        file.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Default.Image
        file.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv") -> Icons.Default.VideoFile
        file.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") -> Icons.Default.AudioFile
        file.extension in listOf("pdf") -> Icons.Default.PictureAsPdf
        file.extension in listOf("doc", "docx", "txt", "rtf", "odt") -> Icons.Default.Description
        file.extension in listOf("xls", "xlsx", "csv") -> Icons.Default.TableChart
        file.extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Archive
        file.extension in listOf("apk") -> Icons.Default.Android
        file.extension in listOf("html", "css", "js", "xml", "json") -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 根据文件类型获取颜色
 */
@Composable
fun getFileTypeColor(file: FileItem): Color {
    return when {
        file.isDirectory -> FolderColor
        file.extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> ImageColor
        file.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv") -> VideoColor
        file.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") -> AudioColor
        file.extension in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx") -> DocumentColor
        file.extension in listOf("zip", "rar", "7z", "tar", "gz") -> ArchiveColor
        else -> UnknownColor
    }
}
