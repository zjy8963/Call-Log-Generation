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
import java.net.URLEncoder

/**
 * 百度OCR管理器 - 作为豆包OCR的兜底方案
 * 使用百度通用文字识别（高精度版）API
 */
object BaiduOcrManager {

    private const val TAG = "BaiduOcrManager"

    // ==================== 配置区域（必须修改）====================
    // API Key 和 Secret Key：在百度智能云控制台创建应用后获取
    private val API_KEY = BuildConfig.BAIDU_API_KEY
    private const val SECRET_KEY = BuildConfig.BAIDU_SECRET_KEY

    // 通用文字识别（高精度版）API地址
    private const val OCR_API_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"
    // 获取Access Token的地址
    private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    // ============================================================

    // 缓存access_token，避免频繁获取
    private var cachedAccessToken: String? = null
    private var tokenExpireTime: Long = 0

    /**
     * 识别图片中的手机号
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun recognizePhoneNumbers(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始百度OCR处理图片...")

            val base64Image = compressImageToBase64(context, imageUri)
                ?: return@withContext emptyList()

            Log.d(TAG, "图片编码完成，大小: ${base64Image.length} chars")

            val accessToken = getAccessToken()
                ?: return@withContext emptyList()

            val response = callBaiduOcrApi(base64Image, accessToken)
            parsePhoneNumbers(response)

        } catch (e: Exception) {
            Log.e(TAG, "百度OCR识别失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 获取Access Token（带缓存机制）
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        // 检查缓存的token是否还有效（预留5分钟缓冲）
        val currentTime = System.currentTimeMillis()
        if (cachedAccessToken != null && currentTime < tokenExpireTime - 5 * 60 * 1000) {
            Log.d(TAG, "使用缓存的AccessToken")
            return@withContext cachedAccessToken
        }

        try {
            val url = URL("$TOKEN_URL?grant_type=client_credentials&client_id=$API_KEY&client_secret=$SECRET_KEY")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
            }

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("获取Token失败 HTTP $responseCode: $error")
            }

            val json = JSONObject(response)
            val accessToken = json.getString("access_token")
            val expiresIn = json.getLong("expires_in") // 通常是2592000秒（30天）

            // 缓存token
            cachedAccessToken = accessToken
            tokenExpireTime = currentTime + expiresIn * 1000

            Log.d(TAG, "获取AccessToken成功，有效期${expiresIn}秒")
            accessToken

        } catch (e: Exception) {
            Log.e(TAG, "获取AccessToken失败: ${e.message}", e)
            null
        }
    }

    /**
     * 调用百度OCR API
     */
    private fun callBaiduOcrApi(base64Image: String, accessToken: String): String {
        val url = URL("$OCR_API_URL?access_token=$accessToken")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }

        // 对base64图片进行URL编码
        val encodedImage = URLEncoder.encode(base64Image, "UTF-8")
        val params = "image=$encodedImage"

        connection.outputStream.use { it.write(params.toByteArray()) }

        val responseCode = connection.responseCode
        val response = if (responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("百度OCR API错误 HTTP $responseCode: $error")
        }

        Log.d(TAG, "百度OCR响应: ${response.take(200)}...")
        return response
    }

    /**
     * 压缩图片并转为Base64（参考DoubaoOcrManager的实现）
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

            // 计算 inSampleSize
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

            // 应用旋转
            if (rotation != 0) {
                val matrix = android.graphics.Matrix()
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

            // 压缩为 JPEG（百度要求base64编码后不超过4M）
            val outputStream = java.io.ByteArrayOutputStream()
            val quality = if (finalBitmap!!.width > 512) 80 else 85
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            finalBitmap.recycle()

            // 检查大小是否符合要求（百度要求base64后不超过4M，原始图片建议不超过3M）
            if (bytes.size > 3 * 1024 * 1024) {
                Log.w(TAG, "图片过大: ${bytes.size / 1024}KB，尝试进一步压缩")
                // 可以在这里添加二次压缩逻辑
            }

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
     * 解析响应提取手机号
     */
    private fun parsePhoneNumbers(response: String): List<String> {
        return try {
            val json = JSONObject(response)

            // 检查错误码
            if (json.has("error_code")) {
                val errorCode = json.getInt("error_code")
                if (errorCode != 0) {
                    val errorMsg = json.optString("error_msg", "未知错误")
                    Log.e(TAG, "百度OCR返回错误: $errorCode - $errorMsg")
                    return emptyList()
                }
            }

            val wordsResults = json.getJSONArray("words_result")
            val allText = StringBuilder()

            for (i in 0 until wordsResults.length()) {
                val words = wordsResults.getJSONObject(i).getString("words")
                allText.append(words).append(" ")
            }

            val fullText = allText.toString()
            Log.d(TAG, "百度OCR识别文本: $fullText")

            extractPhoneNumbers(fullText)

        } catch (e: Exception) {
            Log.e(TAG, "解析百度OCR响应失败", e)
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