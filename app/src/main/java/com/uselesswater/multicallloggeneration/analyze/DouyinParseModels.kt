package com.uselesswater.multicallloggeneration.analyze

import com.google.gson.annotations.SerializedName

/**
 * 抖音解析响应数据
 */
data class DouyinParseResponse(
    val code: Int,
    val msg: String,
    val data: DouyinVideoData?
)

/**
 * 抖音视频/图集数据
 */
data class DouyinVideoData(
    val type: String,           // video, image, live
    val title: String,
    val desc: String,
    val author: DouyinAuthor?,
    val cover: String?,
    val url: String?,           // 视频直链
    @SerializedName("video_backup")
    val videoBackup: List<String>?,
    val images: List<String>?,
    @SerializedName("live_photo")
    val livePhoto: List<LivePhoto>?,
    val music: DouyinMusic?,
    val width: Int? = null,      // 视频宽度
    val height: Int? = null,     // 视频高度
    val rawData: Any? = null
)

/**
 * 作者信息
 */
data class DouyinAuthor(
    val name: String,
    val id: Long,
    val avatar: String?
)

/**
 * 实况照片数据（Live Photo）
 */
data class LivePhoto(
    val image: String,
    val video: String
)

/**
 * 音乐信息
 */
data class DouyinMusic(
    val title: String?,
    val author: String?,
    val url: String?,
    val cover: String?
)

/**
 * 解析结果密封类
 */
sealed class ParseResult {
    object Idle : ParseResult()                    // 初始空闲状态
    data class Success(val data: DouyinVideoData) : ParseResult()
    data class Error(val message: String) : ParseResult()
    object Loading : ParseResult()
}