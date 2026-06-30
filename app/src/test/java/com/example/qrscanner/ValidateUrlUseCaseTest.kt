package com.example.qrscanner

import com.example.qrscanner.domain.usecase.ValidateUrlUseCase
import com.example.qrscanner.domain.usecase.ValidateUrlUseCase.ValidationResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * URL 安全校验单元测试
 * 覆盖：白名单协议、黑名单协议、危险协议拦截、纯文本识别
 *        控制字符注入、null byte 注入、零宽字符绕过、IDN 欺骗
 */
class ValidateUrlUseCaseTest {

    @Test
    fun `https url returns Safe`() {
        val result = ValidateUrlUseCase.validate("https://www.example.com")
        assertThat(result).isInstanceOf(ValidationResult.Safe::class.java)
    }

    @Test
    fun `http url returns RequiresConfirmation`() {
        val result = ValidateUrlUseCase.validate("http://www.example.com")
        assertThat(result).isInstanceOf(ValidationResult.RequiresConfirmation::class.java)
    }

    @Test
    fun `javascript protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("javascript:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `file protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("file:///etc/passwd")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `content protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("content://contacts/people")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `data protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("data:text/html,<script>alert(1)</script>")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `intent protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("intent://scan/#Intent;scheme=zxing;end")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `plain text is NotAUrl`() {
        val result = ValidateUrlUseCase.validate("hello world")
        assertThat(result).isEqualTo(ValidationResult.NotAUrl)
    }

    @Test
    fun `plain text with special chars is NotAUrl`() {
        val result = ValidateUrlUseCase.validate("你好世界 12345 !@#$%")
        assertThat(result).isEqualTo(ValidationResult.NotAUrl)
    }

    @Test
    fun `weixin protocol returns RequiresConfirmation`() {
        val result = ValidateUrlUseCase.validate("weixin://dl/business/?ticket=xxx")
        assertThat(result).isInstanceOf(ValidationResult.RequiresConfirmation::class.java)
    }

    @Test
    fun `alipays protocol returns RequiresConfirmation`() {
        val result = ValidateUrlUseCase.validate("alipays://platformapi/startapp?saId=10000007")
        assertThat(result).isInstanceOf(ValidationResult.RequiresConfirmation::class.java)
    }

    @Test
    fun `https with uppercase is still safe`() {
        val result = ValidateUrlUseCase.validate("HTTPS://www.example.com")
        assertThat(result).isInstanceOf(ValidationResult.Safe::class.java)
    }

    @Test
    fun `javascript with uppercase is still blocked`() {
        val result = ValidateUrlUseCase.validate("JAVASCRIPT:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `empty string is NotAUrl`() {
        val result = ValidateUrlUseCase.validate("")
        assertThat(result).isEqualTo(ValidationResult.NotAUrl)
    }

    @Test
    fun `url with whitespace is trimmed and validated`() {
        val result = ValidateUrlUseCase.validate("  https://example.com  ")
        assertThat(result).isInstanceOf(ValidationResult.Safe::class.java)
    }

    @Test
    fun `unknown protocol is blocked`() {
        val result = ValidateUrlUseCase.validate("unknownapp://something")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    // ========== 安全加固新增测试：控制字符注入 ==========

    @Test
    fun `null byte in https url is blocked`() {
        // null byte 注入攻击：尝试在协议中插入 NUL(U+0000) 绕过检测
        val result = ValidateUrlUseCase.validate("java\u0000script:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `null byte in javascript url is blocked`() {
        val result = ValidateUrlUseCase.validate("javascript\u0000:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `newline character in url is blocked`() {
        // 换行注入攻击：尝试用换行符分割协议
        val result = ValidateUrlUseCase.validate("javascript\n:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `carriage return in url is blocked`() {
        val result = ValidateUrlUseCase.validate("javascript\r:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `tab character in url is blocked`() {
        // U+0009 HT (制表符) 同样视为控制字符，应被拦截
        val result = ValidateUrlUseCase.validate("java\tscript:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `DEL character in url is blocked`() {
        // U+007F DEL 控制字符
        val result = ValidateUrlUseCase.validate("jav\u007Fascript:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `zero width characters in url are blocked`() {
        // 零宽字符攻击：U+0001 (SOH) 用于绕过肉眼检测
        val result = ValidateUrlUseCase.validate("java\u0001script:alert(1)")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    // ========== 安全加固新增测试：IDN 域名欺骗 ==========

    @Test
    fun `https url with normal punycode is safe`() {
        // 合法的 punycode 域名应正常通过
        val result = ValidateUrlUseCase.validate("https://xn--example-9ua.com")
        assertThat(result).isInstanceOf(ValidationResult.Safe::class.java)
    }

    @Test
    fun `http url with long domain requires confirmation`() {
        // 长 HTTP 链接应要求确认（不应因长度而直接放行）
        val longUrl = "http://www." + "a".repeat(2000) + ".example.com/path"
        val result = ValidateUrlUseCase.validate(longUrl)
        assertThat(result).isInstanceOf(ValidationResult.RequiresConfirmation::class.java)
    }

    // ========== 安全加固新增测试：边界情况 ==========

    @Test
    fun `protocol with only colon is NotAUrl`() {
        // 冒号在首位，colonIndex <= 0
        val result = ValidateUrlUseCase.validate(":something")
        assertThat(result).isEqualTo(ValidationResult.NotAUrl)
    }

    @Test
    fun `protocol starting with digit is NotAUrl`() {
        // 协议必须以字母开头（正则 ^[a-z]...）
        val result = ValidateUrlUseCase.validate("123abc://example.com")
        assertThat(result).isEqualTo(ValidationResult.NotAUrl)
    }

    @Test
    fun `ftp protocol is blocked as unknown`() {
        // FTP 不在白名单中，应被拦截
        val result = ValidateUrlUseCase.validate("ftp://files.example.com/document.pdf")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `ssh protocol is blocked as unknown`() {
        val result = ValidateUrlUseCase.validate("ssh://user@host")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `tel protocol is blocked as unknown`() {
        val result = ValidateUrlUseCase.validate("tel:+8613800138000")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `mailto protocol is blocked as unknown`() {
        val result = ValidateUrlUseCase.validate("mailto:user@example.com")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }

    @Test
    fun `sms protocol is blocked as unknown`() {
        val result = ValidateUrlUseCase.validate("sms:+8613800138000?body=Hello")
        assertThat(result).isInstanceOf(ValidationResult.Blocked::class.java)
    }
}
