package com.example.qrscanner.domain.model

/**
 * 扫描结果的数据模型
 * @param rawValue 扫描到的原始字符串
 * @param format 条码格式（QR_CODE, EAN_13, UPC_A 等）
 */
data class ScannedData(
    val rawValue: String,
    val format: Int
)

/**
 * 内容类型分类，决定 UI 展示哪些操作按钮
 */
sealed class ContentType {
    /** 安全的 https 链接，直接打开 */
    data class HttpsUrl(val url: String) : ContentType()

    /** http 链接（非加密），可直接打开但有风险提示 */
    data class HttpUrl(val url: String) : ContentType()

    /** 微信协议链接，需要二次确认 */
    data class WeChatLink(val url: String) : ContentType()

    /** 支付宝协议链接，需要二次确认 */
    data class AlipayLink(val url: String) : ContentType()

    /** 纯文本，不识别为任何已知协议 */
    data class PlainText(val text: String) : ContentType()
}
