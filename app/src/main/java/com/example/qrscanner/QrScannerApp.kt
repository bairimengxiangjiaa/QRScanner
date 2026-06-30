package com.example.qrscanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.qrscanner.data.repository.ClipboardRepository
import com.example.qrscanner.ui.scanner.ScannerScreen
import com.example.qrscanner.ui.scanner.ScannerViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

/**
 * Hilt Application 入口
 * AndroidManifest 中 android:name=".QrScannerApp" 引用此类
 * @HiltAndroidApp 触发 Hilt 代码生成，提供 ViewModel 注入、EntryPoint 等能力
 */
@HiltAndroidApp
class QrScannerApp : Application()

/**
 * Compose 根入口
 * 组装 ScannerScreen 并处理 App 跳转事件
 */
@Composable
fun ScannerRoot() {
    val context = LocalContext.current
    val viewModel: ScannerViewModel = hiltViewModel()
    val clipboardRepository: ClipboardRepository = hiltClipboardRepository()

    Surface(modifier = Modifier.fillMaxSize()) {
        ScannerScreen(
            viewModel = viewModel,
            clipboardRepository = clipboardRepository,
            onOpenApp = { url ->
                openApp(context, url)
            }
        )
    }
}

/**
 * 通过 Hilt EntryPoint 获取 ClipboardRepository 实例
 * Compose 中的非 ViewModel 依赖需要通过 EntryPoint 机制获取
 */
@Composable
private fun hiltClipboardRepository(): ClipboardRepository {
    return EntryPointAccessors.fromApplication(
        LocalContext.current.applicationContext,
        ClipboardRepositoryEntryPoint::class.java
    ).clipboardRepository()
}

/**
 * ClipboardRepository 的 Hilt EntryPoint 接口
 * 用于在非 @HiltViewModel 管理的 Composable 中获取 ClipboardRepository
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipboardRepositoryEntryPoint {
    fun clipboardRepository(): ClipboardRepository
}

/**
 * 打开外部 App（微信/支付宝）
 * 通过 Intent.ACTION_VIEW 调用系统解析对应协议
 * 如果目标 App 未安装则提示用户
 *
 * 安全措施：
 * - 使用 Intent.createChooser 防止 Intent 被恶意应用拦截
 * - 使用 FLAG_ACTIVITY_NEW_TASK 防止任务栈注入
 * - chooser 标题非 null，确保用户明确知道正在选择应用
 */
private fun openApp(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 使用 createChooser 防止 Intent 被拦截，标题明确提示用户
        val chooser = Intent.createChooser(intent, "选择应用打开").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        // 目标 App 未安装或无对应处理器
        Toast.makeText(context, "未安装对应应用", Toast.LENGTH_SHORT).show()
    }
}
