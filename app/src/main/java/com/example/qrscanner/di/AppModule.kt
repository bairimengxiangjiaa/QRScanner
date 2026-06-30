package com.example.qrscanner.di

import android.content.Context
import com.example.qrscanner.data.repository.ClipboardRepository
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 * 提供全局单例：BarcodeScanner、ClipboardRepository
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * ML Kit 条码扫描器
     * 使用默认配置（Bundled 模型，无需 Google Play 服务）
     * 支持 QR Code、EAN-13、UPC-A、Code 128 等 20+ 格式
     */
    @Provides
    @Singleton
    fun provideBarcodeScanner(): BarcodeScanner {
        return BarcodeScanning.getClient()
    }

    @Provides
    @Singleton
    fun provideClipboardRepository(
        @ApplicationContext context: Context
    ): ClipboardRepository {
        return ClipboardRepository(context)
    }
}
