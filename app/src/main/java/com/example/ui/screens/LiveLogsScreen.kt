package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppScreen
import com.example.model.MqttLogPacket
import com.example.model.PublishPreset
import com.example.ui.components.JsonCodeBlockView
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.util.MqttTopicUtil
import com.example.viewmodel.MqttAssistantViewModel
import java.util.UUID

@Composable
fun LiveLogsScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val packets by viewModel.livePackets.collectAsState()
    val filterQuery by viewModel.logFilterQuery.collectAsState()
    val isPaused by viewModel.isRecordingPaused.collectAsState()
    val isJsonPretty by viewModel.isJsonPrettyFormat.collectAsState()
    val includeFilters by viewModel.includeTopicFilters.collectAsState()
    val excludeFilters by viewModel.excludeTopicFilters.collectAsState()

    var selectedDetailsPacket by remember { mutableStateOf<MqttLogPacket?>(null) }

    val filteredPackets = packets.filter { packet ->
        val matchesAllowed = MqttTopicUtil.isTopicAllowed(packet.topic, includeFilters, excludeFilters)
        val matchesQuery = filterQuery.isBlank() ||
                packet.topic.contains(filterQuery, ignoreCase = true) ||
                packet.payload.contains(filterQuery, ignoreCase = true)
        matchesAllowed && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
    ) {
        // 1. 常驻顶部控制栏 (Fixed Top Header: 输入框常驻，避免消息多了难以翻回)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceContainerLowest,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search Input (常驻搜索框)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OutlineGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = filterQuery,
                            onValueChange = { viewModel.logFilterQuery.value = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("log_filter_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (filterQuery.isEmpty()) {
                                    Text(
                                        text = "搜索主题或报文载荷...",
                                        style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (filterQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空过滤",
                                tint = OutlineGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.logFilterQuery.value = "" }
                            )
                        }
                    }

                    // VS Code / GitHub JSON 格式化开关
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isJsonPretty) PrimaryBlack else SurfaceContainerLow)
                            .clickable { viewModel.toggleJsonPretty() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JSON",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isJsonPretty) OnPrimaryWhite else PrimaryBlack
                            )
                        )
                    }

                    // 暂停 / 继续 流接收
                    IconButton(
                        onClick = { viewModel.toggleStreamPause() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .testTag("stream_pause_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续接收" else "暂停接收",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 清空消息
                    IconButton(
                        onClick = { viewModel.clearLogStream() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .testTag("stream_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空报文",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 计数与状态指示微信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaused) "● 消息流已暂停" else "● 实时接收中 (${filteredPackets.size} 条)",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPaused) OutlineGray else AccentEmerald
                        )
                    )
                    if (includeFilters.isNotEmpty() || excludeFilters.isNotEmpty()) {
                        Text(
                            text = "包含${includeFilters.size}项 / 排除${excludeFilters.size}项规则已生效",
                            style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
                        )
                    }
                }
            }
        }

        // 2. 独立滚动的消息流列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredPackets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (filterQuery.isNotBlank()) "未匹配到相关报文" else "暂无消息 (等待 Broker 推送...)",
                            style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                        )
                    }
                }
            } else {
                items(filteredPackets, key = { it.id }) { packet ->
                    CompactMessageCard(
                        packet = packet,
                        isJsonPretty = isJsonPretty,
                        onClick = { selectedDetailsPacket = packet },
                        onCopyPayload = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", packet.payload))
                            viewModel.showToast("已复制报文内容")
                        }
                    )
                }
            }
        }
    }

    // 3. 报文详情弹窗 (MessageDetailsModalDialog: 解决“详情打不开”问题)
    selectedDetailsPacket?.let { packet ->
        MessageDetailsModalDialog(
            packet = packet,
            isJsonPretty = isJsonPretty,
            onDismiss = { selectedDetailsPacket = null },
            onCopyTopic = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("topic", packet.topic))
                viewModel.showToast("已复制主题: ${packet.topic}")
            },
            onCopyPayload = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", packet.payload))
                viewModel.showToast("已复制载荷内容")
            },
            onLoadIntoPublish = {
                val preset = PublishPreset(
                    id = UUID.randomUUID().toString(),
                    name = packet.topic.substringAfterLast('/'),
                    topic = packet.topic,
                    qos = packet.qos,
                    retain = false,
                    payload = packet.payload
                )
                viewModel.saveOrUpdatePreset(preset)
                viewModel.navigateTo(AppScreen.Publish)
                selectedDetailsPacket = null
                viewModel.showToast("已载入至发布页配置")
            }
        )
    }
}

