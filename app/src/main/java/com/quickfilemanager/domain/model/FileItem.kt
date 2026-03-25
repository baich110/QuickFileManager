package com.quickfilemanager.domain.model

import java.io.File

/**
 * 文件/文件夹数据模型
 * 包含无障碍所需的语义信息
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = "",
    val mimeType: String = ""
) {
    companion object {
        fun fromFile(file: File): FileItem {
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                extension = if (file.isDirectory) "" else file.extension.lowercase(),
                mimeType = getMimeType(file)
            )
        }

        private fun getMimeType(file: File): String {
            val name = file.name.lowercase()
            return when {
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                name.endsWith(".png") -> "image/png"
                name.endsWith(".gif") -> "image/gif"
                name.endsWith(".mp3") -> "audio/mpeg"
                name.endsWith(".mp4") -> "video/mp4"
                name.endsWith(".pdf") -> "application/pdf"
                name.endsWith(".txt") -> "text/plain"
                else -> "application/octet-stream"
            }
        }
    }

    /**
     * 无障碍友好的显示名称
     */
    val accessibleName: String
        get() = if (isDirectory) {
            "文件夹: $name"
        } else {
            "文件: $name, 大小: ${formatSize(size)}"
        }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes 字节"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * 存储位置信息
 */
data class StorageInfo(
    val path: String,
    val label: String,
    val totalSpace: Long,
    val freeSpace: Long,
    val isRemovable: Boolean = false
) {
    val usedSpace: Long get() = totalSpace - freeSpace
    val usedPercentage: Float get() = (usedSpace.toFloat() / totalSpace) * 100
}
