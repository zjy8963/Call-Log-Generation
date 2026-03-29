package com.uselesswater.multicallloggeneration

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * 默认短信应用管理器
 * 处理WRITE_SMS权限的替代方案：切换默认短信应用
 * 注意：所有UI交互（对话框）已移至Compose层处理
 */
class DefaultSmsManager(private val activity: Activity) {

    companion object {
        private const val TAG = "DefaultSmsManager"
        const val REQUEST_CHANGE_DEFAULT_SMS = 1001
    }

    private var pendingCallback: ((Boolean) -> Unit)? = null
    private var previousDefaultSmsApp: String? = null

    /**
     * 检查是否为默认短信应用
     */
    fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(activity)
            defaultPackage == activity.packageName
        }
    }

    /**
     * 获取当前默认短信应用包名
     */
    fun getDefaultSmsPackage(): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(activity)
        } catch (e: Exception) {
            Log.w(TAG, "获取默认短信应用失败: ${e.message}")
            null
        }
    }

    /**
     * 直接启动默认短信应用设置界面（无对话框）
     * UI层应在调用前显示说明对话框
     */
    fun requestDefaultSmsApp(onResult: (Boolean) -> Unit) {
        if (isDefaultSmsApp()) {
            onResult(true)
            return
        }

        // 保存回调供后续使用
        pendingCallback = onResult

        launchDefaultSmsRequest()
    }

    /**
     * 启动默认短信应用设置
     */
    private fun launchDefaultSmsRequest() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用RoleManager
                val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                activity.startActivityForResult(intent, REQUEST_CHANGE_DEFAULT_SMS)
            } else {
                // Android 9及以下使用传统方式
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
                activity.startActivityForResult(intent, REQUEST_CHANGE_DEFAULT_SMS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动默认短信设置失败: ${e.message}", e)
            pendingCallback?.invoke(false)
            pendingCallback = null
        }
    }

    /**
     * 在Activity的onActivityResult中调用
     */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CHANGE_DEFAULT_SMS) {
            val success = isDefaultSmsApp()
            pendingCallback?.invoke(success)
            pendingCallback = null
        }
    }

    /**
     * 恢复之前的默认短信应用
     * 返回是否成功启动恢复流程（实际恢复需要用户确认）
     */
    fun restoreDefaultSmsApp(onComplete: ((Boolean) -> Unit)? = null) {
        val previousApp = previousDefaultSmsApp ?: getDefaultSmsPackage()

        if (previousApp == null || previousApp == activity.packageName) {
            // 尝试恢复系统默认
            restoreToSystemDefault(onComplete)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 无法直接设置其他应用为默认，需要引导用户
                // UI层应显示对话框引导用户手动设置
                onComplete?.invoke(false)
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, previousApp)
                activity.startActivity(intent)
                onComplete?.invoke(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复默认短信应用失败: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * 恢复到系统默认短信应用
     */
    private fun restoreToSystemDefault(onComplete: ((Boolean) -> Unit)?) {
        try {
            // 获取系统短信应用
            val systemSmsApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+
                listOf("com.google.android.apps.messaging", "com.android.mms")
            } else {
                listOf("com.android.mms", "com.google.android.apps.messaging")
            }

            val installedSystemApp = systemSmsApps.find { packageName ->
                try {
                    activity.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }

            if (installedSystemApp != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, installedSystemApp)
                activity.startActivity(intent)
                onComplete?.invoke(true)
            } else {
                // Android 10+ 或找不到系统应用，需要用户手动设置
                onComplete?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复系统默认失败: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * 打开系统默认应用设置界面
     */
    fun openDefaultAppsSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        activity.startActivity(intent)
    }

    /**
     * 保存当前默认短信应用（在切换前调用）
     */
    fun saveCurrentDefaultSmsApp() {
        previousDefaultSmsApp = getDefaultSmsPackage()
        Log.d(TAG, "保存当前默认短信应用: $previousDefaultSmsApp")
    }

    /**
     * 获取保存的默认短信应用
     */
    fun getPreviousDefaultSmsApp(): String? = previousDefaultSmsApp

    /**
     * 检查必要的短信权限
     */
    fun checkSmsPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            SmsConstants.PERMISSION_READ_SMS,
            SmsConstants.PERMISSION_SEND_SMS,
            SmsConstants.PERMISSION_RECEIVE_SMS
        )

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求短信权限
     */
    fun requestSmsPermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        onResult: (Boolean) -> Unit
    ) {
        val permissions = arrayOf(
            SmsConstants.PERMISSION_READ_SMS,
            SmsConstants.PERMISSION_SEND_SMS,
            SmsConstants.PERMISSION_RECEIVE_SMS
        )

        launcher.launch(permissions)
    }
}