/**
 * 紧凑型消息卡片：
 * 1. 彻底砍掉底部整行，释放空间
 * 2. 将 QoS 等级、大小、Retain、时间、序号合并到主题下方单行
 * 3. 规范为 "QoS 0/1/2" 避免误解为 "QO"
 * 4. 接入 GitHub / VS Code 风格的高亮代码排版
 */
@Composable
private fun CompactMessageCard(
    packet: MqttLogPacket,
    isJsonPretty: Boolean,
    onClick: () -> Unit,
    onCopyPayload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("log_packet_${packet.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Colored Dot + Topic + Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(packet.dotColorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = packet.topic,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = PrimaryBlack
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCopyPayload,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制载荷",
                        tint = OnSurfaceVariantGray,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Row 2: 合并元数据 (规范明确的 QoS 等级、大小、Retain、时间戳、序号，底部释放全部空间)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // QoS Badge (规范名称，消除 [QO] 误解)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "QoS ${packet.qos}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    )
                }

                // Retain Badge
                if (packet.devInfo.contains("Retain", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "Retain",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                }

                // Size Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 4.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = packet.sizeText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = OnSurfaceVariantGray
                        )
                    )
                }

                // Sequence
                Text(
                    text = packet.packetSeq,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = OnSurfaceVariantGray
                    )
                )

                // Timestamp
                Text(
                    text = packet.timestamp,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }

            // Row 3: VS Code / GitHub 风格的 JSON 语法高亮代码块 (卡片内最多预览 5 行，点击卡片可看全)
            JsonCodeBlockView(
                rawText = packet.payload,
                isJsonPretty = isJsonPretty,
                showLineNumbers = isJsonPretty,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 全功能报文详情弹窗 (真正打开详情，展示完整报文属性与操作)
 */
@Composable
private fun MessageDetailsModalDialog(
    packet: MqttLogPacket,
    isJsonPretty: Boolean,
    onDismiss: () -> Unit,
    onCopyTopic: () -> Unit,
    onCopyPayload: () -> Unit,
    onLoadIntoPublish: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "消息详情",
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OutlineGray
                        )
                    }
                }

                // Attributes List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailRow(label = "主题 (Topic)", value = packet.topic, onCopy = onCopyTopic)
                    DetailRow(label = "服务质量 (QoS)", value = "QoS ${packet.qos} (${when(packet.qos) { 0 -> "最多发一次"; 1 -> "至少送达一次"; 2 -> "保证仅送达一次"; else -> "" }})")
                    DetailRow(label = "报文序号", value = packet.packetSeq)
                    DetailRow(label = "接收时间", value = packet.timestamp)
                    DetailRow(label = "载荷大小", value = packet.sizeText)
                    DetailRow(label = "保留标记", value = if (packet.devInfo.contains("Retain")) "是 (Retained)" else "否")
                }

                // Payload Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "载荷内容 (Payload)",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )
                    TextButton(onClick = onCopyPayload) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制载荷", fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Bold)
                    }
                }

                // Full VS Code / GitHub JSON Highlighter View
                JsonCodeBlockView(
                    rawText = packet.payload,
                    isJsonPretty = isJsonPretty,
                    showLineNumbers = true,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = OutlineVariantLight, thickness = 0.5.dp)

                // Actions Bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("关闭", color = OnSurfaceVariantGray)
                    }

                    Button(
                        onClick = onLoadIntoPublish,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("载入至发布页", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryBlack
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onCopy != null) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    tint = OutlineGray,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onCopy)
                )
            }
        }
    }
}
