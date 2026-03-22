package com.uselesswater.multicallloggeneration

import android.util.Log
import kotlin.random.Random

/**
 * 手机号生成器
 */
class PhoneNumberGenerator(private val repository: PhoneNumberRepository) {

    companion object {
        private const val TAG = "PhoneNumberGenerator"
        private const val NUMBER_LENGTH = 11  // 手机号总长度
        private const val PREFIX_LENGTH = 7   // 号段长度（如1300000）
        private const val SUFFIX_LENGTH = 4   // 随机部分长度
    }

    /**
     * 根据策略批量生成手机号
     */
    suspend fun generateByStrategy(strategy: GenerationStrategy): List<GeneratedPhoneNumber> {
        val results = mutableListOf<GeneratedPhoneNumber>()
        val random = Random.Default

        // 计算各类别的数量
        val primaryCount = (strategy.targetCount * strategy.primaryCityRatio / 100.0).toInt()
        val sameProvinceCount = (strategy.targetCount * strategy.sameProvinceRatio / 100.0).toInt()
        val randomCount = strategy.targetCount - primaryCount - sameProvinceCount

        Log.d(TAG, "生成计划: 主要城市=$primaryCount, 同省其他=$sameProvinceCount, 随机=$randomCount")

        // 1. 生成主要城市号码
        if (primaryCount > 0) {
            val primaryPrefixes = repository.getPrefixesByCity(strategy.primaryCity)
            if (primaryPrefixes.isNotEmpty()) {
                repeat(primaryCount) {
                    val prefixInfo = primaryPrefixes.random()
                    results.add(
                        GeneratedPhoneNumber(
                            number = generateNumberFromPrefix(prefixInfo.prefix),
                            province = prefixInfo.province,
                            city = prefixInfo.city,
                            operator = prefixInfo.operator,
                            source = GenerationSource.PRIMARY_CITY
                        )
                    )
                }
            }
        }

        // 2. 生成同省其他城市号码
        if (sameProvinceCount > 0) {
            val otherCityPrefixes = repository.getPrefixesByProvinceExcludingCity(
                strategy.primaryProvince,
                strategy.primaryCity
            )
            if (otherCityPrefixes.isNotEmpty()) {
                repeat(sameProvinceCount) {
                    val prefixInfo = otherCityPrefixes.random()
                    results.add(
                        GeneratedPhoneNumber(
                            number = generateNumberFromPrefix(prefixInfo.prefix),
                            province = prefixInfo.province,
                            city = prefixInfo.city,
                            operator = prefixInfo.operator,
                            source = GenerationSource.SAME_PROVINCE
                        )
                    )
                }
            }
        }

        // 3. 生成完全随机号码
        if (randomCount > 0) {
            val allData = repository.loadPhoneData()
            if (allData.isNotEmpty()) {
                repeat(randomCount) {
                    val prefixInfo = allData.random()
                    results.add(
                        GeneratedPhoneNumber(
                            number = generateNumberFromPrefix(prefixInfo.prefix),
                            province = prefixInfo.province,
                            city = prefixInfo.city,
                            operator = prefixInfo.operator,
                            source = GenerationSource.RANDOM
                        )
                    )
                }
            }
        }

        // 打乱顺序，使分布更自然
        return results.shuffled()
    }

    /**
     * 根据号段生成完整手机号
     */
    private fun generateNumberFromPrefix(prefix: String): String {
        val suffix = (0 until SUFFIX_LENGTH)
            .map { Random.nextInt(0, 10) }
            .joinToString("")
        return prefix + suffix
    }

    /**
     * 验证手机号格式（11位数字）
     */
    fun validatePhoneNumber(number: String): Boolean {
        return number.length == NUMBER_LENGTH && number.all { it.isDigit() }
    }
}