package com.uselesswater.multicallloggeneration

/**
 * 应用常量类，用于集中管理所有硬编码的常量
 */
object Constants {

    // 权限相关
    const val PERMISSION_READ_CALL_LOG = android.Manifest.permission.READ_CALL_LOG
    const val PERMISSION_WRITE_CALL_LOG = android.Manifest.permission.WRITE_CALL_LOG
    const val PERMISSION_READ_PHONE_STATE = android.Manifest.permission.READ_PHONE_STATE
    const val PERMISSION_READ_PHONE_NUMBERS = android.Manifest.permission.READ_PHONE_NUMBERS
    // 日志标签
    const val TAG_MAIN_ACTIVITY = "MainActivity"
    const val TAG_DEBUG_CALL_LOG = "DebugCallLog"
    const val TAG_SIM_ADAPTER = "SIMAdapter"

    // 内容提供者URI
    const val CALL_LOG_URI = "content://call_log/calls"
    const val CALL_LOG_SORT_ORDER = "date DESC"

    const val SIM_OPTION_SIM1 = "SIM 1"
    const val SIM_OPTION_SIM2 = "SIM 2"

    // 时间范围配置
    const val TIME_RANGE_SHORT_MIN = 1
    const val TIME_RANGE_SHORT_MAX = 30
    const val TIME_RANGE_MEDIUM_MIN = 30
    const val TIME_RANGE_MEDIUM_MAX = 60
    const val TIME_RANGE_LONG_MIN = 60
    const val TIME_RANGE_LONG_MAX = 300
    const val TIME_RANGE_CUSTOM_MIN = 1
    const val TIME_RANGE_CUSTOM_MAX = 3000

    const val TIME_RANGE_SHORT_NAME = "1秒-30秒"
    const val TIME_RANGE_MEDIUM_NAME = "30秒-1分钟"
    const val TIME_RANGE_LONG_NAME = "1分钟-5分钟"
    const val TIME_RANGE_CUSTOM_NAME = "自定义时长"

    // 通话间隔时间（毫秒）
    const val CALL_INTERVAL_MIN = 40
    const val CALL_INTERVAL_MAX = 120
    const val MILLISECONDS_PER_SECOND = 1000L

    // 界面文本
    const val APP_TITLE = "📞 李哥通话记录生成器"
    const val DEFAULT_MESSAGE = "本工具为爱电访的「空中的裤头·李」推出！\n请节制使用！"
    const val PHONE_NUMBER_POOL_TITLE = "电话号码池"
    const val PHONE_NUMBER_PLACEHOLDER = "例如：\n13800138000\n13900139000\n13700137000"
    const val PHONE_NUMBER_LABEL = "每行一个号码"
    const val TIME_SETTINGS_TITLE = "时间设置"
    const val START_TIME_LABEL = "起始时间："
    const val DATE_BUTTON_TEXT = "选择日期"
    const val TIME_BUTTON_TEXT = "选择时间"

    const val END_TIME_LABEL = "截止时间: "

    const val END_TIME_CHECKBOX_LABEL = "截止时间（可选）"

    const val END_TIME_DEFAULT_OFFSET_HOURS = 1L // 默认截止时间为起始时间后24小时

    const val TIME_RANGE_ERROR = "⚠️ 截止时间必须晚于起始时间"
    const val CALL_DURATION_LABEL = "通话时长："
    const val SIM_SELECTION_TITLE = "SIM卡选择"
    const val GENERATE_BUTTON_TEXT = "🚀 批量生成通话记录"

    const val CHECK_UPDATE_BUTTON_TEXT = "🔄 检查更新"
    const val AUTHOR_INFO = "Love 空中的裤头·李~"

    // ========== 弹窗和提示消息常量 ==========

    // 权限相关
    const val ERROR_PERMISSION_DENIED = "❌ 生成失败: 权限不足。"
    const val PERMISSION_GRANTED = "✅ 已获得所有必要权限"
    const val PERMISSION_PARTIAL = "⚠️ 部分权限未授予，功能可能受限"
    const val ERROR_CALL_LOG_PERMISSION_REQUIRED = "⚠️ 需要授予通话记录权限才能生成记录"

    // 生成相关
    const val ERROR_NO_PHONE_NUMBERS = "⚠️ 请至少输入一个电话号码"
    const val ERROR_GENERATION_FAILED = "❌ 生成失败: "
    const val ERROR_SIM_NOT_FOUND = "⚠️ 无法找到选择的SIM卡 (SIM %d)。请检查SIM卡状态和权限。"
    const val SUCCESS_GENERATION = "✅ 成功生成 %d 条通话记录！"

    // 识别相关
    const val SUCCESS_RECOGNITION_ADD_NUMBERS = "✅ 识别成功，添加 %d 个号码"
    const val SUCCESS_GENERATION_ADD_NUMBERS = "✅ 生成成功，添加 %d 个号码"

    // 输入验证
    const val ERROR_MIN_DURATION_GREATER_THAN_MAX = "⚠️ 最小时长不能大于最大时长"
    const val ERROR_MAX_RECORDS_EXCEEDED = "⚠️ 一次最多生成1000条通话记录，当前：%d条"

