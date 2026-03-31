package com.uselesswater.multicallloggeneration

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.lifecycle.lifecycleScope
import com.uselesswater.multicallloggeneration.analyze.DouyinParserActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 检查更新
 */
private fun MainActivity.checkForUpdate(
    includePreReleases: Boolean,
    onStart: () -> Unit,
    onResult: (UpdateResult) -> Unit
) {
    lifecycleScope.launch(Dispatchers.IO) {
        onStart()

        val updateChecker = UpdateChecker(this@checkForUpdate)
        UpdateChecker.includePreReleases.value = includePreReleases

        updateChecker.checkForUpdate { result ->
            lifecycleScope.launch(Dispatchers.Main) {
                onResult(result)
            }
        }
    }
}
/**
 * 我的页面 - 个人中心界面
 * 包含应用更新检查、作者信息等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUpdateOptions by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var includePreReleases by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 使用条件加载背景图片，避免在Composable中使用try-catch
        val bgPainter = painterResource(id = R.drawable.bg_main)
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
                text = "我的",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
// 抖音解析功能入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "工具箱",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FilledTonalButton(
                        onClick = {
                            // 导航到抖音解析页面
                            // 如果你使用 Navigation: navController.navigate("douyin_parser")
                            // 或者使用 Intent 启动新 Activity
                            val intent = android.content.Intent(context, DouyinParserActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎵 抖音链接解析")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "支持解析抖音视频、图集、实况照片，提供预览和下载功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 更新按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "应用设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FilledTonalButton(
                        onClick = { showUpdateOptions = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Constants.CHECK_UPDATE_BUTTON_TEXT)
                    }
                }
            }

            // 作者信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = Constants.AUTHOR_INFO,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    // 更新选项对话框
    if (showUpdateOptions) {
        AlertDialog(
            onDismissRequest = { showUpdateOptions = false },
            title = {
                Text("📦 更新检查选项", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column {
                    Text(
                        text = "请选择更新策略：",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = UpdateChecker.updateToLatest.value,
                            onCheckedChange = { UpdateChecker.updateToLatest.value = it }
                        )
                        Text("总是更新到最新版本", modifier = Modifier.padding(start = 8.dp))
                    }

                    Text(
                        text = "如果启用，将忽略版本新旧直接更新到最新版",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includePreReleases,
                            onCheckedChange = { includePreReleases = it }
                        )
                        Text("包含预发布版本", modifier = Modifier.padding(start = 8.dp))
                    }

                    Text(
                        text = "预发布版本可能包含新功能但不够稳定",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateOptions = false
                        val activity = context as? MainActivity
                        if (activity != null) {
                            activity.checkForUpdate(
                                includePreReleases = includePreReleases,
                                onStart = { },
                                onResult = { result ->
                                    updateResult = result
                                    showUpdateDialog = true
                                }
                            )
                        } else {
                            updateResult = UpdateResult.Error("无法获取Activity上下文")
                            showUpdateDialog = true
                        }
                    }
                ) {
                    Text("开始检查")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateOptions = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 下载进度对话框
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) {
                    showDownloadDialog = false
                }
            },
            title = {
                Text("📥 正在下载更新", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "下载进度: $downloadProgress%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "请勿关闭应用，正在下载更新文件...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(vertical = 16.dp)
                        )
                        Text(
                            text = "正在准备下载...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (isDownloading) {
                    TextButton(
                        onClick = {
                            val downloadManager = AppDownloadManager(context)
                            downloadManager.cancelDownload()
                            isDownloading = false
                            showDownloadDialog = false
                            Toast.makeText(context, Constants.DOWNLOAD_CANCELLED, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("取消下载")
                    }
                } else {
                    null
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(
                        onClick = { showDownloadDialog = false }
                    ) {
                        Text("关闭")
                    }
                } else {
                    null
                }
            }
        )
    }

    // 更新检查结果对话框
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(
                    when (updateResult) {
                        is UpdateResult.UpdateAvailable -> "📦 发现新版本"
                        is UpdateResult.NoUpdateAvailable -> Constants.UPDATE_NO_UPDATE_AVAILABLE
                        is UpdateResult.Error -> Constants.UPDATE_CHECK_FAILED
                        null -> "检查更新"
                    }
                )
            },
            text = {
                when (val result = updateResult) {
                    is UpdateResult.UpdateAvailable -> {
                        Column {
                            Text("版本: ${result.release.tagName}")
                            Text("发布日期: ${result.release.publishedAt}")
                            if (result.release.prerelease) {
                                Text(Constants.UPDATE_PRE_RELEASE_WARNING, color = Color.Yellow)
                            }

                            if (UpdateChecker.updateToLatest.value) {
                                Text("📋 更新策略: 更新到最新版本",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("📋 更新策略: 只更新到新版本",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }

                            Text("更新内容:")
                            Text(result.release.body, style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    is UpdateResult.NoUpdateAvailable -> {
                        if (UpdateChecker.updateToLatest.value) {
                            Text("🎉 恭喜！您已经运行着最新版本！")
                        } else {
                            Text("当前已是最新版本！")
                        }
                    }
                    is UpdateResult.Error -> {
                        Text("检查更新失败: ${result.message}")
                    }
                    null -> {
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                when (updateResult) {
                    is UpdateResult.UpdateAvailable -> {
                        TextButton(
                            onClick = {
                                val release = (updateResult as UpdateResult.UpdateAvailable).release
                                val apkAsset = release.assets.firstOrNull()
                                if (apkAsset != null) {
                                    val downloadManager = AppDownloadManager(context)
                                    isDownloading = true
                                    downloadProgress = 0
                                    showDownloadDialog = true
                                    showUpdateDialog = false

                                    downloadManager.downloadApkSimple(
                                        downloadUrl = apkAsset.downloadUrl,
                                        fileName = apkAsset.name,
                                        onProgress = { progress ->
                                            downloadProgress = progress
                                            Log.d("DownloadProgress", "下载进度: $progress%")
                                        },
                                        onComplete = { file ->
                                            isDownloading = false
                                            showDownloadDialog = false

                                            if (file != null) {
                                                downloadManager.installApkFile(file)
                                            } else {
                                                Toast.makeText(context, Constants.DOWNLOAD_FAILED, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        ) {
                            Text("下载更新")
                        }
                    }
                    else -> null
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
}