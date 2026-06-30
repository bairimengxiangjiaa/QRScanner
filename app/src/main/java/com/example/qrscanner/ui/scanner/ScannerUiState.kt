package com.example.qrscanner.ui.scanner

import com.example.qrscanner.domain.model.ContentType

/**
 * 扫描页 UI 状态
 * 使用 sealed class 保证状态穷举，编译器强制处理所有情况
 *
 * 设计说明：
 *   权限状态不放在这里——ScannerScreen 通过 ActivityResultContracts 管理权限（单一真相源）。
 *   ViewModel 仅管理扫描结果和相机运行状态。
 */
sealed class ScannerUiState {
    /** 正在扫描中 */
    object Scanning : ScannerUiState()

    /** 检测到条码，等待用户操作 */
    data class Detected(val contentType: ContentType) : ScannerUiState()

    /** 相机绑定失败（设备不支持等），需提示用户并允许重试 */
    object CameraError : ScannerUiState()
}
