package com.example.qrscanner.ui.scanner

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrscanner.domain.model.ContentType
import com.example.qrscanner.domain.model.ScannedData
import com.example.qrscanner.domain.usecase.AnalyzeBarcodeUseCase
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * 扫描页 ViewModel
 * 职责：管理 CameraX 生命周期 + ML Kit 分析 + UI 状态
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeScanner: BarcodeScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    /** 分析线程专用单线程池，避免阻塞 UI */
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** 防抖时间戳：记录上一次成功检测的时间 */
    private var lastDetectedTimestamp = 0L

    /** 防抖间隔（毫秒）：同一条码 3 秒内不重复触发 */
    private val debounceIntervalMs = 3000L

    /** 相机分析用例 */
    private val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    /**
     * 绑定相机到生命周期
     * 在 Screen 的 DisposableEffect 中调用
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 预览用例
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 图像分析用例 - 接入 ML Kit
            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                // 相机绑定失败（设备不支持、权限问题等）
                _uiState.value = ScannerUiState.PermissionDenied
            }
        }, analysisExecutor)
    }

    /**
     * 处理相机帧图像
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { rawValue ->
                    handleDetectedBarcode(rawValue, barcodes.first().format)
                }
            }
            .addOnCompleteListener {
                // 必须关闭 imageProxy 以接收下一帧
                imageProxy.close()
            }
    }

    /**
     * 处理检测到的条码
     * 包含防抖逻辑和内容分析
     */
    private fun handleDetectedBarcode(rawValue: String, format: Int) {
        val now = System.currentTimeMillis()

        // 防抖：短时间内同一条码不重复触发
        if (now - lastDetectedTimestamp < debounceIntervalMs) {
            return
        }

        lastDetectedTimestamp = now

        val scannedData = ScannedData(rawValue = rawValue, format = format)
        val contentType = AnalyzeBarcodeUseCase.execute(scannedData)

        _uiState.value = ScannerUiState.Detected(contentType)
    }

    /**
     * 用户点击"继续扫描"后恢复扫描状态
     */
    fun continueScanning() {
        _uiState.value = ScannerUiState.Scanning
        lastDetectedTimestamp = 0L
    }

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
        barcodeScanner.close()
    }
}
