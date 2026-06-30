package com.example.qrscanner.ui.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.qrscanner.data.repository.ClipboardRepository
import com.example.qrscanner.ui.permission.PermissionScreen
import com.example.qrscanner.ui.result.ResultSheet
import com.example.qrscanner.ui.theme.ScanFrame
import com.example.qrscanner.ui.theme.ScanOverlay

/**
 * 扫描页
 * 展示相机预览 + 扫描框叠加层 + 结果弹窗
 */
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    clipboardRepository: ClipboardRepository,
    onOpenApp: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // 权限状态
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限申请回调
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // 首次进入且无权限则发起申请
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasCameraPermission -> {
                PermissionScreen(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onGoToSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            else -> {
                // 相机预览层
                CameraPreview(viewModel = viewModel, lifecycleOwner = lifecycleOwner)

                // 扫描框叠加层
                ScanOverlay()

                // 底部提示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "将二维码/条形码放入框内",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 结果弹窗
                if (uiState is ScannerUiState.Detected) {
                    val contentType = (uiState as ScannerUiState.Detected).contentType
                    ResultSheet(
                        contentType = contentType,
                        onDismiss = { viewModel.continueScanning() },
                        onOpenLink = { url ->
                            openInBrowser(context, url)
                            viewModel.continueScanning()
                        },
                        onOpenApp = { url ->
                            onOpenApp(url)
                            viewModel.continueScanning()
                        },
                        onCopy = { text ->
                            clipboardRepository.copyToClipboard(text = text)
                            viewModel.continueScanning()
                        }
                    )
                }
            }
        }
    }
}

/**
 * CameraX 相机预览
 * 使用 AndroidView 嵌入 PreviewView
 */
@Composable
private fun CameraPreview(
    viewModel: ScannerViewModel,
    lifecycleOwner: LifecycleOwner
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                viewModel.bindCamera(lifecycleOwner, this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 扫描框叠加层：半透明遮罩 + 中心透明区域 + 角落标记
 */
@Composable
private fun ScanOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanSize = size.minDimension * 0.7f
        val left = (size.width - scanSize) / 2
        val top = (size.height - scanSize) / 2 - 50f
        val scanRect = Rect(left, top, left + scanSize, top + scanSize)

        // 绘制半透明遮罩（中间镂空）
        val path = Path().apply {
            addRoundRect(RoundRect(scanRect, CornerRadius(16f, 16f)))
        }
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(ScanOverlay)
        }

        // 绘制扫描框边框
        drawRoundRect(
            color = ScanFrame,
            topLeft = Offset(scanRect.left, scanRect.top),
            size = Size(scanRect.width, scanRect.height),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3f)
        )

        // 绘制四角标记
        drawCornerMarks(scanRect, ScanFrame)
    }
}

/**
 * 绘制扫描框四角 L 形标记
 */
private fun DrawScope.drawCornerMarks(rect: Rect, color: Color) {
    val markLength = 30f
    val strokeWidth = 4f
    val corners = listOf(
        // 左上
        listOf(Offset(rect.left, rect.top + markLength), Offset(rect.left, rect.top), Offset(rect.left + markLength, rect.top)),
        // 右上
        listOf(Offset(rect.right - markLength, rect.top), Offset(rect.right, rect.top), Offset(rect.right, rect.top + markLength)),
        // 左下
        listOf(Offset(rect.left, rect.bottom - markLength), Offset(rect.left, rect.bottom), Offset(rect.left + markLength, rect.bottom)),
        // 右下
        listOf(Offset(rect.right - markLength, rect.bottom), Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - markLength))
    )

    corners.forEach { points ->
        points.zipWithNext().forEach { (start, end) ->
            drawLine(color, start, end, strokeWidth)
        }
    }
}

/**
 * 在系统浏览器中打开 URL
 * 使用 ACTION_VIEW 让系统选择默认浏览器，沙箱隔离
 */
private fun openInBrowser(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        // 强制在新任务中打开，不在应用内嵌
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // 使用 createChooser 防止 Intent 被恶意应用拦截，同时给用户选择浏览器的权利
    val chooser = Intent.createChooser(intent, "用浏览器打开").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
