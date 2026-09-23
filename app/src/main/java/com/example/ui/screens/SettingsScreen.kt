package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BrokerProfile
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.OutlineGray
import java.util.UUID
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MqttConnectionState
import com.example.ui.theme.ErrorContainerRed
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnErrorContainerRed
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SecondaryContainerMint
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.viewmodel.MqttAssistantViewModel

@Composable
fun SettingsScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val brokerProfiles by viewModel.brokerProfiles.collectAsState()
    val activeBrokerId by viewModel.activeBrokerId.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isExportingConfig by viewModel.isExportingConfig.collectAsState()
    val isImportingConfig by viewModel.isImportingConfig.collectAsState()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkBatteryOptimizationStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val configPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importConfiguration(context, it) }
    }

    var showBrokerDialog by remember { mutableStateOf(false) }
    var editingBroker by remember { mutableStateOf<BrokerProfile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Multi-Broker Management Cluster Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("multi_broker_cluster_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "多 Broker 节点集群 (${brokerProfiles.size}个)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Button(
                        onClick = {
                            editingBroker = BrokerProfile(
                                id = UUID.randomUUID().toString(),
                                name = "自定义节点",
                                host = "",
                                port = 1883,
                                clientId = "client_mobile_" + UUID.randomUUID().toString().take(6),
                                username = "",
                                password = ""
                            )
                            showBrokerDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_broker_node_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = OnPrimaryWhite, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新增节点", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnPrimaryWhite)
                    }
                }

                Text(
                    text = "点击任意节点卡片即可一键切换并自动重连，支持独立保存各节点的主机、端口及鉴权凭证。",
                    style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariantGray, fontSize = 12.sp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brokerProfiles.forEach { profile ->
                        val isSelected = profile.id == activeBrokerId
                        BrokerNodeCard(
                            profile = profile,
                            isSelected = isSelected,
                            isConnected = isSelected && config.isConnected,
                            onSelect = {
                                if (!isSelected) {
                                    viewModel.selectBroker(profile.id)
                                }
                            },
                            onEdit = {
                                editingBroker = profile
                                showBrokerDialog = true
                            },
                            onDelete = {
                                viewModel.deleteBroker(profile.id)
                            },
                            canDelete = brokerProfiles.size > 1
                        )
                    }
                }
            }
        }

        // 2. Background Keep-Alive & Auto-Reconnect Engine (Production Grade)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "后台常驻与断线重连",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }
                }


                // 1. Auto Reconnect switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动断线重连",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "弱网、网络切换或服务端重启时，自动以退避算法重试并恢复订阅",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = config.autoReconnect,
                        onCheckedChange = { viewModel.toggleAutoReconnect() },
                        modifier = Modifier.testTag("settings_auto_reconnect_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 2. Foreground Keep-Alive Service
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "后台常驻保活服务 (Foreground Service)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "启用 Android 前台保活通知与定时心跳，防止切到后台或锁屏被系统冻结",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = config.backgroundKeepAliveEnabled,
                        onCheckedChange = { viewModel.toggleBackgroundKeepAlive(context) },
                        modifier = Modifier.testTag("settings_background_service_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 3. CPU WakeLock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CPU 唤醒锁 (WakeLock)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "息屏待机状态下保持 CPU 微唤醒，确保长连接心跳与报文毫秒级响应",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = config.wakeLockEnabled,
                        onCheckedChange = { viewModel.toggleWakeLock() },
                        modifier = Modifier.testTag("settings_wakelock_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 4. Auto-Start on Boot (开机自启动)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开机自启动",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "设备开机或重启后自动拉起保活服务，并恢复 MQTT 连接与监听",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = config.autoStartEnabled,
                        onCheckedChange = { viewModel.toggleAutoStart(context) },
                        modifier = Modifier.testTag("settings_auto_start_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 5. Process Guard (进程守护，默认开启)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "进程守护 (Watchdog)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "默认开启。应用覆盖更新或异常强退时由系统自愈唤醒，保障持续收发",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = config.processGuardEnabled,
                        onCheckedChange = { viewModel.toggleProcessGuard() },
                        modifier = Modifier.testTag("settings_process_guard_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 6. Ignore Battery Optimizations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "忽略电池优化 (防系统杀后台)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "申请无限制后台电源策略，彻底杜绝程序最小化或锁屏被系统冻结",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { viewModel.requestIgnoreBatteryOptimization(context) },
                        modifier = Modifier
                            .height(34.dp)
                            .defaultMinSize(minWidth = 68.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (isBatteryOptimizationIgnored) "查看状态" else "立即开启",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 7. Log & Data Storage
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "日志与本地缓存",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }
                }


                // 满额自动导出 Excel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "满额自动导出 Excel",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "满 ${java.text.NumberFormat.getIntegerInstance().format(config.bufferThreshold)} 条自动归档至系统 Download 目录",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.autoExportExcel,
                        onCheckedChange = { viewModel.toggleAutoExportExcel() },
                        modifier = Modifier.testTag("auto_export_excel_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // Metrics cards (1:1 绝对等高与严格对称排版)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "缓存阈值",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray, fontSize = 11.5.sp)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${config.bufferThreshold}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = PrimaryBlack
                                )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "条",
                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                            )
                        }
                        Text(
                            text = "超限循环覆盖",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "已占用空间",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray, fontSize = 11.5.sp)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f", config.usedSpaceMb),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = PrimaryBlack
                                )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "MB",
                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                            )
                        }
                        Text(
                            text = "累计 ${config.packetCount} 条报文",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                }

                // Storage management: Clean & Export Excel (1:1 对称等宽利落排布)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 清理空间按钮
                    OutlinedButton(
                        onClick = { viewModel.clearAllData() },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OutlineGray.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OnSurfaceVariantGray
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("clear_all_data_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清理空间",
                            modifier = Modifier.size(16.dp),
                            tint = OnSurfaceVariantGray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "清理空间",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }

                    // 导出 Excel 按钮
                    Button(
                        onClick = { viewModel.exportPacketsToExcel(context) },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("export_excel_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isExporting) "导出中..." else "导出 Excel",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // 4. Configuration Backup & Restore Card (JSON Export/Import)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(SurfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = PrimaryBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "配置备份与迁移",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                    }
                }

                Text(
                    text = "一键导出恢复或通过口令互传所有服务器节点、发布预设、订阅主题与引擎设置，换机或多设备调试零门槛。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = OnSurfaceVariantGray,
                        lineHeight = 16.sp
                    )
                )

                // Buttons: 导入配置 (Outlined) & 导出配置 (Primary Solid) 1:1 对称等宽
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 导入配置
                    OutlinedButton(
                        onClick = { configPickerLauncher.launch("application/json") },
                        enabled = !isImportingConfig,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OutlineGray.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("import_config_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "导入配置",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isImportingConfig) "导入中..." else "导入配置",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    // 导出配置
                    Button(
                        onClick = { viewModel.exportConfiguration(context) },
                        enabled = !isExportingConfig,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("export_config_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出配置",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isExportingConfig) "导出中..." else "导出配置",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 口令免文件极速互传 (Clean Minimalist Text Actions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.importConfigFromClipboard(context) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "从剪贴板口令导入",
                            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = OnSurfaceVariantGray)
                        )
                    }

                    TextButton(
                        onClick = { viewModel.copyConfigToken(context) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "复制配置口令",
                            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = OnSurfaceVariantGray)
                        )
                    }
                }
            }
        }

        // Footer version info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = OnSurfaceVariantGray,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "MQTT Assistant v1.0.0 (Android Native)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }
            Text(
                text = "Powered by codywon",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = OnSurfaceVariantGray.copy(alpha = 0.7f)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Modal dialog for editing or adding a broker profile
    if (showBrokerDialog && editingBroker != null) {
        BrokerProfileEditDialog(
            initialBroker = editingBroker!!,
            onDismiss = {
                showBrokerDialog = false
                editingBroker = null
            },
            onSave = { updated ->
                viewModel.saveOrUpdateBroker(updated)
                showBrokerDialog = false
                editingBroker = null
            }
        )
    }
}

