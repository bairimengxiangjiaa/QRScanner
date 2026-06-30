package com.example.qrscanner.ui.scanner

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.qrscanner.domain.model.ContentType
import com.example.qrscanner.domain.model.ScannedData
import com.example.qrscanner.domain.usecase.AnalyzeBarcodeUseCase
import com.google.mlkit.vision.barcode.BarcodeScanner
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
 *
 * 权限管理说明：
 *   相机权限的「是否已授权」由 ScannerScreen 通过 ActivityResultContracts 管理（单一真相源），
 *   ViewModel 不持有权限状态，避免双重真相。ViewModel 仅管理扫描结果与相机绑定错误。
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeScanner: BarcodeScanner,
    private val analyzeBarcodeUseCase: AnalyzeBarcodeUseCase
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
     *
     * 关键约束：CameraX 的所有 API（含 bindToLifecycle）必须在主线程调用，
     * 否则会抛 IllegalStateException 导致应用闪退。
     * 因此 Future 的回调必须运行在主线程 Executor 上，
     * analysisExecutor 仅用于图像分析（后台线程）。
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        // CameraX 强制主线程：使用主线程 Executor 执行回调
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 预览用例
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 图像分析用例 - 接入 ML Kit（分析在后台线程执行）
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
                // 相机绑定失败（设备不支持等），通知 UI 展示错误
                _uiState.value = ScannerUiState.CameraError
            }
        }, mainExecutor)
    }

    /**
     * 解绑相机，释放 CameraX 资源
     * 在 ScannerScreen 的 DisposableEffect.onDispose 中调用
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun unbindCamera(context: android.content.Context) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            // 忽略：退出时解绑失败不影响用户体验
        }
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
        val contentType = analyzeBarcodeUseCase.execute(scannedData)

        _uiState.value = ScannerUiState.Detected(contentType)
    }

    /**
     * 用户点击"继续扫描"后恢复扫描状态
     */
    fun continueScanning() {
        _uiState.value = ScannerUiState.Scanning
        lastDetectedTimestamp = 0L
    }

    /**
     * 相机出错后用户点击重试
     */
    fun retryCamera() {
        _uiState.value = ScannerUiState.Scanning
    }

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
        barcodeScanner.close()
    }
}
