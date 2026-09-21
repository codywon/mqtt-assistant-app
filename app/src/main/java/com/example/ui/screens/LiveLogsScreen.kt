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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var isTopicFilterDialogVisible by remember { mutableStateOf(false) }
    val totalFilterRules = includeFilters.size + excludeFilters.size

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filteredPackets = packets.filter { packet ->
        val matchesAllowed = MqttTopicUtil.isTopicAllowed(packet.topic, includeFilters, excludeFilters)
        val matchesQuery = filterQuery.isBlank() ||
                packet.topic.contains(filterQuery, ignoreCase = true) ||
                packet.payload.contains(filterQuery, ignoreCase = true)
        matchesAllowed && matchesQuery
    }

    // 自动向下滚动：未暂停时，新消息到达自动平滑向下滚动至最底部，底部永远是最新一条
    LaunchedEffect(filteredPackets.size, isPaused) {
        if (!isPaused && filteredPackets.isNotEmpty()) {
            listState.animateScrollToItem(filteredPackets.size - 1)
        }
    }

    // 初始进入或恢复时直接定位到最后一条最新消息
    LaunchedEffect(Unit) {
        if (filteredPackets.isNotEmpty()) {
            listState.scrollToItem(filteredPackets.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
    ) {
        // 1. 常驻顶部控制栏 (Clean, Monochrome, Minimalist ChatGPT Aesthetic)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceContainerLowest,
            shadowElevation = 1.dp
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Search Input (常驻极简搜索框，无厚重灰底)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                            .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OutlineGray,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = filterQuery,
                            onValueChange = { viewModel.logFilterQuery.value = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("log_filter_input"),
                            textStyle = TextStyle(fontSize = 12.5.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (filterQuery.isEmpty()) {
                                    Text(
                                        text = "搜索主题或报文...",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
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
                                    .size(15.dp)
                                    .clickable { viewModel.logFilterQuery.value = "" }
                            )
                        }
                    }

                    // 1. 主题过滤规则 (包含/排除) 纯净图标按钮 - 彻底去除灰底色块
                    IconButton(
                        onClick = { isTopicFilterDialogVisible = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("log_filter_rules_btn")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "主题过滤规则",
                                tint = if (totalFilterRules > 0) PrimaryBlack else OnSurfaceVariantGray,
                                modifier = Modifier.size(18.dp)
                            )
                            if (totalFilterRules > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlack)
                                )
                            }
                        }
                    }

                    // 2. JSON 格式化纯净轻量开关 - 无厚重灰底积木
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (isJsonPretty) 1.dp else 0.5.dp,
                                color = if (isJsonPretty) PrimaryBlack else SurfaceContainerDefault,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.toggleJsonPretty() }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JSON",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                fontWeight = if (isJsonPretty) FontWeight.Bold else FontWeight.Medium,
                                color = if (isJsonPretty) PrimaryBlack else OnSurfaceVariantGray
                            )
                        )
                    }

                    // 3. 暂停 / 恢复 自动向下滚动 - 纯净图标，无灰底方块
                    IconButton(
                        onClick = {
                            val wasPaused = isPaused
                            viewModel.toggleStreamPause()
                            if (wasPaused) {
                                // 点击从暂停切回继续时，平滑滚动至最底部最新消息
                                coroutineScope.launch {
                                    if (filteredPackets.isNotEmpty()) {
                                        listState.animateScrollToItem(filteredPackets.size - 1)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("stream_pause_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "恢复自动滚动" else "暂停自动滚动",
                            tint = if (isPaused) Color(0xFFDC2626) else PrimaryBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 4. 清空消息 - 纯净图标，无灰底方块
                    IconButton(
                        onClick = { viewModel.clearLogStream() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("stream_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
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
                        text = if (isPaused) "⏸ 自动滚动已暂停 (${filteredPackets.size} 条 · 可自由浏览)" else "● 自动吸附最新 (${filteredPackets.size} 条)",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPaused) Color(0xFFD97706) else AccentEmerald
                        )
                    )
                    if (totalFilterRules > 0) {
                        Text(
                            text = "过滤已生效 (${totalFilterRules}条规则)",
                            style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
                        )
                    }
                }
            }
        }

        // 过滤规则生效指示横幅 (参考 PC 客户端，支持通配符)
        if (totalFilterRules > 0) {
            val filterSummary = buildString {
                if (excludeFilters.isNotEmpty()) append("已排除 ${excludeFilters.size} 项 ")
                if (includeFilters.isNotEmpty()) append("已包含 ${includeFilters.size} 项")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerLowest)
                    .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(8.dp))
                    .clickable { isTopicFilterDialogVisible = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = PrimaryBlack,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "过滤已生效: $filterSummary",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )
                }
                Text(
                    text = "配置规则 >",
                    style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, fontWeight = FontWeight.Medium)
                )
            }
        }

        // 2. 独立滚动的消息流列表 (支持自动吸底滚动与暂停自由翻阅)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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
                            onCopyTopic = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("topic", packet.topic))
                                viewModel.showToast("已复制主题: ${packet.topic}")
                            },
                            onCopyPayload = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", packet.payload))
                                viewModel.showToast("已复制消息内容")
                            }
                        )
                    }
                }
            }

            // 悬浮快速“滚到底部最新”微按钮 (暂停滚动模式下展示，一键吸底并恢复自动滚动)
            if (isPaused && filteredPackets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(filteredPackets.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                        .size(42.dp),
                    shape = CircleShape,
                    containerColor = PrimaryBlack,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "回到底部最新",
                        modifier = Modifier.size(20.dp)
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
                viewModel.showToast("已复制消息内容")
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

    // 4. PC级主题过滤规则弹窗 (包含/排除通配符)
    if (isTopicFilterDialogVisible) {
        TopicFilterRulesModalDialog(
            includeFilters = includeFilters,
            excludeFilters = excludeFilters,
            onAddInclude = { viewModel.addIncludeTopicFilter(it) },
            onRemoveInclude = { viewModel.removeIncludeTopicFilter(it) },
            onClearIncludes = { viewModel.clearIncludeTopicFilters() },
            onAddExclude = { viewModel.addExcludeTopicFilter(it) },
            onRemoveExclude = { viewModel.removeExcludeTopicFilter(it) },
            onClearExcludes = { viewModel.clearExcludeTopicFilters() },
            onDismiss = { isTopicFilterDialogVisible = false }
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
    onCopyTopic: () -> Unit,
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
            // Row 1: Colored Dot + Topic (支持多行完整换行，点击直接复制主题) + Copy Payload Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCopyTopic)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.5.dp)
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
                            lineHeight = 18.sp,
                            color = PrimaryBlack
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCopyPayload,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制消息",
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

                // Clean Topic Section (支持多行完整换行，整块区域点击直接复制主题)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .clickable(onClick = onCopyTopic)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "主题 (点击可直接复制)",
                            style = TextStyle(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariantGray
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制主题",
                            tint = OutlineGray,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // 与消息卡片完全一致的多行换行主题呈现，支持任意层级主题
                    Text(
                        text = packet.topic,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            color = PrimaryBlack
                        )
                    )
                }

                // Message Payload Header with 复制消息 button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "完整消息内容",
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
                        Text("复制消息", fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Bold)
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

                // Actions Bottom: 等高 44.dp，严格对齐与纯净工业质感
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, OutlineVariantLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlack
                        )
                    ) {
                        Text(
                            text = "关闭",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                    }

                    Button(
                        onClick = onLoadIntoPublish,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "载入至发布页",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryWhite
                            )
                        )
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

/**
 * PC-Grade Topic Filter Rules Dialog (Includes and Excludes with Wildcard Support).
 * Strictly Monochrome per user requirements.
 */
@Composable
private fun TopicFilterRulesModalDialog(
    includeFilters: List<String>,
    excludeFilters: List<String>,
    onAddInclude: (String) -> Unit,
    onRemoveInclude: (Int) -> Unit,
    onClearIncludes: () -> Unit,
    onAddExclude: (String) -> Unit,
    onRemoveExclude: (Int) -> Unit,
    onClearExcludes: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf("exclude") } // "exclude" or "include"
    var inputPattern by remember { mutableStateOf("") }

    val isExclude = activeTab == "exclude"
    val currentList = if (isExclude) excludeFilters else includeFilters

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "主题过滤规则 (消息筛选)",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 16.sp
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = OutlineGray, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = OutlineVariantLight, thickness = 0.5.dp)

                // Segment Tabs: Exclude Tab (Recommended / Priority) vs Include Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "exclude" to "排除规则 (${excludeFilters.size})",
                        "include" to "包含规则 (${includeFilters.size})"
                    ).forEach { (tabKey, tabLabel) ->
                        val selected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) PrimaryBlack else Color.Transparent)
                                .clickable {
                                    activeTab = tabKey
                                    inputPattern = ""
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray
                                )
                            )
                        }
                    }
                }

                // Input field + Add button (去除灰厚重感)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                            .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = inputPattern,
                            onValueChange = { inputPattern = it },
                            modifier = Modifier.weight(1f).testTag("filter_pattern_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = PrimaryBlack,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (inputPattern.isEmpty()) {
                                    Text(
                                        text = if (isExclude) "输入排除主题，支持 + 和 #" else "输入包含主题，支持 + 和 #",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (inputPattern.isNotEmpty()) {
                            IconButton(onClick = { inputPattern = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = OutlineGray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val trimmed = inputPattern.trim()
                            if (trimmed.isNotEmpty()) {
                                if (isExclude) onAddExclude(trimmed) else onAddInclude(trimmed)
                                inputPattern = ""
                            }
                        },
                        enabled = inputPattern.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite,
                            disabledContainerColor = SurfaceContainerLow,
                            disabledContentColor = OutlineGray
                        ),
                        modifier = Modifier.height(42.dp).testTag("filter_pattern_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Wildcard helper chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "快捷通配符:", style = TextStyle(fontSize = 11.sp, color = OutlineGray))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerLow)
                            .clickable {
                                val t = inputPattern.trim()
                                inputPattern = if (t.isEmpty()) "+" else if (t.endsWith("/")) "$t+" else "$t/+"
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ 单级通配", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PrimaryBlack))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerLow)
                            .clickable {
                                val t = inputPattern.trim()
                                inputPattern = if (t.isEmpty()) "#" else if (t.endsWith("/")) "$t#" else "$t/#"
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("# 多级通配 (末端)", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PrimaryBlack))
                    }
                }

                // Description hint
                Text(
                    text = if (isExclude) {
                        "★ 排除条件优先级最高：匹配规则的消息直接过滤隐藏。\n示例: test/ping/# 或 sensor/+/raw"
                    } else {
                        "★ 包含条件：配置后仅显示匹配此规则的消息。\n示例: device/+/status 或 topic/#"
                    },
                    style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp)
                )

                // List header & Clear button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已配置 ${currentList.size} 个${if (isExclude) "排除" else "包含"}条件",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                    )
                    if (currentList.isNotEmpty()) {
                        TextButton(
                            onClick = { if (isExclude) onClearExcludes() else onClearIncludes() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("清空全部", fontSize = 11.sp, color = OnSurfaceVariantGray)
                        }
                    }
                }

                // List of tag filters
                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                            .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(8.dp))
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExclude) "暂无排除条件，添加后将隐藏匹配的消息" else "暂无包含条件，添加后将只显示匹配的消息",
                            style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentList.forEachIndexed { index, pattern ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerLow)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBlack)
                                    )
                                    Text(
                                        text = pattern,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryBlack
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = { if (isExclude) onRemoveExclude(index) else onRemoveInclude(index) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "删除条件", tint = OnSurfaceVariantGray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack, contentColor = OnPrimaryWhite),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
