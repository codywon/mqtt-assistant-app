package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.util.AppUpdateManager
import com.example.util.UpdateInfo
import com.example.viewmodel.UpdateUiState
import java.io.File
import java.util.Locale

/**
 * 工业级现代化全自动在线更新对话框
 */
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onIgnore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) {
        return
    }

    val context = LocalContext.current
    val currentVersion = AppUpdateManager.getCurrentVersionName(context)

    Dialog(
        onDismissRequest = {
            // 下载过程中防止误触外部关闭
            if (state !is UpdateUiState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateUiState.Downloading,
            dismissOnClickOutside = state !is UpdateUiState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, OutlineVariantLight), RoundedCornerShape(20.dp)),
            color = SurfaceContainerLowest,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header: 标题与版本徽标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (state) {
                                is UpdateUiState.Downloading -> Icons.Default.Download
                                is UpdateUiState.ReadyToInstall -> Icons.Default.SystemUpdate
                                is UpdateUiState.PermissionRequired -> Icons.Default.Security
                                is UpdateUiState.Error -> Icons.Default.ErrorOutline
                                else -> Icons.Default.RocketLaunch
                            },
                            contentDescription = null,
                            tint = OnPrimaryWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state) {
                                is UpdateUiState.Downloading -> "正在高速下载更新"
                                is UpdateUiState.ReadyToInstall -> "新版本已就绪"
                                is UpdateUiState.PermissionRequired -> "需要应用安装授权"
                                is UpdateUiState.Error -> "更新异常"
                                else -> "发现新版本"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 18.sp
                            )
                        )

                        val updateInfo = when (state) {
                            is UpdateUiState.UpdateAvailable -> state.info
                            is UpdateUiState.Downloading -> state.info
                            is UpdateUiState.ReadyToInstall -> state.info
                            is UpdateUiState.PermissionRequired -> state.info
                            else -> null
                        }

                        if (updateInfo != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Surface(
                                    color = SurfaceContainerHigh,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "v$currentVersion ➔ ${updateInfo.tagName}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = OnSurfaceDark
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (updateInfo.fileSize > 0) {
                                    val sizeMb = String.format(Locale.US, "%.1f MB", updateInfo.fileSize / (1024.0 * 1024.0))
                                    Text(
                                        text = sizeMb,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = OnSurfaceVariantGray
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Body 内容区域
                when (state) {
                    is UpdateUiState.UpdateAvailable -> {
                        Text(
                            text = "更新日志",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceDark,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerLow)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownRenderer(
                                content = state.info.releaseNotes,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { onIgnore(state.info.tagName) }
                            ) {
                                Text(
                                    text = "忽略此版",
                                    color = OnSurfaceVariantGray,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, OutlineVariantLight)
                                ) {
                                    Text(
                                        text = "稍后",
                                        color = OnSurfaceDark,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = { onStartDownload(state.info) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryBlack,
                                        contentColor = OnPrimaryWhite
                                    )
                                ) {
                                    Text(
                                        text = "立即升级",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val percent = (state.progress * 100).toInt()
                            val downloadedMb = String.format(Locale.US, "%.1f", state.downloadedBytes / (1024.0 * 1024.0))
                            val totalMb = if (state.totalBytes > 0) {
                                String.format(Locale.US, "%.1f", state.totalBytes / (1024.0 * 1024.0))
                            } else {
                                "--"
                            }

                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PrimaryBlack,
                                trackColor = SurfaceContainerHigh
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$downloadedMb MB / $totalMb MB",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariantGray,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlack,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ 速率: ${if (state.speedText.isNotBlank()) state.speedText else "测速连接中..."}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryBlack,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = state.currentChannel.ifBlank { "智能通道" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = OnSurfaceVariantGray,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AppUpdateManager.openInBrowser(context, state.info.downloadUrl)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, OutlineVariantLight),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "外部浏览器下载",
                                        fontSize = 11.sp,
                                        color = OnSurfaceDark
                                    )
                                }

                                Text(
                                    text = "后台持续下载中",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OnSurfaceVariantGray,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Text(
                            text = "安装包已完整下载！请点击下方按钮完成系统覆盖更新。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OnSurfaceDark,
                                fontSize = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, OutlineVariantLight)
                            ) {
                                Text(
                                    text = "取消",
                                    color = OnSurfaceDark,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = { onInstall(state.apkFile) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlack,
                                    contentColor = OnPrimaryWhite
                                )
                            ) {
                                Text(
                                    text = "立即安装",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    is UpdateUiState.PermissionRequired -> {
                        Text(
                            text = "系统提示：为完成更新，需要授予应用【安装未知应用】的权限。\n\n点击下方按钮将自动跳转至系统设置页，开启后返回即可继续安装。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OnSurfaceDark,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, OutlineVariantLight)
                            ) {
                                Text(
                                    text = "取消",
                                    color = OnSurfaceDark,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = { onInstall(state.apkFile) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlack,
                                    contentColor = OnPrimaryWhite
                                )
                            ) {
                                Text(
                                    text = "前往设置允许",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    is UpdateUiState.Error -> {
                        Text(
                            text = "更新提示：${state.message}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = ErrorRed,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codywon/mqtt-assistant-app/releases"))
                                    context.startActivity(browserIntent)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, OutlineVariantLight)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "网页下载",
                                    color = OnSurfaceDark,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlack,
                                    contentColor = OnPrimaryWhite
                                )
                            ) {
                                Text(
                                    text = "知道了",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
