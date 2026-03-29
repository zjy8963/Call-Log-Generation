package com.uselesswater.multicallloggeneration.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * 短信接收器（成为默认短信应用必需组件）
 * 本应用仅用于生成记录，不实际处理短信
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到短信广播: ${intent.action}")

        // 本应用不实际处理短信接收，直接转发给系统默认应用
        // 或者忽略（因为我们只是临时作为默认应用）

        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            // 如果需要，可以在这里处理短信
            // 但本工具仅用于生成记录，不需要实际处理
            Log.d(TAG, "短信已接收，但本应用仅用于记录生成")
        }
    }
}