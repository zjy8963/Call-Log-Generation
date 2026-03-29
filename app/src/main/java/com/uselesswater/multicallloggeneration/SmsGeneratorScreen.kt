package com.uselesswater.multicallloggeneration

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 短信记录生成界面 - 完全统一通话记录界面设计风格
 * AI识别和号码生成调用与MainActivity相同的函数接口
 */
@RequiresApi(Build.VERSION_CODES.N)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsGeneratorScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // ========== 状态管理 ==========
    var phoneNumbersText by remember { mutableStateOf("") }
    var smsContentText by remember { mutableStateOf("") }
    var selectedSmsType by remember { mutableStateOf(SmsType.INBOX) }
    var selectedSim by remember { mutableIntStateOf(1) }

    // 时间设置
    var startTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var enableTimeRange by remember { mutableStateOf(false) }
    var endTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 3600000) }

    // UI状态
    var isGenerating by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDefaultSmsDialog by remember { mutableStateOf(false) }
    var showRestoreSmsDialog by remember { mutableStateOf(false) }
    var generationResult by remember { mutableStateOf<Result<Int>?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // ========== AI识别和号码生成对话框状态 ==========
    var showDoubaoOcr by remember { mutableStateOf(false) }
    var showPhoneGenerator by remember { mutableStateOf(false) }

    // ========== AI生成短信内容对话框状态 ==========
    var showAiGenerateDialog by remember { mutableStateOf(false) }
    var aiGenerateInstruction by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<String?>(null) }

    // ========== 与MainActivity相同的依赖实例化 ==========
    val repository = remember { PhoneNumberRepository(context) }
    val generator = remember { PhoneNumberGenerator(repository) }
    val locationService = remember { LocationService(context) }

    // AI服务
    val aiService = remember { QianfanApiService() }

    // 从BuildConfig读取API Key（需要在build.gradle中配置）
    val apiKey = remember { BuildConfig.QIANFAN_API_KEY }

    // 默认短信应用管理器
    val defaultSmsManager = remember { activity?.let { DefaultSmsManager(it) } }

    // 时间格式化
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val displayTime = remember(startTimeMillis) {
        Instant.ofEpochMilli(startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }
    val displayEndTime = remember(endTimeMillis) {
        Instant.ofEpochMilli(endTimeMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    // ========== 与MainActivity相同的号码合并逻辑 ==========
    fun mergePhoneNumbers(newNumbers: List<String>) {
        val existing = phoneNumbersText
            .split("\n", " ", ",", "，")
            .map { it.trim() }
            .filter { it.length == 11 && it.matches(Regex("\\d{11}")) }

        val merged = (existing + newNumbers).distinct()
        phoneNumbersText = merged.joinToString("\n")
    }

    // ========== 与MainActivity相同的Toast显示 ==========
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    // ========== AI生成短信内容 ==========
    fun generateAiContent(instruction: String) {
        if (instruction.isBlank()) {
            showToast("请输入生成指令")
            return
        }

        if (apiKey.isEmpty()) {
            showToast("API Key未配置，请检查local.properties")
            return
        }

        scope.launch {
            isAiGenerating = true
            val result = aiService.generateSmsContent(apiKey, instruction)
            isAiGenerating = false

            result.onSuccess { content ->
                smsContentText = content
                showAiGenerateDialog = false
                aiGenerateInstruction = ""
                selectedTemplate = null
                showToast("AI生成成功")
            }.onFailure { error ->
                showToast("生成失败: ${error.message}")
            }
        }
    }

    // 执行生成操作
    val executeGeneration: () -> Unit = {
        scope.launch {
            isGenerating = true
            progress = 0

            val phoneNumbers = phoneNumbersText
                .split("\n", " ", ",", "，")
                .map { it.trim() }
                .filter { it.length == 11 && it.matches(Regex("\\d{11}")) }

            val configs = phoneNumbers.mapIndexed { index, number ->
                val timeOffset = if (enableTimeRange && endTimeMillis > startTimeMillis) {
                    Random.nextLong(0, (endTimeMillis - startTimeMillis).coerceAtLeast(1000))
                } else {
                    index * Random.nextLong(
                        SmsConstants.SMS_INTERVAL_MIN,
                        SmsConstants.SMS_INTERVAL_MAX
                    )
                }

                val content = if (smsContentText.isNotBlank()) {
                    smsContentText
                } else {
                    SmsGenerator.generateConversationContent(
                        index,
                        phoneNumbers.size,
                        selectedSmsType.isOutgoing
                    )
                }

                SmsGenerator.SmsGenerationConfig(
                    phoneNumber = number,
                    content = content,
                    timestamp = startTimeMillis + timeOffset,
                    smsType = selectedSmsType,
                    simSlot = selectedSim,
                    read = true
                )
            }

            val result = SmsGenerator.generateSmsRecords(
                context,
                configs
            ) { current, totalCount ->
                progress = current
                total = totalCount
            }

            generationResult = result
            isGenerating = false
            showConfirmDialog = false
            showResultDialog = true
        }
    }

    // 检查权限并开始生成流程
    val startGenerationFlow: () -> Unit = {
        val phoneNumbers = phoneNumbersText
            .split("\n", " ", ",", "，")
            .map { it.trim() }
            .filter { it.length == 11 && it.matches(Regex("\\d{11}")) }

        if (phoneNumbers.isEmpty()) {
            showToast("请输入至少一个有效的11位手机号")
        } else {
            total = phoneNumbers.size

            val isDefault = defaultSmsManager?.isDefaultSmsApp() ?: false
            if (!isDefault) {
                showDefaultSmsDialog = true
            } else {
                showConfirmDialog = true
            }
        }
    }

    // ========== 背景与布局 ==========
    Box(modifier = modifier.fillMaxSize()) {
        // 背景图片 - 与通话界面完全一致
        val bgPainter: Painter = painterResource(id = R.drawable.bg_main)
        Image(
            painter = bgPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题
            Text(
                text = SmsConstants.SMS_SCREEN_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = SmsConstants.SMS_DEFAULT_MESSAGE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ========== 电话号码输入区域 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题行：左侧标题，右侧清空按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SmsConstants.SMS_PHONE_LABEL,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = { phoneNumbersText = "" }
                        ) {
                            Text("清空")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phoneNumbersText,
                        onValueChange = { phoneNumbersText = it },
                        label = { Text("每行一个号码") },
                        placeholder = { Text("13800138000\n13900139000") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = MaterialTheme.shapes.medium,
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ========== AI识别和号码生成按钮 - 调用与MainActivity相同的接口 ==========
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AI识别按钮 - 调用 DoubaoOcrDialog
                        Button(
                            onClick = { showDoubaoOcr = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI识别")
                        }

                        // 号码生成按钮 - 调用 PhoneNumberGeneratorDialog
                        Button(
                            onClick = { showPhoneGenerator = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("号码生成")
                        }
                    }
                }
            }

            // ========== AI识别对话框 - 与MainActivity相同 ==========
            if (showDoubaoOcr) {
                DoubaoOcrDialog(
                    onDismiss = { showDoubaoOcr = false },
                    onNumbersSelected = { nums ->
                        // 与MainActivity相同的合并逻辑
                        mergePhoneNumbers(nums)
                        showDoubaoOcr = false
                        // 与MainActivity相同的提示
                        showToast(String.format(Constants.SUCCESS_RECOGNITION_ADD_NUMBERS, nums.size))
                    }
                )
            }

            // ========== 号码生成对话框 - 与MainActivity相同 ==========
            if (showPhoneGenerator) {
                PhoneNumberGeneratorDialog(
                    repository = repository,
                    generator = generator,
                    locationService = locationService,
                    onDismiss = { showPhoneGenerator = false },
                    onNumbersGenerated = { numbers ->
                        // 与MainActivity相同的合并逻辑
                        mergePhoneNumbers(numbers)
                        showPhoneGenerator = false
                        // 与MainActivity相同的提示
                        showToast(String.format(Constants.SUCCESS_GENERATION_ADD_NUMBERS, numbers.size))
                    }
                )
            }

            // ========== 短信内容区域 ==========

            // ========== 短信内容区域 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "短信内容",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = { smsContentText = "" }
                        ) {
                            Text("清空")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = smsContentText,
                        onValueChange = { smsContentText = it },
                        label = { Text(SmsConstants.SMS_CONTENT_LABEL) },
                        placeholder = { Text(SmsConstants.SMS_CONTENT_PLACEHOLDER) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = MaterialTheme.shapes.medium,
                        minLines = 4
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ========== AI生成按钮 ==========
                    Button(
                        onClick = { showAiGenerateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI生成")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ========== 快捷模板按钮 - 一行三个，均匀分布 ==========
                    val templateKeys = QianfanApiService.BASE_PROMPTS.keys.toList()

                    // 第一行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templateKeys.take(3).forEach { templateKey ->
                            FilledTonalButton(
                                onClick = {
                                    if (apiKey.isNotEmpty()) {
                                        scope.launch {
                                            isAiGenerating = true
                                            val result = aiService.generateFromTemplate(apiKey, templateKey)
                                            isAiGenerating = false
                                            result.onSuccess { content ->
                                                smsContentText = content
                                                showToast("已生成: $templateKey")
                                            }.onFailure { error ->
                                                showToast("生成失败: ${error.message}")
                                            }
                                        }
                                    } else {
                                        showToast("API Key未配置")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    templateKey,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第二行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templateKeys.subList(3, 6).forEach { templateKey ->
                            FilledTonalButton(
                                onClick = {
                                    if (apiKey.isNotEmpty()) {
                                        scope.launch {
                                            isAiGenerating = true
                                            val result = aiService.generateFromTemplate(apiKey, templateKey)
                                            isAiGenerating = false
                                            result.onSuccess { content ->
                                                smsContentText = content
                                                showToast("已生成: $templateKey")
                                            }.onFailure { error ->
                                                showToast("生成失败: ${error.message}")
                                            }
                                        }
                                    } else {
                                        showToast("API Key未配置")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    templateKey,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第三行（剩余一个）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 第一个按钮
                        templateKeys.getOrNull(6)?.let { templateKey ->
                            FilledTonalButton(
                                onClick = {
                                    if (apiKey.isNotEmpty()) {
                                        scope.launch {
                                            isAiGenerating = true
                                            val result = aiService.generateFromTemplate(apiKey, templateKey)
                                            isAiGenerating = false
                                            result.onSuccess { content ->
                                                smsContentText = content
                                                showToast("已生成: $templateKey")
                                            }.onFailure { error ->
                                                showToast("生成失败: ${error.message}")
                                            }
                                        }
                                    } else {
                                        showToast("API Key未配置")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    templateKey,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }

                        // 占位，保持布局一致
                        if (templateKeys.size < 7) {
                            Box(modifier = Modifier.weight(1f))
                        }

                        // 占位，保持布局一致
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }

            // ========== AI生成短信对话框 ==========
            if (showAiGenerateDialog) {
                AlertDialog(
                    onDismissRequest = {
                        if (!isAiGenerating) {
                            showAiGenerateDialog = false
                            aiGenerateInstruction = ""
                            selectedTemplate = null
                        }
                    },
                    title = {
                        Text(
                            "AI生成短信内容",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "选择预设模板或输入自定义指令：",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 模板选择下拉框
                            var templateExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = templateExpanded,
                                onExpandedChange = { templateExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedTemplate ?: "自定义指令",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("选择模板") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(templateExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                )

                                ExposedDropdownMenu(
                                    expanded = templateExpanded,
                                    onDismissRequest = { templateExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("自定义指令") },
                                        onClick = {
                                            selectedTemplate = null
                                            aiGenerateInstruction = ""
                                            templateExpanded = false
                                        }
                                    )

                                    QianfanApiService.BASE_PROMPTS.forEach { (key, prompt) ->
                                        DropdownMenuItem(
                                            text = { Text(key) },
                                            onClick = {
                                                selectedTemplate = key
                                                aiGenerateInstruction = prompt
                                                templateExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 指令输入框
                            OutlinedTextField(
                                value = aiGenerateInstruction,
                                onValueChange = { aiGenerateInstruction = it },
                                label = { Text("生成指令") },
                                placeholder = { Text("例如：生成一条银行验证码短信...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                minLines = 3,
                                maxLines = 5,
                                enabled = !isAiGenerating
                            )

                            if (isAiGenerating) {
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "AI生成中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { generateAiContent(aiGenerateInstruction) },
                            enabled = !isAiGenerating && aiGenerateInstruction.isNotBlank()
                        ) {
                            Text("生成")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                if (!isAiGenerating) {
                                    showAiGenerateDialog = false
                                    aiGenerateInstruction = ""
                                    selectedTemplate = null
                                }
                            },
                            enabled = !isAiGenerating
                        ) {
                            Text("取消")
                        }
                    }
                )
            }

            // ========== 短信类型选择 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "短信类型",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSmsType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            SmsType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedSmsType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ========== SIM卡选择 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SIM卡选择",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var expanded by remember { mutableStateOf(false) }
                    val simOptions = listOf("SIM 1", "SIM 2")

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = simOptions[selectedSim - 1],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            simOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedSim = index + 1
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ========== 时间设置 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "时间设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 起始时间
                    Text(
                        text = "起始时间: $displayTime",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选择日期")
                        }
                        FilledTonalButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选择时间")
                        }
                    }

                    // 时间范围选项
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableTimeRange,
                            onCheckedChange = { enableTimeRange = it }
                        )
                        Text(
                            "在时间段内随机分布",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // 截止时间选择
                    if (enableTimeRange) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "截止时间: $displayEndTime",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选择日期")
                            }
                            FilledTonalButton(
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选择时间")
                            }
                        }

                        if (endTimeMillis <= startTimeMillis) {
                            Text(
                                text = "截止时间必须晚于起始时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // ========== 生成按钮 ==========
            Button(
                onClick = startGenerationFlow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isGenerating,
                shape = MaterialTheme.shapes.large
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "生成中... $progress/$total",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        Icons.Default.Email,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        SmsConstants.SMS_GENERATE_BUTTON_TEXT,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ========== 日期时间选择器对话框 ==========
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startTimeMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            val currentInstant = Instant.ofEpochMilli(startTimeMillis)
                            val selectedInstant = Instant.ofEpochMilli(selectedDate)

                            val newInstant = selectedInstant.atZone(ZoneId.systemDefault())
                                .withHour(currentInstant.atZone(ZoneId.systemDefault()).hour)
                                .withMinute(currentInstant.atZone(ZoneId.systemDefault()).minute)
                                .toInstant()

                            startTimeMillis = newInstant.toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentTime = Instant.ofEpochMilli(startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间", style = MaterialTheme.typography.headlineSmall) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentInstant = Instant.ofEpochMilli(startTimeMillis)
                        val localDate = currentInstant.atZone(ZoneId.systemDefault()).toLocalDate()

                        val newInstant = localDate.atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()

                        startTimeMillis = newInstant.toEpochMilli()
                        showTimePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEndDatePicker) {
        val endDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endTimeMillis
        )

        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDatePickerState.selectedDateMillis?.let { selectedDate ->
                            val currentInstant = Instant.ofEpochMilli(endTimeMillis)
                            val selectedInstant = Instant.ofEpochMilli(selectedDate)

                            val newInstant = selectedInstant.atZone(ZoneId.systemDefault())
                                .withHour(currentInstant.atZone(ZoneId.systemDefault()).hour)
                                .withMinute(currentInstant.atZone(ZoneId.systemDefault()).minute)
                                .toInstant()

                            endTimeMillis = newInstant.toEpochMilli()
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    if (showEndTimePicker) {
        val currentEndTime = Instant.ofEpochMilli(endTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val endTimePickerState = rememberTimePickerState(
            initialHour = currentEndTime.hour,
            initialMinute = currentEndTime.minute
        )

        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("选择截止时间", style = MaterialTheme.typography.headlineSmall) },
            text = { TimePicker(state = endTimePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentInstant = Instant.ofEpochMilli(endTimeMillis)
                        val localDate = currentInstant.atZone(ZoneId.systemDefault()).toLocalDate()

                        val newInstant = localDate.atTime(endTimePickerState.hour, endTimePickerState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()

                        endTimeMillis = newInstant.toEpochMilli()
                        showEndTimePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 各种对话框 ==========
    if (showDefaultSmsDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultSmsDialog = false },
            title = { Text("需要切换默认短信应用") },
            text = {
                Text("由于Android系统限制，写入短信记录需要 temporarily 将本应用设为默认短信应用。\n\n" +
                        "操作完成后会自动恢复原来的默认应用，不会影响您的正常使用。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDefaultSmsDialog = false
                        defaultSmsManager?.saveCurrentDefaultSmsApp()
                        defaultSmsManager?.requestDefaultSmsApp { granted ->
                            if (granted) {
                                showConfirmDialog = true
                            }
                        }
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultSmsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showConfirmDialog) {
        val phoneNumbers = remember(phoneNumbersText) {
            phoneNumbersText.split("\n", " ", ",", "，")
                .map { it.trim() }
                .filter { it.length == 11 && it.matches(Regex("\\d{11}")) }
        }

        AlertDialog(
            onDismissRequest = { if (!isGenerating) showConfirmDialog = false },
            title = { Text("确认生成", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "将生成 ${phoneNumbers.size} 条短信记录",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "• 类型: ${selectedSmsType.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "• SIM卡: SIM $selectedSim",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "• 时间: $displayTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (enableTimeRange) {
                        Text(
                            "• 截止时间: $displayEndTime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (smsContentText.isNotEmpty()) {
                        Text(
                            "• 内容: ${smsContentText.take(20)}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "• 内容: 随机生成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = executeGeneration,
                    enabled = !isGenerating
                ) {
                    Text("确认生成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isGenerating) showConfirmDialog = false },
                    enabled = !isGenerating
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    if (generationResult?.isSuccess == true) "生成成功" else "生成失败",
                    color = if (generationResult?.isSuccess == true)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            },
            text = {
                generationResult?.let { result ->
                    if (result.isSuccess) {
                        Text("成功生成 ${result.getOrDefault(0)} 条短信记录")
                    } else {
                        Text("失败原因: ${result.exceptionOrNull()?.message}")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResultDialog = false
                        val isDefault = defaultSmsManager?.isDefaultSmsApp() ?: false
                        if (isDefault) {
                            defaultSmsManager?.restoreDefaultSmsApp { success ->
                                if (!success) {
                                    showRestoreSmsDialog = true
                                }
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }

    if (showRestoreSmsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreSmsDialog = false },
            title = { Text("恢复默认短信应用") },
            text = {
                Text("为避免影响正常短信功能，请手动将系统短信应用设为默认。\n\n" +
                        "设置 → 应用 → 默认应用 → 短信应用")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreSmsDialog = false
                        defaultSmsManager?.openDefaultAppsSettings()
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreSmsDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}