package com.uselesswater.multicallloggeneration.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 短信发送服务（成为默认短信应用必需组件）
 * 用于处理RESPOND_VIA_MESSAGE意图
 */
class HeadlessSmsSendService : Service() {

    companion object {
        private const val TAG = "HeadlessSmsSendService"
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind: $intent")
        return null
    }
}