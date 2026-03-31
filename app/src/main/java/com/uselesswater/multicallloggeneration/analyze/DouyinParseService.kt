package com.uselesswater.multicallloggeneration.analyze

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 抖音解析服务 - 修复链接提取问题
 */
class DouyinParseService {

    companion object {
        private const val TAG = "DouyinParseService"
        private const val BASE_URL = "https://api.bugpk.com/api/douyin"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    private val gson = Gson()

    /**
     * 解析抖音链接 - 仅需要 url 参数
     */
    suspend fun parseUrl(url: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== 开始解析 ===")
            Log.d(TAG, "输入文本: $url")

            if (url.isBlank()) {
                return@withContext ParseResult.Error("请输入抖音链接")
            }

            // 从分享文本中提取纯链接（修复版）
            val extractedUrl = extractUrlFromText(url.trim())
            Log.d(TAG, "提取后的URL: $extractedUrl")

            if (!isValidDouyinUrl(extractedUrl)) {
                Log.e(TAG, "无效的抖音链接: $extractedUrl")
                return@withContext ParseResult.Error("无效的抖音链接，请使用分享链接")
            }

            // 构建请求URL
            val encodedUrl = URLEncoder.encode(extractedUrl, "UTF-8")
            val requestUrl = "$BASE_URL?url=$encodedUrl"

            Log.d(TAG, "请求地址: $requestUrl")

            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                doInput = true
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "响应码: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "响应: ${response.take(300)}...")

                val parseResponse = gson.fromJson(response, DouyinParseResponse::class.java)

                when (parseResponse.code) {
                    200 -> {
                        if (parseResponse.data != null) {
                            Log.d(TAG, "解析成功! 类型: ${parseResponse.data.type}")
                            ParseResult.Success(parseResponse.data)
                        } else {
                            ParseResult.Error("解析成功但无数据")
                        }
                    }
                    else -> ParseResult.Error("错误(${parseResponse.code}): ${parseResponse.msg}")
                }
            } else {
                ParseResult.Error("网络请求失败，HTTP码：$responseCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "异常: ${e.message}", e)
            ParseResult.Error("网络异常: ${e.message}")
        }
    }

    /**
     * 【修复版】从分享文本中提取纯URL
     * 支持：
     * 1. 抖音短链接 v.douyin.com/xxx
     * 2. 网页链接 www.douyin.com/video/xxx
     * 3. 分享文案里的链接
     */
    private fun extractUrlFromText(text: String): String {
        // 通用正则：匹配所有抖音视频链接（修复核心）
        val douyinPattern = Regex("""https?://[^\s]+douyin[^\s]*""")

        // 找到第一个匹配的链接
        val result = douyinPattern.find(text)?.value?.trim()

        // 如果没匹配到，返回原文本（不截断）
        return result ?: text.trim()
    }

    /**
     * 验证是否为有效的抖音链接
     */
    fun isValidDouyinUrl(url: String): Boolean {
        val patterns = listOf(
            Regex("""https?://v\.douyin\.com/[A-Za-z0-9]+/?.*"""),
            Regex("""https?://www\.douyin\.com/video/\d+/?.*"""),
            Regex("""https?://www\.iesdouyin\.com/share/video/\d+/?.*""")
        )
        return patterns.any { it.matches(url) }
    }
}
