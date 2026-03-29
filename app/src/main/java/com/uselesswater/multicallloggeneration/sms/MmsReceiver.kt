package com.uselesswater.multicallloggeneration.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * MMS接收器（成为默认短信应用必需组件）
 */
class MmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到MMS广播: ${intent.action}")
        // 本应用不处理MMS
    }
}