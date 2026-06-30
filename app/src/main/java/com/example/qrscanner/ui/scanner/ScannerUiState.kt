package com.example.qrscanner.ui.scanner

import com.example.qrscanner.domain.model.ContentType

/**
 * 扫描页 UI 状态
 * 使用 sealed class 保证状态穷举，编译器强制处理所有情况
 */
sealed class ScannerUiState {
    /** 正在扫描中 */
    object Scanning : ScannerUiState()

    /** 检测到条码，等待用户操作 */
    data class Detected(val contentType: ContentType) : ScannerUiState()

    /** 权限未授予 */
    object PermissionDenied : ScannerUiState()

    /** 权限被永久拒绝，需要跳转系统设置 */
    object PermissionPermanentlyDenied : ScannerUiState()
}
