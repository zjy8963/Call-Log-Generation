package com.uselesswater.multicallloggeneration

import android.provider.Telephony

/**
 * 短信类型枚举，提供类型安全和厂商适配
 */
enum class SmsType(
    val value: Int,
    val displayName: String,
    val uriPath: String,
    val requiresAddress: Boolean = true,
    val isOutgoing: Boolean = false
) {
    /**
     * 接收的短信（收件箱）
     */
    INBOX(
        value = SmsConstants.SMS_TYPE_INBOX,
        displayName = "接收短信",
        uriPath = "inbox",
        isOutgoing = false
    ),

    /**
     * 发送的短信（已发送）
     */
    SENT(
        value = SmsConstants.SMS_TYPE_SENT,
        displayName = "发送短信",
        uriPath = "sent",
        isOutgoing = true
    ),

    /**
     * 草稿短信
     */
    DRAFT(
        value = SmsConstants.SMS_TYPE_DRAFT,
        displayName = "草稿短信",
        uriPath = "draft",
        isOutgoing = true
    ),

    /**
     * 发送失败的短信
     */
    FAILED(
        value = SmsConstants.SMS_TYPE_FAILED,
        displayName = "发送失败",
        uriPath = "failed",
        isOutgoing = true
    ),

    /**
     * 发件箱（发送中）
     */
    OUTBOX(
        value = SmsConstants.SMS_TYPE_OUTBOX,
        displayName = "发件箱",
        uriPath = "outbox",
        isOutgoing = true
    );

    companion object {
        fun fromValue(value: Int): SmsType? {
            return entries.find { it.value == value }
        }

        fun getAllOptions(): List<Pair<String, Int>> {
            return entries.map { it.displayName to it.value }
        }
    }

    /**
     * 获取完整URI
     */
    fun getUri(): String = "${SmsConstants.SMS_URI}/$uriPath"

    /**
     * 获取备用URI（主URI失败时尝试）
     * 部分设备可能只支持主URI content://sms
     */
    fun getFallbackUri(): String = SmsConstants.SMS_URI
}