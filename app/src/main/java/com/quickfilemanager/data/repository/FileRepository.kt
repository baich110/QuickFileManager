package com.quickfilemanager.data.repository

import android.os.Environment
import android.os.StatFs
import com.quickfilemanager.domain.model.FileItem
import com.quickfilemanager.domain.model.OperationResult
import com.quickfilemanager.domain.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor() {

    /**
     * 获取指定目录下的文件和文件夹列表
     */
    suspend fun listFiles(path: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(path)
            if (!directory.exists() || !directory.isDirectory) {
                return@withContext Result.failure(Exception("目录不存在或不可访问"))
            }
            if (!directory.canRead()) {
                return@withContext Result.failure(Exception("没有读取权限"))
            }

            val files = directory.listFiles()?.map { FileItem.fromFile(it) }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()

            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取存储设备列表
     */
    suspend fun getStorageList(): List<StorageInfo> = withContext(Dispatchers.IO) {
        val storageList = mutableListOf<StorageInfo>()

        // 内部存储
        val internalPath = Environment.getExternalStorageDirectory().absolutePath
        storageList.add(getStorageInfo(internalPath, "内部存储"))

        // 外部存储（如果有）
        val externalDirs = Environment.getExternalStorageDirectories()
        externalDirs?.forEachIndexed { index, path ->
            if (path != internalPath) {
                storageList.add(getStorageInfo(path, "SD卡 ${index + 1}", isRemovable = true))
            }
        }

        storageList
    }

    private fun getStorageInfo(path: String, label: String, isRemovable: Boolean = false): StorageInfo {
        val stat = StatFs(path)
        return StorageInfo(
            path = path,
            label = label,
            totalSpace = stat.blockSizeLong * stat.blockCountLong,
            freeSpace = stat.blockSizeLong * stat.availableBlocksLong,
            isRemovable = isRemovable
        )
    }

    /**
     * 创建文件夹
     */
    suspend fun createFolder(parentPath: String, name: String): OperationResult = withContext(Dispatchers.IO) {
        try {
            val newFolder = File(parentPath, name)
            if (newFolder.exists()) {
                return@withContext OperationResult.Error("文件夹已存在")
            }
            if (newFolder.mkdirs()) {
                OperationResult.Success("已创建文件夹: $name")
            } else {
                OperationResult.Error("创建失败")
            }
        } catch (e: Exception) {
            OperationResult.Error("创建失败: ${e.message}", e)
        }
    }

    /**
     * 删除文件或文件夹
     */
    suspend fun delete(path: String): Flow<OperationResult> = flow {
        try {
            val file = File(path)
            if (!file.exists()) {
                emit(OperationResult.Error("文件不存在"))
                return@flow
            }

            if (file.isDirectory) {
                // 删除文件夹内容
                file.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }

            if (file.delete()) {
                emit(OperationResult.Success("已删除: ${file.name}"))
            } else {
                emit(OperationResult.Error("删除失败"))
            }
        } catch (e: Exception) {
            emit(OperationResult.Error("删除失败: ${e.message}", e))
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    /**
     * 重命名
     */
    suspend fun rename(path: String, newName: String): OperationResult = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext OperationResult.Error("文件不存在")
            }
            val newFile = File(file.parentFile, newName)
            if (newFile.exists()) {
                return@withContext OperationResult.Error("目标名称已存在")
            }
            if (file.renameTo(newFile)) {
                OperationResult.Success("已重命名为: $newName")
            } else {
                OperationResult.Error("重命名失败")
            }
        } catch (e: Exception) {
            OperationResult.Error("重命名失败: ${e.message}", e)
        }
    }

    /**
     * 复制文件
     */
    suspend fun copy(sourcePath: String, targetPath: String): Flow<OperationResult> = flow {
        try {
            val source = File(sourcePath)
            if (!source.exists()) {
                emit(OperationResult.Error("源文件不存在"))
                return@flow
            }

            val targetDir = File(targetPath)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val target = File(targetDir, source.name)

            if (source.isDirectory) {
                copyDirectory(source, target) { current, total, name ->
                    emit(OperationResult.Progress(current, total, name))
                }
            } else {
                source.copyTo(target, overwrite = true)
            }

            emit(OperationResult.Success("已复制到: ${target.absolutePath}"))
        } catch (e: Exception) {
            emit(OperationResult.Error("复制失败: ${e.message}", e))
        }
    }

    private fun copyDirectory(source: File, target: File, onProgress: (Int, Int, String) -> Unit) {
        if (!target.exists()) {
            target.mkdirs()
        }

        val files = source.listFiles() ?: return
        files.forEachIndexed { index, file ->
            val dest = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, dest, onProgress)
            } else {
                file.copyTo(dest, overwrite = true)
            }
            onProgress(index + 1, files.size, file.name)
        }
    }

    /**
     * 移动文件
     */
    suspend fun move(sourcePath: String, targetPath: String): OperationResult = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            if (!source.exists()) {
                return@withContext OperationResult.Error("源文件不存在")
            }

            val target = File(targetPath, source.name)

            if (source.renameTo(target)) {
                OperationResult.Success("已移动到: ${target.absolutePath}")
            } else {
                // 如果直接移动失败，尝试复制后删除
                copyAndDelete(source, target)
            }
        } catch (e: Exception) {
            OperationResult.Error("移动失败: ${e.message}", e)
        }
    }

    private suspend fun copyAndDelete(source: File, target: File): OperationResult {
        return try {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
            OperationResult.Success("已移动到: ${target.absolutePath}")
        } catch (e: Exception) {
            OperationResult.Error("移动失败: ${e.message}", e)
        }
    }

    /**
     * 获取父目录路径
     */
    fun getParentPath(path: String): String? {
        val file = File(path)
        return file.parentFile?.absolutePath
    }

    /**
     * 检查路径是否为根目录
     */
    fun isRootPath(path: String): Boolean {
        val rootPaths = listOf(
            Environment.getExternalStorageDirectory().absolutePath,
            "/",
            ""
        )
        return rootPaths.contains(path) || path.length <= 4
    }
}
