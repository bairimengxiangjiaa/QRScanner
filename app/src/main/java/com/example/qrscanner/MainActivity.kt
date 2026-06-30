package com.example.qrscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.qrscanner.ui.theme.QRScannerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 宿主
 * @AndroidEntryPoint 启用 Hilt 注入（供 ViewModel 使用）
 *
 * 沉浸式实现：enableEdgeToEdge() 让内容穿透系统栏，
 * 状态栏图标外观由各 Composable 按场景动态控制。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 默认浅色状态栏图标（PermissionScreen 场景）
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDarkMode()

        setContent {
            QRScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScannerRoot()
                }
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
