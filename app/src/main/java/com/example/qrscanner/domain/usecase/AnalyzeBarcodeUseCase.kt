package com.example.qrscanner.domain.usecase

import com.example.qrscanner.domain.model.ContentType
import com.example.qrscanner.domain.model.ScannedData
import com.example.qrscanner.domain.usecase.ValidateUrlUseCase.ValidationResult

/**
 * 分析条码内容，将其分类为不同的 ContentType
 * 决定 UI 展示哪些操作按钮
 *
 * 设计目的：
 *   作为内容类型分类器，将扫描到的原始字符串分为 HTTPS、HTTP、微信、支付宝、纯文本等类型，
 *   所有分类决策统一经过 ValidateUrlUseCase 安全校验，不存在绕过路径。
 *
 * 能做到：
 *   - 识别 https/http 链接、微信/支付宝协议链接、纯文本
 *   - 对超长内容做截断处理，防止内存攻击
 *   - 所有内容统一经过协议安全校验（无快速路径绕过）
 *
 * 做不到：
 *   - 不解析 URL 域名或参数
 *   - 不检测 URL 是否指向恶意网站
 */
object AnalyzeBarcodeUseCase {

    /**
     * 扫描内容最大允许长度（4096 字符）
     * 超过此长度的内容直接降级为纯文本展示，防止内存消耗攻击
     */
    private const val MAX_CONTENT_LENGTH = 4096

    /**
     * 对外暴露的最大长度常量，供 UI 层做截断展示
     */
    const val DISPLAY_MAX_LENGTH = 512

    /**
     * 分析扫描到的条码内容，分类为 ContentType
     *
     * 重要安全设计：不使用 startsWith 做快速路径，
     * 所有协议判断统一经过 ValidateUrlUseCase，防止绕过安全校验。
     *
     * @param data 扫描到的原始数据
     * @return 分类后的内容类型
     */
    fun execute(data: ScannedData): ContentType {
        val raw = data.rawValue.trim()

        // 安全校验：超长内容降级为纯文本，仅展示截断后的部分
        if (raw.length > MAX_CONTENT_LENGTH) {
            return ContentType.PlainText(raw.take(DISPLAY_MAX_LENGTH))
        }

        // 统一通过 ValidateUrlUseCase 进行协议校验
        return when (val result = ValidateUrlUseCase.validate(raw)) {
            is ValidationResult.Safe -> {
                // 安全协议目前只有 https
                ContentType.HttpsUrl(raw)
            }
            is ValidationResult.RequiresConfirmation -> {
                // 根据协议类型分类为对应 ContentType
                when (result.protocol) {
                    "http" -> ContentType.HttpUrl(raw)
                    "weixin" -> ContentType.WeChatLink(raw)
                    "alipays", "alipay" -> ContentType.AlipayLink(raw)
                    else -> ContentType.PlainText(raw)
                }
            }
            is ValidationResult.Blocked -> {
                // 危险协议降级为纯文本展示（不提供"打开"按钮）
                ContentType.PlainText(raw)
            }
            is ValidationResult.NotAUrl -> {
                // 非协议内容视为纯文本
                ContentType.PlainText(raw)
            }
        }
    }
}
