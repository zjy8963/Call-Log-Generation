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

/**
 * 豆包/火山引擎OCR管理器 - OpenAI兼容格式
 */
object DoubaoOcrManager {

    private const val TAG = "DoubaoOcrManager"

    // ==================== 配置区域（必须修改）====================
    // API Key：在火山引擎控制台 "API Key管理" 创建
    private const val API_KEY = "96208841-ec85-4ba5-94fd-771d85a03b09"

    // 接入点ID：在火山引擎控制台 "在线推理" 创建的接入点ID
    // 格式如：ep-20240101-abcdefgh 或 doubao-vision-lite-4k-xxx
    private const val ENDPOINT_ID = "ep-20260315232208-nq27v"

    // API地址（北京节点）
    private const val ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
    // ============================================================

    /**
     * 识别图片中的手机号
     */
    suspend fun recognizePhoneNumbers(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始处理图片...")

            val base64Image = compressImageToBase64(context, imageUri)
                ?: return@withContext emptyList()

            Log.d(TAG, "图片编码完成，大小: ${base64Image.length} chars")

            val response = callApi(base64Image)
            parsePhoneNumbers(response)

        } catch (e: Exception) {
            Log.e(TAG, "识别失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 压缩图片并转为Base64
     */
    private fun compressImageToBase64(context: Context, uri: Uri, maxSize: Int = 1024): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val scale = if (options.outWidth > maxSize) options.outWidth / maxSize else 1

            val bitmapOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inJustDecodeBounds = false
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bitmapOptions)
            } ?: return null

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            Log.e(TAG, "图片处理失败", e)
            null
        }
    }

    /**
     * 调用火山引擎API
     */
    private fun callApi(base64Image: String): String {
        val url = URL(ENDPOINT)
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 60000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $API_KEY")
        }

        val requestBody = buildJsonRequest(base64Image)
        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = connection.responseCode
        val response = if (responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("HTTP $responseCode: $error")
        }

        Log.d(TAG, "API响应: ${response.take(200)}...")
        return response
    }

    /**
     * 构建JSON请求体
     */
    private fun buildJsonRequest(base64Image: String): String {
        val json = JSONObject().apply {
            // ========== 关键修复：使用 model 字段传入接入点ID ==========
            put("model", ENDPOINT_ID)
            // =========================================================

            put("temperature", 0.1)
            put("max_tokens", 500)

            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一个专门提取手机号的助手。请从图片中提取所有有效的中国大陆手机号（11位数字，1开头）。只返回手机号，每行一个，不要任何其他文字。如果没有找到，返回\"无\"。")
                })

                put(JSONObject().apply {
                    put("role", "user")
                    put("content", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "提取图片中的所有手机号")
                        })
                    })
                })
            })
        }

        return json.toString()
    }

    /**
     * 解析响应提取手机号
     */
    private fun parsePhoneNumbers(response: String): List<String> {
        return try {
            val json = JSONObject(response)
            val choices = json.getJSONArray("choices")

            if (choices.length() == 0) return emptyList()

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            Log.d(TAG, "AI返回: $content")

            extractPhoneNumbers(content)

        } catch (e: Exception) {
            Log.e(TAG, "解析失败", e)
            emptyList()
        }
    }

    /**
     * 从文本中提取11位手机号
     */
    private fun extractPhoneNumbers(text: String): List<String> {
        val cleaned = text
            .replace(Regex("[\\s\\-()（）]"), "")
            .replace("无", "")
            .replace("null", "")

        val regex = Regex("1[3-9]\\d{9}")
        return regex.findAll(cleaned).map { it.value }.distinct().toList()
    }
}