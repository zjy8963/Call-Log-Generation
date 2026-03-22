package com.uselesswater.multicallloggeneration

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 手机号批量生成对话框
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhoneNumberGeneratorDialog(
    repository: PhoneNumberRepository,
    generator: PhoneNumberGenerator,
    locationService: LocationService,
    onDismiss: () -> Unit,
    onNumbersGenerated: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    // 数据加载状态
    var isLoading by remember { mutableStateOf(true) }
    var provinces by remember { mutableStateOf<List<String>>(emptyList()) }
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }

    // 模式选择：0-简单, 1-自动, 2-手动
    // 自动策略调整为默认选项
    var selectedMode by remember { mutableIntStateOf(1) }
    val modeOptions = listOf("简单模式", "自动策略", "手动策略")

    // 通用设置
    var selectedProvince by remember { mutableStateOf("山西") }
    var selectedCity by remember { mutableStateOf("太原") }
    var generateCount by remember { mutableStateOf("30") }

    // 手动策略设置
    var primaryRatio by remember { mutableIntStateOf(75) }
    var sameProvinceRatio by remember { mutableIntStateOf(20) }
    var randomRatio by remember { mutableIntStateOf(5) }

    // 生成状态
    var isGenerating by remember { mutableStateOf(false) }
    var generatedNumbers by remember { mutableStateOf<List<GeneratedPhoneNumber>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }

    // 定位状态
    var isLocating by remember { mutableStateOf(false) }
    var autoLocation by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 权限相关状态
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // 获取定位的挂起函数 - 修改：获取定位后重置归属地设置
    suspend fun fetchLocation() {
        isLocating = true
        autoLocation = locationService.getCurrentLocation()
        autoLocation?.let { (province, city) ->
            // 重置归属地设置为当前定位城市
            selectedProvince = province
            selectedCity = city
            // 自动模式下自动更新城市列表
            cities = repository.getCitiesByProvince(province)
        }
        isLocating = false
    }

    // 权限请求 Launcher - 必须在顶层定义，不能嵌套在函数中
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // 权限已授予，重新尝试获取定位
                scope.launch { fetchLocation() }
            }
            else -> {
                // 权限被拒绝，显示提示
                showPermissionDeniedDialog = true
            }
        }
    }

    // 检查并请求定位权限的函数 - 在 Launcher 定义之后
    fun checkAndRequestLocation() {
        when {
            locationService.checkLocationPermission() -> {
                // 已有权限，直接获取定位
                scope.launch { fetchLocation() }
            }
            activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } == true -> {
                // 需要显示权限说明
                showPermissionRationale = true
            }
            else -> {
                // 直接请求权限
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // 初始化加载数据
    LaunchedEffect(Unit) {
        provinces = repository.getProvinces()
        cities = repository.getCitiesByProvince(selectedProvince)
        isLoading = false

        // 检查权限并自动获取定位
        checkAndRequestLocation()
    }

    // 权限被拒绝的对话框
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("需要定位权限") },
            text = {
                Text(
                    "获取定位信息需要位置权限。您可以在系统设置中手动开启权限，或使用手动选择地区功能。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDeniedDialog = false
                        // 打开应用设置页面
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDeniedDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 权限说明对话框
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("为什么需要定位权限？") },
            text = {
                Text(
                    "自动获取当前位置需要访问您的位置信息。这用于智能推荐您所在地区的手机号码前缀，让生成的号码更真实。我们不会存储或上传您的位置信息。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {
                    Text("允许")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionRationale = false }
                ) {
                    Text("拒绝")
                }
            }
        )
    }

    // 省份改变时更新城市列表（仅非自动模式需要）
    LaunchedEffect(selectedProvince) {
        if (!isLoading && selectedMode != 1) {
            cities = repository.getCitiesByProvince(selectedProvince)
            if (!cities.contains(selectedCity) && cities.isNotEmpty()) {
                selectedCity = cities.first()
            }
        }
    }

    // 验证比例总和
    val ratioSum = primaryRatio + sameProvinceRatio + randomRatio
    val isRatioValid = ratioSum == 100

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "批量生成手机号",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载号段数据...")
                }
            } else if (showPreview && generatedNumbers.isNotEmpty()) {
                // 预览界面
                GeneratedNumbersPreview(
                    numbers = generatedNumbers,
                    onConfirm = {
                        onNumbersGenerated(generatedNumbers.map { it.number })
                        onDismiss()
                    },
                    onBack = { showPreview = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 模式选择 - 并排显示
                    Text(
                        "选择生成模式",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 使用 Row 让三个按钮并排显示，均分宽度
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modeOptions.forEachIndexed { index, mode ->
                            FilterChip(
                                selected = selectedMode == index,
                                onClick = { selectedMode = index },
                                label = {
                                    Text(
                                        mode,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 模式说明
                    when (selectedMode) {
                        0 -> {
                            Column {
                                Text(
                                    "简单模式：所有号码均来自您指定的城市",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isLocating) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "正在获取定位...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else if (autoLocation != null) {
                                    Text(
                                        "📍 当前定位：${autoLocation!!.first} ${autoLocation!!.second}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        1 -> {
                            Column {
                                Text(
                                    "自动策略：以定位城市为主，混入20%同省其他城市，5%完全随机",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isLocating) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "正在获取定位...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else if (autoLocation != null) {
                                    Text(
                                        "📍 当前定位：${autoLocation!!.first} ${autoLocation!!.second}（已自动应用）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        2 -> {
                            Column {
                                Text(
                                    "手动策略：自定义调整各类号码的生成比例",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isLocating) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "正在获取定位...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else if (autoLocation != null) {
                                    Text(
                                        "📍 当前定位：${autoLocation!!.first} ${autoLocation!!.second}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 地区选择 - 仅在非自动策略模式下显示
                    if (selectedMode != 1) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "归属地设置",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    // 定位按钮 - 使用 checkAndRequestLocation
                                    TextButton(
                                        onClick = { checkAndRequestLocation() },
                                        enabled = !isLocating,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isLocating) "定位中..." else "点击重置",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 省份选择
                                var provinceExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = provinceExpanded,
                                    onExpandedChange = { provinceExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedProvince,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("省份") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = provinceExpanded,
                                        onDismissRequest = { provinceExpanded = false }
                                    ) {
                                        provinces.forEach { province ->
                                            DropdownMenuItem(
                                                text = { Text(province) },
                                                onClick = {
                                                    selectedProvince = province
                                                    provinceExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 城市选择
                                var cityExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = cityExpanded,
                                    onExpandedChange = { cityExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedCity,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("城市") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = cityExpanded,
                                        onDismissRequest = { cityExpanded = false }
                                    ) {
                                        cities.forEach { city ->
                                            DropdownMenuItem(
                                                text = { Text(city) },
                                                onClick = {
                                                    selectedCity = city
                                                    cityExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 手动策略的比例滑块
                    if (selectedMode == 2) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "生成比例配置",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 主要城市比例
                                RatioSlider(
                                    label = "主要城市",
                                    value = primaryRatio,
                                    onValueChange = { newValue: Int ->
                                        val oldPrimary = primaryRatio
                                        val delta = newValue - oldPrimary

                                        if (delta != 0) {
                                            if (delta > 0) {
                                                var remaining = delta
                                                val randomDeduction = minOf(remaining, randomRatio)
                                                randomRatio -= randomDeduction
                                                remaining -= randomDeduction

                                                if (remaining > 0) {
                                                    sameProvinceRatio = max(0, sameProvinceRatio - remaining)
                                                }
                                            } else {
                                                val increase = -delta
                                                sameProvinceRatio += increase
                                            }

                                            primaryRatio = newValue

                                            val sum = primaryRatio + sameProvinceRatio + randomRatio
                                            if (sum != 100) {
                                                val diff = 100 - sum
                                                randomRatio = (randomRatio + diff).coerceIn(0, 100)
                                            }
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 同省其他城市比例
                                RatioSlider(
                                    label = "同省其他城市",
                                    value = sameProvinceRatio,
                                    onValueChange = { newValue: Int ->
                                        val newRandom = 100 - primaryRatio - newValue

                                        if (newRandom >= 0) {
                                            sameProvinceRatio = newValue
                                            randomRatio = newRandom
                                        } else {
                                            sameProvinceRatio = 100 - primaryRatio
                                            randomRatio = 0
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 完全随机比例
                                RatioSlider(
                                    label = "完全随机",
                                    value = randomRatio,
                                    onValueChange = { newValue: Int ->
                                        val newSameProvince = 100 - primaryRatio - newValue

                                        if (newSameProvince >= 0) {
                                            randomRatio = newValue
                                            sameProvinceRatio = newSameProvince
                                        } else {
                                            randomRatio = 100 - primaryRatio
                                            sameProvinceRatio = 0
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 比例验证提示
                                if (!isRatioValid) {
                                    Text(
                                        "⚠️ 比例总和必须等于100%（当前: ${ratioSum}%）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Text(
                                        "✓ 比例配置正确（总和: 100%）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 生成数量
                    OutlinedTextField(
                        value = generateCount,
                        onValueChange = {
                            if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 3)) {
                                generateCount = it
                            }
                        },
                        label = { Text("生成数量 (1-1000)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = generateCount.toIntOrNull()?.let { it < 1 || it > 1000 } ?: true
                    )
                }
            }
        },
        confirmButton = {
            if (!showPreview) {
                Button(
                    onClick = {
                        val count = generateCount.toIntOrNull() ?: 50
                        val strategy = when (selectedMode) {
                            0 -> GenerationStrategy.simpleMode(selectedCity, selectedProvince, count)
                            1 -> {
                                val (province, city) = autoLocation ?: (selectedProvince to selectedCity)
                                GenerationStrategy.autoMode(city, province, count)
                            }
                            else -> GenerationStrategy(
                                primaryCity = selectedCity,
                                primaryProvince = selectedProvince,
                                primaryCityRatio = primaryRatio,
                                sameProvinceRatio = sameProvinceRatio,
                                randomRatio = randomRatio,
                                targetCount = count
                            )
                        }

                        scope.launch {
                            isGenerating = true
                            generatedNumbers = generator.generateByStrategy(strategy)
                            isGenerating = false
                            showPreview = true
                        }
                    },
                    enabled = !isGenerating && !isLoading &&
                            generateCount.toIntOrNull()?.let { it in 1..1000 } == true &&
                            (selectedMode != 2 || isRatioValid)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("生成号码")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 比例滑块组件
 */
@Composable
private fun RatioSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value.toFloat(),
            onValueChange = { newValue: Float ->
                onValueChange(newValue.toInt())
            },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 生成结果预览界面
 */
@Composable
private fun GeneratedNumbersPreview(
    numbers: List<GeneratedPhoneNumber>,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column {
        Text(
            "生成完成！共 ${numbers.size} 个号码",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 统计信息
        val stats = numbers.groupBy { it.source }.mapValues { it.value.size }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats[GenerationSource.PRIMARY_CITY]?.let {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("主要城市: $it") }
                )
            }
            stats[GenerationSource.SAME_PROVINCE]?.let {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("同省其他: $it") }
                )
            }
            stats[GenerationSource.RANDOM]?.let {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("完全随机: $it") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 号码列表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                numbers.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { phone ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    phone.number,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${phone.city} ${phone.operator}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("返回修改")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text("确认使用")
            }
        }
    }
}