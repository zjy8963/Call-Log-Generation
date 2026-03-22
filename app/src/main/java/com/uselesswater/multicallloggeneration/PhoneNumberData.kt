package com.uselesswater.multicallloggeneration

/**
 * 手机号段数据模型
 */
data class PhonePrefixInfo(
    val prefix: String,      // 号段，如"1300000"
    val province: String,    // 省份，如"山东"
    val city: String,        // 城市，如"济南"
    val operator: String     // 运营商，如"联通"
)

/**
 * 生成策略配置
 */
data class GenerationStrategy(
    val primaryCity: String,           // 主要城市（如"太原"）
    val primaryProvince: String,       // 主要省份（如"山西"）
    val primaryCityRatio: Int,         // 主要城市比例（0-100）
    val sameProvinceRatio: Int,        // 同省其他城市比例（0-100）
    val randomRatio: Int,              // 完全随机比例（0-100）
    val targetCount: Int               // 目标生成数量
) {
    init {
        require(primaryCityRatio + sameProvinceRatio + randomRatio == 100) {
            "比例总和必须等于100"
        }
        require(targetCount in 1..1000) {
            "生成数量必须在1-1000之间"
        }
    }

    companion object {
        /**
         * 自动模式：75%定位城市 + 20%同省其他城市 + 5%完全随机
         */
        fun autoMode(primaryCity: String, primaryProvince: String, count: Int): GenerationStrategy {
            return GenerationStrategy(
                primaryCity = primaryCity,
                primaryProvince = primaryProvince,
                primaryCityRatio = 75,
                sameProvinceRatio = 20,
                randomRatio = 5,
                targetCount = count
            )
        }

        /**
         * 简单模式：100%指定城市
         */
        fun simpleMode(city: String, province: String, count: Int): GenerationStrategy {
            return GenerationStrategy(
                primaryCity = city,
                primaryProvince = province,
                primaryCityRatio = 100,
                sameProvinceRatio = 0,
                randomRatio = 0,
                targetCount = count
            )
        }
    }
}

/**
 * 生成结果
 */
data class GeneratedPhoneNumber(
    val number: String,
    val province: String,
    val city: String,
    val operator: String,
    val source: GenerationSource  // 来源标记
)

enum class GenerationSource {
    PRIMARY_CITY,      // 主要城市
    SAME_PROVINCE,     // 同省其他城市
    RANDOM             // 完全随机
}