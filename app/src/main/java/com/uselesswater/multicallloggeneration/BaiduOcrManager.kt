package com.uselesswater.multicallloggeneration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 百度OCR管理器 - 稳定可用，每日5万免费额度
 * 申请地址：https://ai.baidu.com/tech/ocr/general
 */
object BaiduOcrManager {

    private const val TAG = "BaiduOcrManager"

    // ==================== 配置区域（必须修改）====================
    // 在百度AI开放平台获取：https://ai.baidu.com
    private const val API_KEY = "你的API Key"
    private const val SECRET_KEY = "你的Secret Key"
    // ============================================================

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0

    /**
     * 识别图片中的手机号
     */
    suspend fun recognizePhoneNumbers(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始百度OCR识别...")

            // 获取token
            val token = getAccessToken() ?: run {
                Log.e(TAG, "获取access token失败")
                return@withContext emptyList()
            }

            // 压缩并编码图片
            val base64Image = compressImageToBase64(context, imageUri) ?: run {
                Log.e(TAG, "图片处理失败")
                return@withContext emptyList()
            }

            Log.d(TAG, "图片编码完成，大小: ${base64Image.length}")

            // 调用百度OCR API
            val url = URL("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=$token")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
            }

            // 发送数据
            val postData = "image=${URLEncoder.encode(base64Image, "UTF-8")}" +
                    "&detect_direction=true" +
                    "&probability=false"

            connection.outputStream.use { it.write(postData.toByteArray()) }

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("HTTP $responseCode: $error")
            }

            Log.d(TAG, "百度OCR响应: ${response.take(300)}")

            parsePhoneNumbers(response)

        } catch (e: Exception) {
            Log.e(TAG, "百度OCR识别失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 获取百度Access Token（带缓存）
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        // 检查缓存
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            Log.d(TAG, "使用缓存的access token")
            return@withContext accessToken
        }

        try {
            val url = URL("https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=$API_KEY&client_secret=$SECRET_KEY")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            accessToken = json.optString("access_token")
            val expiresIn = json.optLong("expires_in", 0)
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000 // 提前60秒过期

            if (accessToken.isNullOrEmpty()) {
                val error = json.optString("error_description", "未知错误")
                Log.e(TAG, "获取token失败: $error")
                return@withContext null
            }

            Log.d(TAG, "获取新access token成功，有效期${expiresIn}秒")
            accessToken

        } catch (e: Exception) {
            Log.e(TAG, "获取access token异常", e)
            null
        }
    }

    /**
     * 压缩图片并转为Base64（百度要求图片不超过4M）
     */
    private fun compressImageToBase64(context: Context, uri: Uri, maxWidth: Int = 1024, maxHeight: Int = 1024): String? {
        return try {
            // 读取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // 计算缩放比例
            var scale = 1
            while (options.outWidth / scale > maxWidth || options.outHeight / scale > maxHeight) {
                scale *= 2
            }

            // 解码图片
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inJustDecodeBounds = false
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 压缩为JPEG，质量85%，确保不超过4M
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

            // 如果还是太大，降低质量
            var quality = 85
            while (outputStream.size() > 4 * 1024 * 1024 && quality > 30) {
                outputStream.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }

            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            Log.d(TAG, "图片压缩完成: ${bitmap.width}x${bitmap.height}, 质量$quality%, Base64长度${base64.length}")

            base64

        } catch (e: Exception) {
            Log.e(TAG, "图片处理失败", e)
            null
        }
    }

    /**
     * 解析OCR响应，提取手机号
     */
    private fun parsePhoneNumbers(response: String): List<String> {
        return try {
            val json = JSONObject(response)

            // 检查错误
            if (json.has("error_code")) {
                val errorCode = json.optInt("error_code")
                val errorMsg = json.optString("error_msg", "未知错误")
                Log.e(TAG, "百度OCR返回错误: $errorCode - $errorMsg")
                return emptyList()
            }

            val wordsResult = json.optJSONArray("words_result")
            if (wordsResult == null || wordsResult.length() == 0) {
                Log.w(TAG, "OCR未识别到任何文字")
                return emptyList()
            }

            // 收集所有识别到的文字
            val allText = StringBuilder()
            for (i in 0 until wordsResult.length()) {
                val item = wordsResult.optJSONObject(i)
                val words = item?.optString("words", "") ?: ""
                if (words.isNotEmpty()) {
                    allText.append(words).append(" ")
                    Log.d(TAG, "识别到文字[$i]: $words")
                }
            }

            val text = allText.toString()
            Log.d(TAG, "合并后文字: ${text.take(200)}...")

            // 提取手机号
            extractPhoneNumbers(text)

        } catch (e: Exception) {
            Log.e(TAG, "解析OCR响应失败", e)
            emptyList()
        }
    }

    /**
     * 从文本中提取11位手机号
     */
    private fun extractPhoneNumbers(text: String): List<String> {
        // 清理文本：移除空格、横线、括号等常见分隔符
        val cleaned = text.replace(Regex("[\\s\\-()（）_——]"), "")

        // 匹配11位手机号：1开头，第二位3-9，后面9位数字
        val pattern = Regex("1[3-9]\\d{9}")
        val matches = pattern.findAll(cleaned)

        val numbers = matches.map { it.value }.distinct().toList()

        Log.d(TAG, "提取到 ${numbers.size} 个手机号: $numbers")

        return numbers
    }
}