package com.uselesswater.multicallloggeneration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log

/**
 * 短信记录生成工具类
 * 防御性编程：支持多种设备厂商字段适配
 *
 * 修复说明：
 * 1. 动态检测数据库实际支持的字段，避免插入不存在的列（如小米设备的sim_id问题）
 * 2. 提供备用URI机制，当特定URI失败时回退到主URI
 * 3. 更完善的SIM字段处理策略
 */
object SmsGenerator {

    private const val TAG = "SmsGenerator"

    // 缓存设备实际支持的字段，避免重复查询
    private var cachedSupportedFields: Set<String>? = null
    private var cachedManufacturer: String? = null

    /**
     * 短信生成配置
     */
    data class SmsGenerationConfig(
        val phoneNumber: String,
        val content: String,
        val timestamp: Long,
        val smsType: SmsType,
        val simSlot: Int = 1,
        val read: Boolean = true,
        val threadId: Long? = null
    )

    /**
     * 获取设备实际支持的短信字段
     * 通过查询数据库表结构获取真实存在的字段
     */
    private fun getActualSupportedFields(context: Context): Set<String> {
        val currentManufacturer = Build.MANUFACTURER.lowercase()

        // 如果厂商改变，清除缓存
        if (cachedManufacturer != currentManufacturer) {
            cachedSupportedFields = null
            cachedManufacturer = currentManufacturer
        }

        // 使用缓存避免重复查询
        cachedSupportedFields?.let { return it }

        return try {
            val uri = Uri.parse(SmsConstants.SMS_URI)
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                null, // 查询所有列
                "1=0", // 不返回任何行，只获取列信息
                null,
                null
            )

            val fields = mutableSetOf<String>()
            cursor?.let {
                val columnNames = it.columnNames
                fields.addAll(columnNames)
                Log.d(TAG, "数据库实际字段(${currentManufacturer}): ${columnNames.joinToString()}")
                it.close()
            }

            // 如果查询失败，使用基于厂商的推荐字段
            if (fields.isEmpty()) {
                fields.addAll(getRecommendedFieldsForManufacturer(currentManufacturer))
                Log.w(TAG, "无法查询数据库字段，使用推荐字段: ${fields.joinToString()}")
            }

            cachedSupportedFields = fields
            fields
        } catch (e: Exception) {
            Log.e(TAG, "获取数据库字段失败: ${e.message}", e)
            // 返回基于厂商的推荐字段
            val fallbackFields = getRecommendedFieldsForManufacturer(currentManufacturer)
            cachedSupportedFields = fallbackFields
            fallbackFields
        }
    }

    /**
     * 根据厂商获取推荐的字段集合
     */
    private fun getRecommendedFieldsForManufacturer(manufacturer: String): Set<String> {
        val standardFields = setOf(
            Telephony.TextBasedSmsColumns.ADDRESS,
            Telephony.TextBasedSmsColumns.BODY,
            Telephony.TextBasedSmsColumns.DATE,
            Telephony.TextBasedSmsColumns.DATE_SENT,
            Telephony.TextBasedSmsColumns.READ,
            Telephony.TextBasedSmsColumns.STATUS,
            Telephony.TextBasedSmsColumns.TYPE,
            Telephony.TextBasedSmsColumns.THREAD_ID,
            Telephony.TextBasedSmsColumns.PERSON,
            Telephony.TextBasedSmsColumns.PROTOCOL,
            Telephony.TextBasedSmsColumns.REPLY_PATH_PRESENT,
            Telephony.TextBasedSmsColumns.SUBJECT,
            Telephony.TextBasedSmsColumns.SERVICE_CENTER,
            Telephony.TextBasedSmsColumns.LOCKED,
            Telephony.TextBasedSmsColumns.ERROR_CODE
        )

        return when (manufacturer) {
            "xiaomi", "redmi" -> standardFields + setOf("sub_id", "mx_status", "favorite", "bubble_info")
            "vivo", "iqoo" -> standardFields + setOf("sub_id", "sim_id", "seen", "deletable")
            "oppo", "realme", "oneplus" -> standardFields + setOf("sub_id", "oppo_sub_id", "block_type")
            "huawei", "honor" -> standardFields + setOf("sub_id", "hw_sub_id", "hw_priority")
            "samsung" -> standardFields + setOf("sub_id", "secret_mode", "safe_message")
            else -> standardFields + setOf("sub_id", "seen") // 通用Android
        }
    }

    /**
     * 创建短信ContentValues
     * 防御性编程：自动适配不同设备字段，只插入设备实际支持的字段
     */
    fun createSmsValues(config: SmsGenerationConfig, context: Context): ContentValues {
        // 获取设备实际支持的字段
        val actualSupportedFields = getActualSupportedFields(context)

        return ContentValues().apply {
            // 基础字段（所有设备都支持）- 安全插入
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.ADDRESS, config.phoneNumber)
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.BODY, config.content)
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.DATE, config.timestamp)
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.TYPE, config.smsType.value)
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.READ, if (config.read) 1 else 0)

            // 发送时间（对于发出的短信）
            if (config.smsType.isOutgoing) {
                putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.DATE_SENT, config.timestamp)
            }

            // 状态设置
            val status = when (config.smsType) {
                SmsType.FAILED -> SmsConstants.SMS_STATUS_FAILED
                SmsType.SENT -> SmsConstants.SMS_STATUS_COMPLETE
                else -> SmsConstants.SMS_STATUS_NONE
            }
            putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.STATUS, status)

            // 会话ID（如果提供且支持）
            config.threadId?.let {
                putIfSupported(this, actualSupportedFields, Telephony.TextBasedSmsColumns.THREAD_ID, it)
            }

            // SIM卡字段设置（只插入设备实际支持的字段）
            putSimFieldsSafely(this, actualSupportedFields, config)

            Log.d(TAG, "创建短信记录: type=${config.smsType.displayName}, " +
                    "number=${config.phoneNumber}, time=${config.timestamp}")
        }
    }

    /**
     * 安全地插入字段（仅当字段存在时才插入）
     */
    private fun putIfSupported(values: ContentValues, supportedFields: Set<String>, field: String, value: Any?) {
        if (supportedFields.contains(field)) {
            try {
                when (value) {
                    is String -> values.put(field, value)
                    is Int -> values.put(field, value)
                    is Long -> values.put(field, value)
                    is Boolean -> values.put(field, if (value) 1 else 0)
                    null -> values.putNull(field)
                }
            } catch (e: Exception) {
                Log.w(TAG, "插入字段失败: $field = $value, ${e.message}")
            }
        } else {
            Log.v(TAG, "字段不存在，跳过: $field")
        }
    }

    /**
     * 安全地设置SIM卡相关字段
     * 策略：1. 检查设备实际支持的字段 2. 尝试已知可能的字段名 3. 捕获异常
     */
    private fun putSimFieldsSafely(
        values: ContentValues,
        supportedFields: Set<String>,
        config: SmsGenerationConfig
    ) {
        val simValue = config.simSlot
        val manufacturer = Build.MANUFACTURER.lowercase()

        // 根据厂商确定SIM字段优先级
        val simFieldCandidates = when (manufacturer) {
            "xiaomi", "redmi" -> listOf("sub_id") // 小米只支持sub_id
            "vivo", "iqoo" -> listOf("sub_id", "subscription_id", "sim_id")
            "oppo", "realme", "oneplus" -> listOf("sub_id", "oppo_sub_id")
            "huawei", "honor" -> listOf("sub_id", "hw_sub_id")
            "samsung" -> listOf("sub_id")
            else -> listOf("sub_id", "subscription_id", "sim_id", "sim_slot")
        }

        var simFieldSet = false

        for (field in simFieldCandidates) {
            if (supportedFields.contains(field)) {
                try {
                    values.put(field, simValue)
                    Log.d(TAG, "设置SIM字段成功: $field = $simValue")
                    simFieldSet = true
                    // 部分设备支持多个字段，继续尝试其他字段
                } catch (e: Exception) {
                    Log.w(TAG, "设置SIM字段失败: $field, ${e.message}")
                }
            }
        }

        if (!simFieldSet) {
            Log.w(TAG, "设备不支持任何已知的SIM字段，短信将不显示SIM卡信息")
        }

        // 尝试设置其他可选字段（不影响主要功能）
        putOptionalFields(values, supportedFields, config)
    }

    /**
     * 设置可选字段（厂商特定）
     */
    private fun putOptionalFields(
        values: ContentValues,
        supportedFields: Set<String>,
        config: SmsGenerationConfig
    ) {
        val optionalFields = mapOf(
            "seen" to (if (config.read) 1 else 0),
            "deletable" to 1,
            "favorite" to 0,
            "block_type" to 0,
            "hw_priority" to 0
        )

        optionalFields.forEach { (field, value) ->
            if (supportedFields.contains(field)) {
                try {
                    values.put(field, value)
                } catch (e: Exception) {
                    Log.d(TAG, "可选字段设置失败: $field")
                }
            }
        }
    }

    /**
     * 批量生成短信记录
     * 防御性编程：带事务处理和错误恢复
     */
    fun generateSmsRecords(
        context: Context,
        configs: List<SmsGenerationConfig>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            var successCount = 0
            val contentResolver = context.contentResolver

            configs.forEachIndexed { index, config ->
                try {
                    val values = createSmsValues(config, context)

                    // 尝试使用特定URI插入
                    val specificUri = Uri.parse(config.smsType.getUri())
                    var resultUri = contentResolver.insert(specificUri, values)

                    // 如果特定URI失败，尝试使用主URI
                    if (resultUri == null) {
                        Log.w(TAG, "特定URI插入失败: $specificUri，尝试主URI")
                        val fallbackUri = Uri.parse(config.smsType.getFallbackUri())
                        resultUri = contentResolver.insert(fallbackUri, values)
                    }

                    if (resultUri != null) {
                        successCount++
                        Log.d(TAG, "插入成功: $resultUri")
                    } else {
                        Log.w(TAG, "插入失败: 返回null")
                    }

                    onProgress?.invoke(index + 1, configs.size)

                } catch (e: Exception) {
                    Log.e(TAG, "单条短信插入失败: ${e.message}", e)
                    // 继续处理下一条（防御性：不中断批量操作）
                }
            }

            Log.i(TAG, "批量生成完成: $successCount/${configs.size}")
            Result.success(successCount)

        } catch (e: SecurityException) {
            Log.e(TAG, "权限不足: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "批量生成异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 生成随机短信内容
     */
    fun generateRandomContent(): String {
        return SmsConstants.SMS_TEMPLATES.random()
    }

    /**
     * 生成对话式短信内容（用于模拟真实对话）
     */
    fun generateConversationContent(index: Int, total: Int, isOutgoing: Boolean): String {
        val outgoing = listOf(
            "在吗？", "明天有空吗？", "好的，谢谢！", "晚上一起吃饭？",
            "文件发你了，看一下", "收到了吗？", "没问题", "明天几点？"
        )
        val incoming = listOf(
            "在的，怎么了？", "有空，什么事？", "不客气", "好啊，去哪吃？",
            "收到了，正在看", "收到了，谢谢", "好的", "晚上7点吧"
        )

        return if (isOutgoing) {
            outgoing.getOrElse(index % outgoing.size) { generateRandomContent() }
        } else {
            incoming.getOrElse(index % incoming.size) { generateRandomContent() }
        }
    }

    /**
     * 清除字段缓存（用于测试或重新检测）
     */
    fun clearFieldCache() {
        cachedSupportedFields = null
        cachedManufacturer = null
        Log.d(TAG, "字段缓存已清除")
    }

    /**
     * 获取当前缓存的字段信息（用于调试）
     */
    fun getCachedFieldsInfo(): String {
        return "厂商: $cachedManufacturer, 字段数: ${cachedSupportedFields?.size ?: 0}, " +
                "字段: ${cachedSupportedFields?.joinToString() ?: "未缓存"}"
    }
}