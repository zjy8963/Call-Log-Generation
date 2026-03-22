package com.uselesswater.multicallloggeneration

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 手机号段数据仓库
 * 从assets/phonetmp.csv加载数据
 */
class PhoneNumberRepository(private val context: Context) {

    companion object {
        private const val TAG = "PhoneNumberRepository"
        private const val CSV_FILE = "phonetmp.csv"
        private const val DEFAULT_PROVINCE = "北京"
        private const val DEFAULT_CITY = "北京"
    }

    // 内存缓存
    private var phoneDataCache: List<PhonePrefixInfo>? = null

    /**
     * 加载CSV数据（带缓存）
     */
    suspend fun loadPhoneData(): List<PhonePrefixInfo> = withContext(Dispatchers.IO) {
        phoneDataCache?.let { return@withContext it }

        try {
            val data = mutableListOf<PhonePrefixInfo>()
            context.assets.open(CSV_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isBlank()) return@forEach

                        val parts = line.split(",")
                        if (parts.size >= 4) {
                            data.add(
                                PhonePrefixInfo(
                                    prefix = parts[0].trim(),
                                    province = parts[1].trim(),
                                    city = parts[2].trim(),
                                    operator = parts[3].trim()
                                )
                            )
                        }
                    }
                }
            }

            phoneDataCache = data
            Log.i(TAG, "成功加载 ${data.size} 条号段数据")
            data
        } catch (e: Exception) {
            Log.e(TAG, "加载CSV失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取所有省份列表（去重排序）
     */
    suspend fun getProvinces(): List<String> = withContext(Dispatchers.Default) {
        loadPhoneData().map { it.province }.distinct().sorted()
    }

    /**
     * 获取指定省份的所有城市
     */
    suspend fun getCitiesByProvince(province: String): List<String> = withContext(Dispatchers.Default) {
        loadPhoneData()
            .filter { it.province == province }
            .map { it.city }
            .distinct()
            .sorted()
    }

    /**
     * 获取指定城市的所有可用号段
     */
    suspend fun getPrefixesByCity(city: String): List<PhonePrefixInfo> = withContext(Dispatchers.Default) {
        loadPhoneData().filter { it.city == city }
    }

    /**
     * 获取指定省份其他城市的号段（排除指定城市）
     */
    suspend fun getPrefixesByProvinceExcludingCity(province: String, excludeCity: String): List<PhonePrefixInfo> =
        withContext(Dispatchers.Default) {
            loadPhoneData().filter { it.province == province && it.city != excludeCity }
        }

    /**
     * 获取默认地区数据（山西太原）
     */
    suspend fun getDefaultLocation(): Pair<String, String> = withContext(Dispatchers.Default) {
        val data = loadPhoneData()
        val hasShanxi = data.any { it.province == DEFAULT_PROVINCE }

        if (hasShanxi) {
            DEFAULT_PROVINCE to DEFAULT_CITY
        } else {
            // 如果没有山西数据，使用第一条数据的地区
            data.firstOrNull()?.let { it.province to it.city } ?: (DEFAULT_PROVINCE to DEFAULT_CITY)
        }
    }

    /**
     * 检查数据是否已加载
     */
    fun isDataLoaded(): Boolean = phoneDataCache != null
}