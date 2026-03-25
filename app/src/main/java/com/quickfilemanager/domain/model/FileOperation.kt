package com.quickfilemanager.domain.model

/**
 * 文件操作类型
 */
sealed class FileOperation {
    data class Copy(val sourcePath: String, val targetPath: String) : FileOperation()
    data class Move(val sourcePath: String, val targetPath: String) : FileOperation()
    data class Delete(val path: String) : FileOperation()
    data class Rename(val path: String, val newName: String) : FileOperation()
    data class CreateFolder(val path: String, val name: String) : FileOperation()
}

/**
 * 操作结果
 */
sealed class OperationResult {
    data class Success(val message: String) : OperationResult()
    data class Error(val message: String, val exception: Throwable? = null) : OperationResult()
    data class Progress(val current: Int, val total: Int, val fileName: String) : OperationResult()
}
