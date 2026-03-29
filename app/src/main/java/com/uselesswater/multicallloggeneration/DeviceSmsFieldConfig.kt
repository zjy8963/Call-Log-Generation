package com.uselesswater.multicallloggeneration

import android.os.Build
import android.provider.Telephony

/**
 * 短信字段设备配置管理器
 * 不同厂商对短信数据库字段支持不同
 *
 * 注意：此类现在主要用于文档和参考，实际字段检测在 SmsGenerator 中通过
 * 查询数据库表结构动态完成，比硬编码配置更可靠
 */
object DeviceSmsFieldConfig {

    private const val MANUFACTURER_VIVO = "vivo"
    private const val MANUFACTURER_XIAOMI = "xiaomi"
    private const val MANUFACTURER_OPPO = "oppo"
    private const val MANUFACTURER_HUAWEI = "huawei"
    private const val MANUFACTURER_HONOR = "honor"
    private const val MANUFACTURER_SAMSUNG = "samsung"

    /**
     * 短信字段配置
     * 仅用于参考，实际字段检测已移至 SmsGenerator
     */
    data class SmsFieldConfiguration(
        val supportedFields: List<String>,
        val simFieldMapping: Map<String, String>,
        val description: String
    )

    /**
     * 获取当前设备的短信字段配置（仅参考）
     */
    fun getCurrentDeviceConfig(): SmsFieldConfiguration {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when (manufacturer) {
            MANUFACTURER_VIVO -> getVivoConfig()
            MANUFACTURER_XIAOMI -> getXiaomiConfig()
            MANUFACTURER_OPPO -> getOppoConfig()
            MANUFACTURER_HUAWEI -> getHuaweiConfig()
            MANUFACTURER_HONOR -> getHonorConfig()
            MANUFACTURER_SAMSUNG -> getSamsungConfig()
            else -> getDefaultConfig()
        }
    }

    /**
     * 标准Android字段（所有设备都支持）
     */
    private fun getStandardFields(): List<String> = listOf(
        Telephony.TextBasedSmsColumns.ADDRESS,      // 手机号
        Telephony.TextBasedSmsColumns.BODY,         // 内容
        Telephony.TextBasedSmsColumns.DATE,         // 接收时间
        Telephony.TextBasedSmsColumns.DATE_SENT,    // 发送时间
        Telephony.TextBasedSmsColumns.READ,         // 已读状态
        Telephony.TextBasedSmsColumns.STATUS,       // 状态
        Telephony.TextBasedSmsColumns.TYPE,         // 类型
        Telephony.TextBasedSmsColumns.THREAD_ID,    // 会话ID
        Telephony.TextBasedSmsColumns.PERSON,       // 联系人ID
        Telephony.TextBasedSmsColumns.PROTOCOL,     // 协议
        Telephony.TextBasedSmsColumns.REPLY_PATH_PRESENT,
        Telephony.TextBasedSmsColumns.SUBJECT,
        Telephony.TextBasedSmsColumns.SERVICE_CENTER,
        Telephony.TextBasedSmsColumns.LOCKED,
        Telephony.TextBasedSmsColumns.ERROR_CODE
    )

    /**
     * vivo设备配置
     */
    private fun getVivoConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // vivo使用sub_id而非sim_id
                "subscription_id",
                "seen",             // 是否查看
                "deletable"         // 是否可删除
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",  // vivo映射到sub_id
                "subscription_id" to "sub_id"
            ),
            description = "vivo设备（使用sub_id字段）"
        )
    }

    /**
     * 小米设备配置
     */
    private fun getXiaomiConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // 小米使用sub_id
                "mx_status",        // 小米云服务状态
                "favorite",         // 收藏标记
                "bubble_info"       // 气泡信息
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",  // 小米映射到sub_id
                "subscription_id" to "sub_id"
            ),
            description = "小米设备（使用sub_id字段，不支持sim_id）"
        )
    }

    /**
     * OPPO设备配置
     */
    private fun getOppoConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // OPPO使用sub_id
                "oppo_sub_id",      // OPPO特有
                "block_type",       // 拦截类型
                "oppo_flags"        // OPPO标志
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",
                "subscription_id" to "oppo_sub_id"
            ),
            description = "OPPO设备（使用sub_id/oppo_sub_id字段）"
        )
    }

    /**
     * 华为设备配置
     */
    private fun getHuaweiConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // 华为使用sub_id
                "hw_sub_id",        // 华为特有
                "hw_status",        // 华为状态
                "hw_priority"       // 优先级
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",
                "subscription_id" to "hw_sub_id"
            ),
            description = "华为设备（使用sub_id/hw_sub_id字段）"
        )
    }

    /**
     * 荣耀设备配置
     */
    private fun getHonorConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",
                "hw_sub_id",
                "honor_flags"
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",
                "subscription_id" to "hw_sub_id"
            ),
            description = "荣耀设备（使用sub_id/hw_sub_id字段）"
        )
    }

    /**
     * 三星设备配置
     */
    private fun getSamsungConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // 三星使用sub_id
                "secret_mode",      // 三星安全模式
                "safe_message"      // 安全信息标记
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",
                "subscription_id" to "sub_id"
            ),
            description = "三星设备（使用sub_id字段）"
        )
    }

    /**
     * 默认配置
     */
    private fun getDefaultConfig(): SmsFieldConfiguration {
        return SmsFieldConfiguration(
            supportedFields = getStandardFields() + listOf(
                "sub_id",           // 标准Android使用sub_id
                "seen"
            ),
            simFieldMapping = mapOf(
                "sim_id" to "sub_id",
                "subscription_id" to "sub_id"
            ),
            description = "标准Android设备（使用sub_id字段）"
        )
    }

    /**
     * 检查字段是否支持（仅参考，实际检测在SmsGenerator中完成）
     */
    @Deprecated("请使用SmsGenerator.getActualSupportedFields()获取真实支持的字段")
    fun isFieldSupported(fieldName: String): Boolean {
        val config = getCurrentDeviceConfig()
        return config.supportedFields.contains(fieldName)
    }

    /**
     * 获取SIM卡字段名称（仅参考）
     */
    fun getSimFieldName(preferred: String = "sim_id"): String {
        val config = getCurrentDeviceConfig()
        return config.simFieldMapping[preferred] ?: "sub_id"
    }

    /**
     * 获取当前设备制造商
     */
    fun getManufacturer(): String = Build.MANUFACTURER.lowercase()

    /**
     * 获取推荐的SIM字段列表（按优先级排序）
     */
    fun getRecommendedSimFields(): List<String> {
        return when (getManufacturer()) {
            MANUFACTURER_VIVO -> listOf("sub_id", "subscription_id")
            MANUFACTURER_XIAOMI -> listOf("sub_id")
            MANUFACTURER_OPPO -> listOf("sub_id", "oppo_sub_id")
            MANUFACTURER_HUAWEI, MANUFACTURER_HONOR -> listOf("sub_id", "hw_sub_id")
            MANUFACTURER_SAMSUNG -> listOf("sub_id")
            else -> listOf("sub_id", "subscription_id", "sim_id")
        }
    }
}