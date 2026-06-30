package com.example.qrscanner.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 剪贴板操作封装
 * 职责：提供类型安全的剪贴板写入，隐藏 Android API 细节
 */
@Singleton
class ClipboardRepository @Inject constructor(
    private val context: Context
) {

    /**
     * 将文本复制到系统剪贴板
     * @param label 剪贴板条目标签（调试可见，不展示给用户）
     * @param text 要复制的文本
     */
    fun copyToClipboard(label: String = "QRScanner", text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}
