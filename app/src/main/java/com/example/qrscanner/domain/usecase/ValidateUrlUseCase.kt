package com.example.qrscanner.domain.usecase

/**
 * URL 安全校验用例
 *
 * 设计目的：
 *   严格校验 URL 协议，防止 javascript:、file: 等危险协议注入攻击。
 *   采用 fail-closed 策略——未知协议一律拦截。
 *
 * 能做到：
 *   - 识别并放行安全协议（https）
 *   - 识别需二次确认的协议（http、weixin、alipays、alipay）
 *   - 拦截已知危险协议（javascript、file、content、data、intent）
 *   - 拦截未知协议（fail-closed）
 *   - 过滤全部控制字符（0x00-0x1F, 0x7F），防止 null byte/换行/制表符等绕过协议检测
 *
 * 做不到：
 *   - 不校验 URL 域名真实性（非 phishing 检测范畴）
 *   - 不校验 URL 参数合法性
 */
object ValidateUrlUseCase {

    // 允许直接打开的安全协议（无需二次确认）
    private val DIRECT_OPEN_PROTOCOLS = setOf("https")

    // 需要二次确认的协议（自定义 App 协议）
    private val CONFIRM_REQUIRED_PROTOCOLS = setOf("http", "weixin", "alipays", "alipay")

    // 绝对禁止的危险协议黑名单
    private val BLOCKED_PROTOCOLS = setOf(
        "javascript", "file", "content", "data", "intent"
    )

    /**
     * 控制字符正则：匹配全部 ASCII 控制字符 0x00-0x1F 以及 0x7F（DEL）
     * 不遗漏任何控制字符，防止 null byte、换行符(LF/CR)、制表符等绕过协议检测
     */
    private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F]")

    /**
     * 校验 URL 是否安全
     * @param url 待校验的 URL 字符串
     * @return 校验结果：安全则返回协议类型，危险则返回 Blocked
     */
    fun validate(url: String): ValidationResult {
        // 1. 去除首尾空白
        val trimmed = url.trim()

        // 2. 检查是否包含控制字符（null byte 注入、换行注入等）
        if (CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Blocked("control_chars")
        }

        // 3. 提取协议部分（第一个 : 之前的内容）
        // 注意：某些危险协议如 javascript:alert(1) 不带 ://
        val colonIndex = trimmed.indexOf(":")
        if (colonIndex <= 0) {
            // 没有冒号或冒号在首位，视为纯文本
            return ValidationResult.NotAUrl
        }

        val protocol = trimmed.substring(0, colonIndex).lowercase()

        // 4. 校验协议格式：只能包含字母和数字（防止 "hello world" 被误判）
        if (!protocol.matches(Regex("^[a-z][a-z0-9+.-]*$"))) {
            return ValidationResult.NotAUrl
        }

        // 5. 黑名单优先检查
        if (protocol in BLOCKED_PROTOCOLS) {
            return ValidationResult.Blocked(protocol)
        }

        // 6. 安全协议白名单
        if (protocol in DIRECT_OPEN_PROTOCOLS) {
            return ValidationResult.Safe(protocol)
        }

        // 7. 需要确认的协议
        if (protocol in CONFIRM_REQUIRED_PROTOCOLS) {
            return ValidationResult.RequiresConfirmation(protocol)
        }

        // 8. 未知协议一律拦截（fail-closed）
        return ValidationResult.Blocked(protocol)
    }

    sealed class ValidationResult {
        /** 安全，可直接打开 */
        data class Safe(val protocol: String) : ValidationResult()

        /** 需要用户二次确认 */
        data class RequiresConfirmation(val protocol: String) : ValidationResult()

        /** 危险协议，已拦截 */
        data class Blocked(val protocol: String) : ValidationResult()

        /** 不是 URL，是纯文本 */
        data object NotAUrl : ValidationResult()
    }
}
