package com.uselesswater.multicallloggeneration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 百度千帆API服务 - ERNIE-Speed-8K模型
 * API端点硬编码，API Key从local.properties读取
 */
class QianfanApiService {

    companion object {
        // 硬编码API端点
        private const val API_ENDPOINT = "https://qianfan.baidubce.com/v2/chat/completions"
        private const val MODEL_NAME = "qianfan-sug-8k"

        // 基础指令模板
        val BASE_PROMPTS = mapOf(
            "催息提醒" to "生成一条银行贷款还款提醒短信，按照还款计划包含本次应还金额和还款日期（默认是20日）",
            "验证码" to "生成一条常见的手机验证码短信，包含4-6位数字验证码，格式像真实银行或APP发送的验证码短信",
            "快递通知" to "生成一条快递取件通知短信，包含取件码、快递柜位置或驿站地址，语气正式",
            "银行通知" to "生成一条银行交易提醒短信，包含交易金额、时间、余额变动信息，格式专业",
            "预约提醒" to "生成一条银行业务预约提醒短信，包含预约时间、地点、注意事项",
            "营销短信" to "生成一条银行贷款营销短信，码上贷，包含优惠活动、限时折扣、链接等元素",
        )

        // 系统提示词
        private const val SYSTEM_PROMPT = "你是一个短信内容生成助手。请根据用户指令生成一条真实、自然的手机短信内容。" +
                "要求：1.内容简洁，符合短信长度限制；2.语气符合短信类型；3.不要包含敏感信息；4.直接返回短信正文，不要加任何解释。"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 生成短信内容
     * @param apiKey 从local.properties读取的API Key
     * @param userPrompt 用户指令
     * @return 生成的短信内容，失败返回null
     */
    suspend fun generateSmsContent(apiKey: String, userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", MODEL_NAME)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 256)
            }.toString()

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(requestBody.toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("API请求失败: ${response.code} - ${response.message}")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("响应体为空"))

                val jsonResponse = JSONObject(responseBody)

                // 解析响应
                val choices = jsonResponse.optJSONArray("choices")
                    ?: return@withContext Result.failure(IOException("API响应格式错误"))

                if (choices.length() == 0) {
                    return@withContext Result.failure(IOException("API返回空结果"))
                }

                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                    ?: return@withContext Result.failure(IOException("API响应格式错误"))

                val content = message.optString("content", "").trim()

                if (content.isEmpty()) {
                    return@withContext Result.failure(IOException("生成的内容为空"))
                }

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用基础模板生成
     */
    suspend fun generateFromTemplate(apiKey: String, templateKey: String): Result<String> {
        val prompt = BASE_PROMPTS[templateKey]
            ?: return Result.failure(IllegalArgumentException("未知模板: $templateKey"))
        return generateSmsContent(apiKey, prompt)
    }
}