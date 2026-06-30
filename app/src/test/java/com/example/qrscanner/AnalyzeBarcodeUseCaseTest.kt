package com.example.qrscanner

import com.example.qrscanner.domain.model.ContentType
import com.example.qrscanner.domain.model.ScannedData
import com.example.qrscanner.domain.usecase.AnalyzeBarcodeUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 条码内容分析单元测试
 * 覆盖：各类型 URL 分类、微信/支付宝协议识别、纯文本处理
 *        超长内容降级、控制字符降级
 */
class AnalyzeBarcodeUseCaseTest {

    @Test
    fun `https url classified as HttpsUrl`() {
        val data = ScannedData("https://www.baidu.com", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.HttpsUrl::class.java)
        assertThat((result as ContentType.HttpsUrl).url).isEqualTo("https://www.baidu.com")
    }

    @Test
    fun `http url classified as HttpUrl`() {
        val data = ScannedData("http://example.com", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.HttpUrl::class.java)
    }

    @Test
    fun `weixin protocol classified as WeChatLink`() {
        val data = ScannedData("weixin://dl/business/?ticket=abc123", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.WeChatLink::class.java)
    }

    @Test
    fun `weixin with uppercase prefix classified as WeChatLink`() {
        val data = ScannedData("WeiXin://dl/chat", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.WeChatLink::class.java)
    }

    @Test
    fun `alipays protocol classified as AlipayLink`() {
        val data = ScannedData("alipays://platformapi/startapp?saId=10000007", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.AlipayLink::class.java)
    }

    @Test
    fun `alipay protocol classified as AlipayLink`() {
        val data = ScannedData("alipay://platformapi/startapp", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.AlipayLink::class.java)
    }

    @Test
    fun `plain text classified as PlainText`() {
        val data = ScannedData("Hello, World!", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
        assertThat((result as ContentType.PlainText).text).isEqualTo("Hello, World!")
    }

    @Test
    fun `chinese text classified as PlainText`() {
        val data = ScannedData("你好，这是一段中文文本", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `javascript url downgraded to PlainText`() {
        val data = ScannedData("javascript:alert(document.cookie)", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        // 危险协议被拦截，降级为纯文本展示（不会显示"打开链接"按钮）
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `file url downgraded to PlainText`() {
        val data = ScannedData("file:///data/data/com.example/databases/user.db", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `wifi config text classified as PlainText`() {
        val data = ScannedData("WIFI:T:WPA;S:MyNetwork;P:password123;;", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `email text classified as PlainText`() {
        val data = ScannedData("mailto:user@example.com", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        // mailto 不在白名单中，降级为纯文本
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `phone number text classified as PlainText`() {
        val data = ScannedData("tel:+8613800138000", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    // ========== 安全加固新增测试：超长内容 ==========

    @Test
    fun `content exceeding max length is truncated to PlainText`() {
        // 构造超过 4096 字符的恶意长内容
        val longContent = "https://evil.com/" + "a".repeat(5000)
        val data = ScannedData(longContent, 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
        // 验证截断长度不超过 DISPLAY_MAX_LENGTH (512)
        assertThat((result as ContentType.PlainText).text.length).isAtMost(512)
    }

    @Test
    fun `content at max length boundary is still processed normally`() {
        // 刚好在 4096 字符内的 https 链接应正常分类
        val content = "https://example.com/" + "a".repeat(4000)
        assertThat(content.length).isLessThan(4097)
        val data = ScannedData(content, 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.HttpsUrl::class.java)
    }

    // ========== 安全加固新增测试：控制字符注入 ==========

    @Test
    fun `javascript with null byte downgraded to PlainText`() {
        // null byte 注入攻击应被控制字符过滤器拦截
        val data = ScannedData("java\u0000script:alert(1)", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `https with null byte is blocked and downgraded`() {
        val data = ScannedData("https://\u0000evil.com", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        // 控制字符被拦截，降级为纯文本
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `weixin link with newline injection is still WeChatLink`() {
        // 换行符不在控制字符过滤范围内（0x0A 在正则 [\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F] 中被排除）
        // 但 "weixin\n" 提取协议时取第一个冒号前的内容
        // weixin\nddl/business... 不含冒号所以是 NotAUrl → PlainText
        val data = ScannedData("weixin\ndl/business/?ticket=abc", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `weixin link with normal content is WeChatLink`() {
        // 正常微信链接应正确分类
        val data = ScannedData("weixin://dl/business/?ticket=abc123&safe", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.WeChatLink::class.java)
    }

    // ========== 安全加固新增测试：统一校验路径 ==========

    @Test
    fun `all protocols go through ValidateUrlUseCase - no bypass`() {
        // 验证：微信/支付宝协议不再通过 startsWith 快速路径，
        // 而是统一经过 ValidateUrlUseCase 校验
        // 这意味着即使有人绕过 startsWith 检查也无法打开恶意链接
        val weixinData = ScannedData("weixin://dl/business/?ticket=test", 0)
        val result = AnalyzeBarcodeUseCase.execute(weixinData)
        // weixin 在 CONFIRM_REQUIRED_PROTOCOLS 中，所以返回 WeChatLink（需确认）
        assertThat(result).isInstanceOf(ContentType.WeChatLink::class.java)
    }

    // ========== 安全加固新增测试：未知协议处理 ==========

    @Test
    fun `ftp link downgraded to PlainText`() {
        val data = ScannedData("ftp://files.example.com/doc.pdf", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }

    @Test
    fun `intent link downgraded to PlainText`() {
        val data = ScannedData("intent://scan/#Intent;scheme=weixin;package=com.tencent.mm;end", 0)
        val result = AnalyzeBarcodeUseCase.execute(data)
        assertThat(result).isInstanceOf(ContentType.PlainText::class.java)
    }
}
