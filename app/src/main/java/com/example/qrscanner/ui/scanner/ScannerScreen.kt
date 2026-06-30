package com.example.qrscanner.ui.scanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.qrscanner.data.repository.ClipboardRepository
import com.example.qrscanner.ui.permission.PermissionScreen
import com.example.qrscanner.ui.result.ResultSheet
import com.example.qrscanner.ui.theme.ScanFrame

/**
 * 扫描页
 * 展示相机预览 + 扫描框叠加层 + 结果弹窗
 *
 * 沉浸式设计：相机预览全屏铺满（穿透状态栏），UI 元素通过 window insets 避让系统栏。
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

    // 权限状态：单一真相源在此 Composable 管理
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限申请回调
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
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

            uiState is ScannerUiState.CameraError -> {
                CameraErrorScreen(
                    onRetry = { viewModel.retryCamera() }
                )
            }

            else -> {
                // 相机场景：状态栏图标设为白色（深色相机背景）
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        WindowCompat.getInsetsController(window, view)
                            .isAppearanceLightStatusBars = false
                    }
                }

                // 相机预览层（全屏，穿透状态栏）
                CameraPreview(
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner
                )

                // 扫描框叠加层（含扫描线动画）
                ScanOverlay()

                // 底部提示（避让导航栏）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding() + 48.dp
                        ),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "将二维码 / 条形码对准框内",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp
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
 *
 * C2 修复：通过 DisposableEffect 在 Composable 退出时解绑相机，
 * 避免生命周期外的相机占用。
 */
@Composable
private fun CameraPreview(
    viewModel: ScannerViewModel,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                viewModel.bindCamera(lifecycleOwner, this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Composable 销毁时解绑相机，释放资源
    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.unbindCamera(context)
        }
    }
}

/**
 * 扫描框叠加层：半透明遮罩 + 中心透明区域 + 角落标记 + 扫描线动画
 */
@Composable
private fun ScanOverlay() {
    // 扫描线位移动画
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanSize = size.minDimension * 0.65f
        val left = (size.width - scanSize) / 2
        val top = (size.height - scanSize) / 2
        val scanRect = Rect(left, top, left + scanSize, top + scanSize)

        // 绘制半透明遮罩（中间镂空）
        val path = Path().apply {
            addRoundRect(RoundRect(scanRect, CornerRadius(24f, 24f)))
        }
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(Color(0xCC000000))
        }

        // 绘制扫描框边框
        drawRoundRect(
            color = ScanFrame.copy(alpha = 0.4f),
            topLeft = Offset(scanRect.left, scanRect.top),
            size = Size(scanRect.width, scanRect.height),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 2f)
        )

        // 绘制四角标记
        drawCornerMarks(scanRect, ScanFrame)

        // 绘制扫描线（随动画上下移动）
        val lineY = scanRect.top + scanRect.height * scanLineProgress
        drawLine(
            color = ScanFrame.copy(alpha = 0.85f),
            start = Offset(scanRect.left + 8f, lineY),
            end = Offset(scanRect.right - 8f, lineY),
            strokeWidth = 3f
        )
    }
}

/**
 * 绘制扫描框四角 L 形标记
 */
private fun DrawScope.drawCornerMarks(rect: Rect, color: Color) {
    val markLength = 36f
    val strokeWidth = 5f
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
 * 相机错误页面
 */
@Composable
private fun CameraErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "相机无法启动",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "请检查相机是否被其他应用占用，或重启设备后重试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * 在系统浏览器中打开 URL
 * 使用 ACTION_VIEW 让系统选择默认浏览器，沙箱隔离
 *
 * 异常处理：设备可能无浏览器（ActivityNotFoundException）或 Intent 解析失败，
 * 未捕获会直接崩溃，故需区分业务异常与系统异常。
 */
private fun openInBrowser(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "用浏览器打开").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