@Composable
private fun BrokerNodeCard(
    profile: BrokerProfile,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceContainerLow else SurfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("broker_node_${profile.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) PrimaryBlack else OutlineVariantLight
                            )
                    )
                    Text(
                        text = profile.name,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PrimaryBlack)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConnected) "已连接" else "已选用",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimaryWhite
                                )
                            )
                        }
                    }
                    if (profile.tlsEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerDefault)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TLS",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlack
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).testTag("edit_broker_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (canDelete && !isSelected) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_broker_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = ErrorRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${profile.host}:${profile.port}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryBlack
                    )
                )
                Text(
                    text = if (profile.username.isNotBlank()) "User: ${profile.username}" else "匿名",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }
        }
    }
}

@Composable
private fun BrokerProfileEditDialog(
    initialBroker: BrokerProfile,
    onDismiss: () -> Unit,
    onSave: (BrokerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialBroker.name) }
    var host by remember { mutableStateOf(initialBroker.host) }
    var portStr by remember { mutableStateOf(initialBroker.port.toString()) }
    var clientId by remember { mutableStateOf(initialBroker.clientId) }
    var username by remember { mutableStateOf(initialBroker.username) }
    var password by remember { mutableStateOf(initialBroker.password) }
    var protocol by remember { mutableStateOf(initialBroker.protocol) }
    var tlsEnabled by remember { mutableStateOf(initialBroker.tlsEnabled) }
    var cleanSession by remember { mutableStateOf(initialBroker.cleanSession) }
    var keepAliveStr by remember { mutableStateOf(initialBroker.keepAlive.toString()) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialBroker.host.isBlank()) "新增 Broker 节点" else "编辑 Broker 节点",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 16.sp
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)

                // Name
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "节点名称 / 备注",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.weight(1f).testTag("broker_dialog_name_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Host & Port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(2.5f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "服务器主机 (Host)",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = host,
                                onValueChange = { host = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_host_input"),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = PrimaryBlack
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "端口",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = portStr,
                                onValueChange = { portStr = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_port_input"),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = PrimaryBlack
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }
                }

                // Client ID
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "客户端标识 (Client ID)",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Text(
                            text = "随机生成",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = PrimaryBlack,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable {
                                clientId = "client_mobile_" + UUID.randomUUID().toString().take(6)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            modifier = Modifier.weight(1f).testTag("broker_dialog_clientid_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Username & Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "用户名",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_username_input"),
                                textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "密码",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_password_input"),
                                textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack),
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = OutlineGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // TLS & CleanSession toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TLS 加密连接 (MQTTS)",
                        style = TextStyle(fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = tlsEnabled,
                        onCheckedChange = { isTls ->
                            tlsEnabled = isTls
                            if (isTls) {
                                portStr = "8883"
                            } else if (portStr == "8883") {
                                portStr = "1883"
                            }
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "清除会话 (Clean Session)",
                        style = TextStyle(fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = cleanSession,
                        onCheckedChange = { cleanSession = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.6.dp, OutlineGray),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("取消", color = OnSurfaceVariantGray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val p = portStr.toIntOrNull() ?: 1883
                            val ka = keepAliveStr.toIntOrNull() ?: 60
                            val updated = initialBroker.copy(
                                name = name.ifBlank { host },
                                host = host.trim(),
                                port = p,
                                clientId = clientId.trim(),
                                username = username.trim(),
                                password = password,
                                protocol = protocol,
                                tlsEnabled = tlsEnabled,
                                cleanSession = cleanSession,
                                keepAlive = ka
                            )
                            onSave(updated)
                        },
                        enabled = host.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        modifier = Modifier.height(38.dp).testTag("broker_dialog_save_btn")
                    ) {
                        Text("保存节点", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
