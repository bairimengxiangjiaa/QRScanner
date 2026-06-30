package com.example.qrscanner.ui.result

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.qrscanner.domain.model.ContentType

/**
 * 扫描结果底部弹窗
 * 根据 ContentType 动态展示不同的操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    contentType: ContentType,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 二次确认弹窗状态
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf("") }
    var pendingAppName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 内容展示
            Text(
                text = getContentDisplayText(contentType),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 根据内容类型展示操作按钮
            when (contentType) {
                is ContentType.HttpsUrl -> {
                    // https 链接：直接打开 + 复制
                    ActionButton(
                        text = "打开链接",
                        icon = Icons.Filled.Language,
                        onClick = { onOpenLink(contentType.url) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButtons(
                        onCopy = { copyAndToast(context, contentType.url, onCopy) }
                    )
                }

                is ContentType.HttpUrl -> {
                    // http 链接：非加密，需要二次确认（MITM 风险）
                    Text(
                        text = "⚠ 未加密链接（存在安全风险）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActionButton(
                        text = "仍要打开",
                        icon = Icons.Filled.OpenInNew,
                        onClick = {
                            pendingUrl = contentType.url
                            pendingAppName = "浏览器"
                            showConfirmDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButtons(
                        onCopy = { copyAndToast(context, contentType.url, onCopy) }
                    )
                }

                is ContentType.WeChatLink -> {
                    // 微信：需要二次确认
                    ActionButton(
                        text = "打开微信",
                        icon = Icons.Filled.OpenInNew,
                        onClick = {
                            pendingUrl = contentType.url
                            pendingAppName = "微信"
                            showConfirmDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButtons(
                        onCopy = { copyAndToast(context, contentType.url, onCopy) }
                    )
                }

                is ContentType.AlipayLink -> {
                    // 支付宝：需要二次确认
                    ActionButton(
                        text = "打开支付宝",
                        icon = Icons.Filled.OpenInNew,
                        onClick = {
                            pendingUrl = contentType.url
                            pendingAppName = "支付宝"
                            showConfirmDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButtons(
                        onCopy = { copyAndToast(context, contentType.url, onCopy) }
                    )
                }

                is ContentType.PlainText -> {
                    // 纯文本：仅复制
                    ActionButton(
                        text = "复制",
                        icon = Icons.Filled.ContentCopy,
                        onClick = { copyAndToast(context, contentType.text, onCopy) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 继续扫描按钮
            TextButton(onClick = onDismiss) {
                Text("继续扫描")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 二次确认弹窗
    if (showConfirmDialog) {
        ConfirmDialog(
            appName = pendingAppName,
            onConfirm = {
                showConfirmDialog = false
                onOpenApp(pendingUrl)
            },
            onCancel = { showConfirmDialog = false }
        )
    }
}

/**
 * 主操作按钮（实心）
 */
@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * 次要操作按钮组（复制）
 */
@Composable
private fun OutlinedActionButtons(onCopy: () -> Unit) {
    OutlinedButton(
        onClick = onCopy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("复制")
    }
}

/**
 * 复制并提示
 */
private fun copyAndToast(context: android.content.Context, text: String, onCopy: (String) -> Unit) {
    onCopy(text)
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

/**
 * 获取显示文本
 */
private fun getContentDisplayText(contentType: ContentType): String = when (contentType) {
    is ContentType.HttpsUrl -> contentType.url
    is ContentType.HttpUrl -> contentType.url
    is ContentType.WeChatLink -> contentType.url
    is ContentType.AlipayLink -> contentType.url
    is ContentType.PlainText -> contentType.text
}

/**
 * 打开 App 前的二次确认弹窗
 */
@Composable
private fun ConfirmDialog(
    appName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认打开") },
        text = { Text("即将打开 $appName，是否继续？") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}
