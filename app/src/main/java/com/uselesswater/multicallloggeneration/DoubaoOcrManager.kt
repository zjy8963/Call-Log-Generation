package com.uselesswater.multicallloggeneration

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


/**
 * 豆包/火山引擎OCR管理器 - OpenAI兼容格式
 */
object DoubaoOcrManager {

    private const val TAG = "DoubaoOcrManager"

    // ==================== 配置区域（必须修改）====================
    // API Key：在火山引擎控制台 "API Key管理" 创建
    private val API_KEY = BuildConfig.DOUBAO_API_KEY

    // 接入点ID：在火山引擎控制台 "在线推理" 创建的接入点ID
    // 格式如：ep-20240101-abcdefgh 或 doubao-vision-lite-4k-xxx
    private val ENDPOINT_ID = BuildConfig.DOUBAO_ENDPOINT_ID

    // API地址（北京节点）
    private const val ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
    // ============================================================

    /**
     * 识别图片中的手机号
     */
    @RequiresApi(Build.VERSION_CODES.N)
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
    /**
     * 压缩图片并转为Base64（修复版：支持相机照片的EXIF旋转）
     */
    /**
     * 压缩图片并转为Base64（修复相机照片旋转问题）
     */
    /**
     * 压缩图片并转为Base64（修复相机照片旋转问题）
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun compressImageToBase64(context: Context, uri: Uri, maxSize: Int = 1024): String? {
        var bitmap: android.graphics.Bitmap? = null
        var rotatedBitmap: android.graphics.Bitmap? = null
        var finalBitmap: android.graphics.Bitmap? = null

        try {
            // 读取 EXIF 旋转信息
            val rotation = getRotationFromExif(context, uri)
            Log.d(TAG, "图片旋转角度: $rotation")

            // 计算缩放比例
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }

            // 计算 inSampleSize（同时考虑宽高）
            val widthScale = options.outWidth / maxSize
            val heightScale = options.outHeight / maxSize
            val scale = if (widthScale > heightScale) widthScale else heightScale
            var inSampleSize = 1
            if (scale > 1) {
                inSampleSize = scale
            }

            Log.d(TAG, "原图尺寸: ${options.outWidth}x${options.outHeight}, 采样: $inSampleSize")

            // 解码图片
            val decodeOptions = android.graphics.BitmapFactory.Options()
            decodeOptions.inSampleSize = inSampleSize
            decodeOptions.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565

            context.contentResolver.openInputStream(uri)?.use { stream ->
                bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            if (bitmap == null) {
                Log.e(TAG, "解码图片失败")
                return null
            }

            // 应用旋转（关键修复：使用完全限定名）
            if (rotation != 0) {
                val matrix = android.graphics.Matrix()
                // 使用 setRotate 设置旋转角度
                matrix.setRotate(rotation.toFloat())

                rotatedBitmap = android.graphics.Bitmap.createBitmap(
                    bitmap!!, 0, 0,
                    bitmap!!.width, bitmap!!.height, matrix, true
                )
                bitmap!!.recycle()
                bitmap = null
            } else {
                rotatedBitmap = bitmap
            }

            // 最终缩放
            val width = rotatedBitmap!!.width
            val height = rotatedBitmap!!.height
            finalBitmap = if (width > maxSize || height > maxSize) {
                val ratio = if (width > height) {
                    maxSize.toFloat() / width
                } else {
                    maxSize.toFloat() / height
                }
                val newWidth = (width * ratio).toInt()
                val newHeight = (height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true)
            } else {
                rotatedBitmap
            }

            if (finalBitmap !== rotatedBitmap && rotatedBitmap != null) {
                rotatedBitmap.recycle()
            }

            // 压缩为 JPEG
            val outputStream = java.io.ByteArrayOutputStream()
            val quality = if (finalBitmap!!.width > 512) 80 else 85
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            finalBitmap.recycle()

            Log.d(TAG, "最终图片: ${bytes.size / 1024}KB")
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "内存不足", e)
            bitmap?.recycle()
            rotatedBitmap?.recycle()
            finalBitmap?.recycle()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "图片处理失败", e)
            bitmap?.recycle()
            rotatedBitmap?.recycle()
            finalBitmap?.recycle()
            return null
        }
    }

    /**
     * 从 EXIF 获取旋转角度
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun getRotationFromExif(context: Context, uri: Uri): Int {
        var exif: android.media.ExifInterface? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                exif = android.media.ExifInterface(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 EXIF 失败")
            return 0
        }

        if (exif == null) return 0

        val orientation = exif!!.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
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