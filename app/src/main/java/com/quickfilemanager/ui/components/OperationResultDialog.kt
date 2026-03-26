package com.quickfilemanager.ui.components
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
@Composable
fun OperationResultDialog(message: String, isError: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (isError) "操作失败" else "操作成功") }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } })
}