    // 更新检查
    const val UPDATE_NO_UPDATE_AVAILABLE = "✅ 已是最新版本"
    const val UPDATE_CHECK_FAILED = "❌ 检查更新失败"
    const val UPDATE_PRE_RELEASE_WARNING = "⚠️ 预发布版本"

    // 下载相关
    const val DOWNLOAD_CANCELLED = "下载已取消"
    const val DOWNLOAD_FAILED = "下载失败"

    // 日期时间格式
    const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm"

    // SIM卡调试字段
    val POSSIBLE_SIM_FIELDS = arrayOf(
        "subscription_id", "sub_id", "sim_id", "simid",
        "slot_id", "sim_slot", "phone_id", "account_id", "sim_name",
        "account_name", "subscription_component_name", "phone_account_id",
        "sim_index", "sim_number", "sim_slot_index"
    )

    const val DEFAULT_SIM_SLOT_INDEX_OFFSET = 1

    // 通话类型常量
    const val CALL_TYPE_INCOMING = 1
    const val CALL_TYPE_OUTGOING = 2
    const val CALL_TYPE_MISSED = 3
    const val CALL_TYPE_REJECTED = 5
    // 呼出未接通（支持该功能的所有设备通用），使用6作为自定义类型值
    const val CALL_TYPE_OUTGOING_UNANSWERED = 6
    // vivo设备呼出未接通，使用2作为类型值
    const val CALL_TYPE_VIVO_OUTGOING_UNANSWERED = 2

    const val FIELD_MISSED_REASON = "missed_reason"
    const val FIELD_IS_REJECTED = "is_rejected"

    // 默认值
    const val DEFAULT_RING_DURATION = 15
}

// ========== 短信相关常量 ==========
object SmsConstants {
    // 权限相关
    const val PERMISSION_READ_SMS = android.Manifest.permission.READ_SMS
    const val PERMISSION_SEND_SMS = android.Manifest.permission.SEND_SMS
    const val PERMISSION_RECEIVE_SMS = android.Manifest.permission.RECEIVE_SMS

    // ContentProvider URI
    const val SMS_URI = "content://sms"
    const val SMS_INBOX_URI = "content://sms/inbox"
    const val SMS_SENT_URI = "content://sms/sent"
    const val SMS_DRAFT_URI = "content://sms/draft"

    // 短信类型（Telephony.TextBasedSmsColumns）
    const val SMS_TYPE_INBOX = 1      // 接收
    const val SMS_TYPE_SENT = 2       // 发送
    const val SMS_TYPE_DRAFT = 3      // 草稿
    const val SMS_TYPE_OUTBOX = 4     // 发件箱（发送中）
    const val SMS_TYPE_FAILED = 5     // 发送失败
    const val SMS_TYPE_QUEUED = 6     // 队列中

    // 短信状态
    const val SMS_STATUS_NONE = -1
    const val SMS_STATUS_COMPLETE = 0
    const val SMS_STATUS_PENDING = 32
    const val SMS_STATUS_FAILED = 64

    // 读取状态
    const val SMS_READ = 1
    const val SMS_UNREAD = 0

    // 界面文本
    const val SMS_SCREEN_TITLE = "📨 李哥短信记录生成器"
    const val SMS_DEFAULT_MESSAGE = "短信记录生成工具\n请节制使用！"

    const val SMS_PHONE_LABEL = "电话号码池"
    const val SMS_CONTENT_LABEL = "短信内容"
    const val SMS_CONTENT_PLACEHOLDER = "请输入短信内容，或留空使用随机内容..."
    const val SMS_GENERATE_BUTTON_TEXT = "🚀 批量生成短信记录"

    // 错误提示
    const val ERROR_NOT_DEFAULT_SMS_APP = "⚠️ 需要设为默认短信应用才能写入记录"
    const val ERROR_SMS_PERMISSION_REQUIRED = "⚠️ 需要短信权限"
    const val ERROR_NO_SMS_CONTENT = "⚠️ 请至少输入一条短信内容或手机号"
    const val SUCCESS_SMS_GENERATION = "✅ 成功生成 %d 条短信记录！"
    const val ERROR_RESTORE_DEFAULT_SMS = "⚠️ 请手动恢复默认短信应用"

    // 短信内容模板（用于随机生成）
    val SMS_TEMPLATES = listOf(
        "您好，请问明天有空吗？",
        "收到，谢谢！",
        "快递已放到门口，请查收。",
        "晚上一起吃饭吗？",
        "文件已发送，请查收。",
        "好的，没问题。",
        "明天上午10点开会，请准时参加。",
        "验证码：123456，请勿泄露。",
        "您的订单已发货，请注意查收。",
        "周末有空出来玩吗？"
    )

    // 短信间隔时间（毫秒）
    const val SMS_INTERVAL_MIN = 30 * 1000L    // 30秒
    const val SMS_INTERVAL_MAX = 5 * 60 * 1000L // 5分钟
}