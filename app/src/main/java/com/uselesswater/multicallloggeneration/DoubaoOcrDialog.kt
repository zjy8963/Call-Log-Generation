package com.uselesswater.multicallloggeneration

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat  // ========== 添加这行 ==========
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.launch

enum class OcrEngine(val displayName: String, val description: String) {
    AUTO("自动识别", "优先豆包，失败时用百度兜底"),
    DOUBAO("豆包AI", "仅使用豆包AI识别"),
    BAIDU("百度OCR", "仅使用百度OCR识别")
}
/**
 * 豆包AI OCR识别对话框
 */
@RequiresApi(Build.VERSION_CODES.N)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubaoOcrDialog(
    onDismiss: () -> Unit,
    onNumbersSelected: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var numbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedEngine by remember { mutableStateOf(OcrEngine.AUTO) } // AUTO, DOUBAO, BAIDU

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
            numbers = emptyList()
            selected = emptySet()
            error = null
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val uri = saveBitmapToTemp(context, it)
            imageUri = uri
            numbers = emptyList()
            selected = emptySet()
            error = null
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePhoto.launch(null)
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("豆包AI识别")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (imageUri == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Send,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("选择包含手机号的图片")
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { pickImage.launch("image/*") }) {
                                    //Icon(Icons.Default.ArrowDropDown, null)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("相册")
                                }
                                Button(onClick = {
                                    // ========== 修复：使用 ContextCompat 检查权限 ==========
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                        == PackageManager.PERMISSION_GRANTED) {
                                        takePhoto.launch(null)
                                    } else {
                                        requestPermission.launch(Manifest.permission.CAMERA)
                                    }
                                    // =====================================================
                                }) {
                                    //Icon(Icons.Default.Add, null)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("拍照")
                                }
                            }
                        }
                    }
                } else {
                    // 在图片显示前添加引擎选择
                    Spacer(modifier = Modifier.height(8.dp))

                    var engineExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = engineExpanded,
                        onExpandedChange = { engineExpanded = !engineExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedEngine.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("识别引擎") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            supportingText = { Text(selectedEngine.description) }
                        )

                        ExposedDropdownMenu(
                            expanded = engineExpanded,
                            onDismissRequest = { engineExpanded = false }
                        ) {
                            OcrEngine.entries.forEach { engine ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(engine.displayName)
                                            Text(
                                                engine.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedEngine = engine
                                        engineExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(context)
                                .data(imageUri)
                                .build()
                        )

                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        IconButton(
                            onClick = { imageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, "重选", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (numbers.isEmpty() && !loading) {
// ========== 修改：支持豆包+百度双引擎识别（百度兜底） ==========
                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null

                                    val result = when (selectedEngine) {
                                        OcrEngine.AUTO -> {
                                            // 自动模式：豆包优先，百度兜底
                                            Log.d("DoubaoOcrDialog", "[自动模式] 尝试豆包AI...")
                                            var doubaoResult = DoubaoOcrManager.recognizePhoneNumbers(context, imageUri!!)

                                            if (doubaoResult.isEmpty()) {
                                                Log.d("DoubaoOcrDialog", "[自动模式] 豆包失败，尝试百度OCR...")
                                                doubaoResult = BaiduOcrManager.recognizePhoneNumbers(context, imageUri!!)
                                            }
                                            doubaoResult
                                        }
                                        OcrEngine.DOUBAO -> {
                                            Log.d("DoubaoOcrDialog", "[豆包模式] 仅使用豆包AI...")
                                            DoubaoOcrManager.recognizePhoneNumbers(context, imageUri!!)
                                        }
                                        OcrEngine.BAIDU -> {
                                            Log.d("DoubaoOcrDialog", "[百度模式] 仅使用百度OCR...")
                                            BaiduOcrManager.recognizePhoneNumbers(context, imageUri!!)
                                        }
                                    }

                                    if (result.isNotEmpty()) {
                                        numbers = result
                                        selected = result.toSet()
                                    } else {
                                        error = "未识别到手机号，请确保图片清晰且包含有效号码"
                                    }

                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (selectedEngine) {
                                    OcrEngine.AUTO -> "开始识别 (自动)"
                                    OcrEngine.DOUBAO -> "开始识别 (豆包)"
                                    OcrEngine.BAIDU -> "开始识别 (百度)"
                                }
                            )
                        }
// ================================================================
                    }

                    if (loading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("豆包AI分析中...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (numbers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("找到 ${numbers.size} 个号码", style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = {
                                    selected = if (selected.size == numbers.size) emptySet() else numbers.toSet()
                                }
                            ) {
                                Text(if (selected.size == numbers.size) "取消全选" else "全选")
                            }
                        }

                        Card(
                            modifier = Modifier.heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(numbers) { number ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = number in selected,
                                            onCheckedChange = { checked ->
                                                selected = if (checked) selected + number else selected - number
                                            }
                                        )
                                        Text(number, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onNumbersSelected(selected.toList()) },
                enabled = selected.isNotEmpty() && !loading
            ) {
                Text("添加 (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
        }
    )
}

private fun saveBitmapToTemp(context: android.content.Context, bitmap: android.graphics.Bitmap): Uri {
    val file = java.io.File.createTempFile("ocr_", ".jpg", context.cacheDir)
    file.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
    }
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}