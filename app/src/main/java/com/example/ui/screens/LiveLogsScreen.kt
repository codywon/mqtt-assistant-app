package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
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
import com.example.ui.components.ClaudeOrbitLoading
import com.example.ui.components.JsonCodeBlockView
import com.example.ui.components.MarkdownRenderer
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
import com.example.viewmodel.LiveHealthWatchdogState
import com.example.viewmodel.MqttAssistantViewModel
import com.example.viewmodel.WatchdogAnomaly
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
    val includeFilters by viewModel.includeTopicFilters.collectAsState()
    val excludeFilters by viewModel.excludeTopicFilters.collectAsState()
    val filteredPackets by viewModel.filteredLivePackets.collectAsState()
    val tslParseResults by viewModel.tslParseResults.collectAsState()
    val overflowCount by viewModel.packetOverflowCount.collectAsState()

    var selectedDetailsPacket by remember { mutableStateOf<MqttLogPacket?>(null) }
    var isTopicFilterDialogVisible by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val totalFilterRules = includeFilters.size + excludeFilters.size

    val inspectingPacket by viewModel.inspectingPacket.collectAsState()
    val inspectionResult by viewModel.packetInspectionResult.collectAsState()
    val isInspecting by viewModel.isPacketInspecting.collectAsState()
    val inspectionThinking by viewModel.packetInspectionThinking.collectAsState()
    val watchdogState by viewModel.liveHealthWatchdogState.collectAsState()
    var isWatchdogExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val onSelectPacket: (MqttLogPacket) -> Unit = remember {
        { packet -> selectedDetailsPacket = packet }
    }
    val onInspectPacket: (MqttLogPacket) -> Unit = remember {
        { packet -> viewModel.inspectPacketWithAi(packet) }
    }
    val onCopyTopicText: (String) -> Unit = remember {
        { topic ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("topic", topic))
            viewModel.showToast("已复制主题: $topic")
        }
    }
    val onCopyPayloadText: (String) -> Unit = remember {
        { payload ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", payload))
            viewModel.showToast("已复制消息内容")
        }
    }

    // 智能自动吸底与防闪屏状态机：
    // 1. 默认处于 autoScrollToBottom 自动向下吸底追踪状态；
    // 2. 当用户向上滚动离开底部时，自动停止吸底，保障自由翻看历史不受干扰；
    // 3. 当用户手动滑回最底部、解除暂停或点击悬浮回到底部按钮时，平滑重新恢复吸底！
    var autoScrollToBottom by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) true
            else {
                val lastVisibleIndex = visibleItems.last().index
                val totalCount = layoutInfo.totalItemsCount
                lastVisibleIndex >= totalCount - 2
            }
        }
    }

    // 仅监听用户真实手势拖拽（彻底排除系统自动滚动导致的误判中断）：
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            if (!isAtBottom) {
                autoScrollToBottom = false
            } else {
                autoScrollToBottom = true
            }
        }
    }

    // 毫秒级零抖动自动向下吸底推进：
    // 使用最新报文唯一 ID (latestPacketId) 作为触发键。
    // 即使报文总量达到 maxBuffer 截断上限导致 filteredPackets.size 恒定，只要有新消息进来 ID 必变，彻底终结假死停滚！
    val latestPacketId = filteredPackets.lastOrNull()?.id ?: ""
    LaunchedEffect(latestPacketId, autoScrollToBottom, isPaused) {
        if (autoScrollToBottom && !isPaused && filteredPackets.isNotEmpty()) {
            listState.scrollToItem(filteredPackets.size - 1)
        }
    }

    // 解除暂停时平滑过渡滚动至最底端并恢复吸底
    LaunchedEffect(isPaused) {
        if (!isPaused && filteredPackets.isNotEmpty()) {
            autoScrollToBottom = true
            listState.animateScrollToItem(filteredPackets.size - 1)
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

                    // 2. 暂停 / 恢复 自动向下滚动 - 纯净图标，无灰底方块
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

                    // 3. 清空消息 - 纯净图标，无灰底方块 (点击触发防误触确认)
                    IconButton(
                        onClick = {
                            if (filteredPackets.isNotEmpty()) {
                                showClearConfirmDialog = true
                            } else {
                                viewModel.showToast("当前暂无报文可清空")
                            }
                        },
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
            }
        }

        // 1.5 现场通信健康度雷达 (Proactive Watchdog Strip)
        LiveHealthWatchdogStrip(
            state = watchdogState,
            overflowCount = overflowCount,
            isExpanded = isWatchdogExpanded,
            onToggleExpand = { isWatchdogExpanded = !isWatchdogExpanded },
            onInspectAnomaly = { anomaly ->
                onInspectPacket(anomaly.rawPacket)
            },
            onGenerateReport = {
                viewModel.generateFieldAcceptanceReport()
            }
        )

        // 2. 独立滚动的消息流列表 (正向自然流序，自动向下吸底滚动与暂停自由翻阅)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = false,
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
                                .padding(vertical = 40.dp),
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
                            tslResult = tslParseResults[packet.id],
                            onCardClick = onSelectPacket,
                            onInspectWithAi = onInspectPacket,
                            onCopyTopic = onCopyTopicText,
                            onCopyPayload = onCopyPayloadText
                        )
                    }
                }
            }

            // 悬浮快速“滚到底部最新”微按钮 (当离开底部翻看历史或处于暂停模式时展示，一键直达并恢复吸附)
            if ((!autoScrollToBottom || !isAtBottom || isPaused) && filteredPackets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        autoScrollToBottom = true
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

    // 3. 报文详情弹窗 (MessageDetailsModalDialog)
    selectedDetailsPacket?.let { packet ->
        MessageDetailsModalDialog(
            packet = packet,
            tslResult = tslParseResults[packet.id],
            onDismiss = { selectedDetailsPacket = null },
            onInspectWithAi = {
                selectedDetailsPacket = null
                onInspectPacket(packet)
            },
            onCopyTopic = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("topic", packet.topic))
                Toast.makeText(context, "已复制主题: ${packet.topic}", Toast.LENGTH_SHORT).show()
                viewModel.showToast("已复制主题: ${packet.topic}")
            },
            onCopyPayload = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", packet.payload))
                Toast.makeText(context, "已复制消息内容", Toast.LENGTH_SHORT).show()
                viewModel.showToast("已复制消息内容")
            },
            onCopyValue = { value ->
                clipboardManager.setPrimaryClip(ClipData.newPlainText("value", value))
                Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
                viewModel.showToast("已复制: $value")
            },
            onLoadIntoPublish = {
                val preset = PublishPreset(
                    id = UUID.randomUUID().toString(),
                    name = packet.topic.substringAfterLast('/'),
                    topic = packet.topic,
                    qos = packet.qos,
                    retain = packet.devInfo.contains("Retain", ignoreCase = true),
                    payload = packet.payload
                )
                viewModel.saveOrUpdatePreset(preset)
                viewModel.navigateTo(AppScreen.Publish)
                selectedDetailsPacket = null
                viewModel.showToast("已载入至发布页配置")
            }
        )
    }

    // 3.5 AI 结构化透视与逆向解码弹窗 (AiPacketInspectorDialog)
    inspectingPacket?.let { packet ->
        AiPacketInspectorDialog(
            packet = packet,
            result = inspectionResult,
            isInspecting = isInspecting,
            thinkingText = inspectionThinking,
            onDismiss = { viewModel.dismissPacketInspection() },
            onSaveAsRule = { name, desc ->
                viewModel.saveInspectionAsProtocolKnowledge(packet, name, desc)
            },
            onContinueInChat = {
                viewModel.continueInspectionInChat(packet)
            },
            onCopyResult = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("ai_inspection", inspectionResult))
                viewModel.showToast("已复制 AI 透视结果")
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

    // 5. 清空实时报文二次防误触确认弹窗
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = SurfaceContainerLowest,
            title = {
                Text(
                    text = "确认清空所有实时报文？",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                )
            },
            text = {
                Text(
                    text = "确定要清空当前列表中的所有实时报文吗？（此操作仅清除当前显示的实时消息流，不影响底层 Broker 订阅与后台接收）",
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = OnSurfaceDark
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogStream()
                        showClearConfirmDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    )
                ) {
                    Text("确认清空", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
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
    tslResult: com.example.model.TslParseResult? = null,
    onCardClick: (MqttLogPacket) -> Unit,
    onInspectWithAi: (MqttLogPacket) -> Unit,
    onCopyTopic: (String) -> Unit,
    onCopyPayload: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(0.6.dp, OutlineVariantLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCardClick(packet) })
            .testTag("log_packet_${packet.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Row 1: Colored Dot + Topic (支持多行完整换行，点击直接复制主题) + AI Inspect & Copy Payload Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .weight(1f)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onInspectWithAi(packet) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI 结构化透视",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCopyPayload(packet.payload) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制消息",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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

            // Row 3: 紧凑原始载荷预览 (默认不换行展开格式化，极小省空间，轻触卡片查看多行格式化与全貌)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceContainerLow.copy(alpha = 0.5f))
                    .border(0.5.dp, SurfaceContainerDefault, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = packet.payload.ifBlank { "（空载荷）" }.replace("\n", " ").trim(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (packet.payload.isBlank()) OutlineGray else PrimaryBlack
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 4: TSL 物模型物理量解析 (直观呈现工程指标与越限报警，彻底替代正则)
            if (tslResult != null && tslResult.values.isNotEmpty()) {
                val hasWarn = tslResult.hasWarnings
                val bgColor = if (hasWarn) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                val borderColor = if (hasWarn) Color(0xFFFECACA) else Color(0xFFDCFCE7)
                val titleColor = if (hasWarn) Color(0xFFDC2626) else Color(0xFF16A34A)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .border(0.6.dp, borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasWarn) "⚠️ 告警 · ${tslResult.protocolName}" else "物模型 · ${tslResult.protocolName}",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                        )
                    }

                    // 物理量横向紧凑平铺
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tslResult.values.take(4).forEach { v ->
                            val valColor = if (v.isWarning) Color(0xFFDC2626) else PrimaryBlack
                            Text(
                                text = "${v.name}: ${v.displayValue}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (v.isWarning) FontWeight.Bold else FontWeight.Medium,
                                    color = valColor
                                )
                            )
                        }
                        if (tslResult.values.size > 4) {
                            Text(
                                text = "+${tslResult.values.size - 4}",
                                style = TextStyle(fontSize = 10.sp, color = OnSurfaceVariantGray)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 全功能报文详情弹窗 (真正打开详情，默认展示完整格式化 JSON 与语法高亮，并提供快捷格式化开关与复制)
 */
@Composable
private fun MessageDetailsModalDialog(
    packet: MqttLogPacket,
    tslResult: com.example.model.TslParseResult? = null,
    onDismiss: () -> Unit,
    onInspectWithAi: () -> Unit,
    onCopyTopic: () -> Unit,
    onCopyPayload: () -> Unit,
    onCopyValue: (String) -> Unit,
    onLoadIntoPublish: () -> Unit
) {
    // 弹窗内部默认开启 JSON 格式化排版高亮，支持一键切换原始文本
    var isFormatPretty by remember { mutableStateOf(true) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    // 弹窗内微交互反馈状态（彻底解决底层 Snackbar 被 Dialog 遮挡的问题，并提供原地瞬时反馈）
    var isTopicCopied by remember { mutableStateOf(false) }
    var isPayloadCopied by remember { mutableStateOf(false) }
    var copiedMetricKey by remember { mutableStateOf<String?>(null) }
    var inlineNotice by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun triggerCopyTopic() {
        onCopyTopic()
        isTopicCopied = true
        inlineNotice = "已复制主题至剪贴板"
        coroutineScope.launch {
            delay(1800)
            isTopicCopied = false
            if (inlineNotice == "已复制主题至剪贴板") {
                inlineNotice = null
            }
        }
    }

    fun triggerCopyPayload() {
        onCopyPayload()
        isPayloadCopied = true
        inlineNotice = "已复制完整消息内容"
        coroutineScope.launch {
            delay(1800)
            isPayloadCopied = false
            if (inlineNotice == "已复制完整消息内容") {
                inlineNotice = null
            }
        }
    }

    fun triggerCopyValue(key: String, fullText: String) {
        onCopyValue(fullText)
        copiedMetricKey = key
        inlineNotice = "已复制: $fullText"
        coroutineScope.launch {
            delay(1800)
            if (copiedMetricKey == key) {
                copiedMetricKey = null
            }
            if (inlineNotice == "已复制: $fullText") {
                inlineNotice = null
            }
        }
    }

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
                .heightIn(max = maxDialogHeight)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(18.dp)
            ) {
                // 1. 顶部固定 Header (标题与关闭按钮常驻)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "消息详情",
                            style = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                        if (packet.packetSeq.isNotEmpty()) {
                            Text(
                                text = "#${packet.packetSeq}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariantGray
                                )
                            )
                        }
                    }
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

                // 弹窗内部原位复制成功提示条 (动态展开/收起，解决底层 Snackbar 遮挡痛点)
                AnimatedVisibility(
                    visible = inlineNotice != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    inlineNotice?.let { notice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFECFDF5))
                                .border(0.8.dp, Color(0xFFA7F3D0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = notice,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF065F46)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 中间滚动区域 (weight(1f, fill = false) 紧凑包裹内容，长消息时开启内部滚动)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 工业级 MQTT 主题卡片：精致层级、原位复制微交互、支持整块轻触复制与自由选中
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isTopicCopied) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                            .border(
                                width = 1.dp,
                                color = if (isTopicCopied) Color(0xFF86EFAC) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { triggerCopyTopic() }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 顶部属性条：TOPIC 徽章 + QoS 徽章 + 大小 + 原位复制胶囊按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // MQTT TOPIC 极客黑标
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "TOPIC",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }

                                // QoS 药丸
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE2E8F0))
                                        .padding(horizontal = 5.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "QoS ${packet.qos}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF475569)
                                        )
                                    )
                                }

                                if (packet.sizeText.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    ) {
                                        Text(
                                            text = packet.sizeText,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF64748B)
                                            )
                                        )
                                    }
                                }

                                if (packet.devInfo.contains("Retain", ignoreCase = true)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFEF3C7))
                                            .border(0.6.dp, Color(0xFFFDE68A), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    ) {
                                        Text(
                                            text = "Retain",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFD97706)
                                            )
                                        )
                                    }
                                }
                            }

                            // 独立原位复制胶囊按钮 (点击瞬间变绿 + 对勾微动效)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isTopicCopied) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isTopicCopied) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { triggerCopyTopic() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTopicCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "复制主题",
                                    tint = if (isTopicCopied) Color(0xFF15803D) else Color(0xFF475569),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isTopicCopied) "已复制 ✓" else "复制主题",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (isTopicCopied) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isTopicCopied) Color(0xFF15803D) else Color(0xFF475569)
                                    )
                                )
                            }
                        }

                        // 主题内容：Monospace，加粗高对比度，支持完整自然换行与自由选中
                        SelectionContainer {
                            Text(
                                text = packet.topic,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    color = if (isTopicCopied) Color(0xFF166534) else PrimaryBlack
                                )
                            )
                        }

                        // 贴心微提示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "轻触卡片亦可一键复制",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    // TSL 物模型解析指标看板 (如果命中了协议)
                    if (tslResult != null && tslResult.values.isNotEmpty()) {
                        val hasWarn = tslResult.hasWarnings
                        val cardBg = if (hasWarn) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                        val borderC = if (hasWarn) Color(0xFFFECACA) else Color(0xFFDCFCE7)
                        val titleC = if (hasWarn) Color(0xFFDC2626) else Color(0xFF16A34A)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .border(0.8.dp, borderC, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasWarn) "⚠️ 越限告警 · ${tslResult.protocolName}" else "物模型解析 · ${tslResult.protocolName}",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = titleC
                                    )
                                )
                                Text(
                                    text = "共 ${tslResult.values.size} 项指标",
                                    style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray)
                                )
                            }

                            // 逐个物理量网格/列表呈现 (点击支持原位微交互复制)
                            tslResult.values.forEach { v ->
                                val isThisMetricCopied = copiedMetricKey == v.name
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isThisMetricCopied) Color(0xFFDCFCE7).copy(alpha = 0.6f) else Color.Transparent)
                                        .clickable { triggerCopyValue(v.name, "${v.name}: ${v.displayValue}") }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (v.isWarning) {
                                            Text(
                                                text = "⚠️",
                                                style = TextStyle(fontSize = 11.sp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = v.name,
                                            style = TextStyle(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (v.isWarning) Color(0xFFDC2626) else PrimaryBlack
                                            )
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isThisMetricCopied) {
                                            Text(
                                                text = "已复制 ✓",
                                                style = TextStyle(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = v.displayValue,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (v.isWarning) Color(0xFFDC2626) else PrimaryBlack
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Message Payload Header with JSON 格式化开关 & 复制消息 buttons (格式化开关挪到这里，并列于复制消息旁边)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // JSON 格式化工具 (极简设计：仅保留 "JSON"，激活高亮，未激活线框)
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFormatPretty) PrimaryBlack else Color.Transparent)
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isFormatPretty) PrimaryBlack else OutlineVariantLight,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { isFormatPretty = !isFormatPretty }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "JSON",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFormatPretty) Color.White else OnSurfaceVariantGray
                                    )
                                )
                            }

                            // 独立原位复制消息胶囊按钮
                            Row(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPayloadCopied) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isPayloadCopied) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { triggerCopyPayload() }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPayloadCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "复制消息",
                                    tint = if (isPayloadCopied) Color(0xFF15803D) else PrimaryBlack,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isPayloadCopied) "已复制 ✓" else "复制消息",
                                    style = TextStyle(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPayloadCopied) Color(0xFF15803D) else PrimaryBlack
                                    )
                                )
                            }
                        }
                    }

                    // Full VS Code / GitHub JSON Highlighter View
                    JsonCodeBlockView(
                        rawText = packet.payload,
                        isJsonPretty = isFormatPretty,
                        showLineNumbers = isFormatPretty,
                        maxLines = Int.MAX_VALUE,
                        onLineClick = { lineText -> triggerCopyValue("代码行", lineText) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineVariantLight, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // 3. 底部绝对常驻操作栏 (关闭, AI 智能透视, 载入发布页) - 无论内容多长，100% 完整显示在底部，绝不被截断
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, OutlineVariantLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlack
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "关闭",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                    }

                    Button(
                        onClick = onInspectWithAi,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI 智能透视",
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryWhite
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onLoadIntoPublish,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, OutlineVariantLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlack
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "去发布",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
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
 * 现代极简主题过滤规则弹窗 (纯黑白现代高级质感，对齐订阅弹窗美学标准)
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
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 纯净 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "主题过滤规则",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 16.sp
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OutlineGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. 分段切换轨道 (严格 34dp 黄金轨道，内嵌微胶囊绝对几何居中)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(3.dp),
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
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) PrimaryBlack else Color.Transparent)
                                .clickable {
                                    activeTab = tabKey
                                    inputPattern = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }

                // 3. 内嵌式一体化输入框 (高 38dp，内嵌通配符药丸微胶囊 + 紧凑添加按钮)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                            .border(0.8.dp, OutlineVariantLight, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = inputPattern,
                            onValueChange = { inputPattern = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_pattern_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                color = PrimaryBlack,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (inputPattern.isEmpty()) {
                                    Text(
                                        text = if (isExclude) "输入排除规则，如 test/+" else "输入包含规则，如 sensor/#",
                                        style = TextStyle(fontSize = 11.5.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // 内嵌微型通配符药丸胶囊 (+ / #)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceContainerLow)
                                    .clickable {
                                        val t = inputPattern.trim()
                                        inputPattern = if (t.isEmpty()) "+" else if (t.endsWith("/")) "$t+" else "$t/+"
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlack,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceContainerLow)
                                    .clickable {
                                        val t = inputPattern.trim()
                                        inputPattern = if (t.isEmpty()) "#" else if (t.endsWith("/")) "$t#" else "$t/#"
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlack,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }

                        if (inputPattern.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空输入",
                                tint = OutlineGray,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { inputPattern = "" }
                            )
                        }
                    }

                    // 添加按钮 (高 38dp)
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("filter_pattern_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "添加",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }

                // 4. 当前规则列表区域
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已配置 ${currentList.size} 条${if (isExclude) "排除" else "包含"}规则",
                            style = TextStyle(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariantGray
                            )
                        )
                        if (currentList.isNotEmpty()) {
                            Text(
                                text = "清空当前",
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariantGray
                                ),
                                modifier = Modifier.clickable {
                                    if (isExclude) onClearExcludes() else onClearIncludes()
                                }
                            )
                        }
                    }

                    if (currentList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.5f))
                                .border(0.6.dp, OutlineVariantLight, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isExclude) "暂无排除规则，添加后将隐藏匹配消息" else "暂无包含规则，添加后将仅保留匹配消息",
                                style = TextStyle(fontSize = 11.5.sp, color = OutlineGray)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentList.forEachIndexed { index, pattern ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceContainerLow)
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除规则",
                                        tint = OnSurfaceVariantGray,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable {
                                                if (isExclude) onRemoveExclude(index) else onRemoveInclude(index)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. 底部操作按钮：高 38dp 纯黑【完成】按钮
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text(
                        text = "完成",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 现场通信健康度主动巡检雷达卡片 (Proactive Watchdog Strip)
 */
@Composable
private fun LiveHealthWatchdogStrip(
    state: LiveHealthWatchdogState,
    overflowCount: Long = 0L,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onInspectAnomaly: (WatchdogAnomaly) -> Unit,
    onGenerateReport: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (state.isHealthy) SurfaceContainerLowest else Color(0xFFFFFBEB),
        border = BorderStroke(
            0.6.dp,
            if (state.isHealthy) OutlineVariantLight else Color(0xFFFDE68A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            // Header Row: 状态指示点 + 网关/速率概览 + 展开按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isHealthy) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Text(
                        text = if (state.isHealthy) "现场指标与链路正常" else "发现 ${state.anomalyCount} 处指标越限/异常",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isHealthy) PrimaryBlack else Color(0xFFB45309)
                        )
                    )
                    Text(
                        text = "· ${state.activeGatewayCount} 个网关 · ~${state.packetRatePerMin} pkt/min" +
                                if (overflowCount > 0) " · 缓冲已覆盖 ${overflowCount} 条" else "",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = if (overflowCount > 0) Color(0xFFD97706) else OnSurfaceVariantGray,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "展开巡检雷达",
                        tint = OnSurfaceVariantGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Panel: 异常事件抓包与报告生成动作
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = OutlineVariantLight, thickness = 0.5.dp)

                    if (overflowCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7).copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚡ 内存环形队列已稳定滚动覆盖 $overflowCount 条旧报文（保障现场高频通信零卡顿）",
                                style = TextStyle(fontSize = 10.5.sp, color = Color(0xFFB45309), fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (state.anomalies.isEmpty()) {
                        Text(
                            text = "各网关数据链路与心跳正常，未检测到丢包、报错标志或超时体征。",
                            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray)
                        )
                    } else {
                        Text(
                            text = "越限告警与异常事件清单（轻触直接调用 AI 深度诊断）:",
                            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            state.anomalies.take(3).forEach { anomaly ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceContainerLow)
                                        .clickable { onInspectAnomaly(anomaly) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = anomaly.topic,
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = PrimaryBlack
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${anomaly.reason} · ${anomaly.timestamp}",
                                            style = TextStyle(fontSize = 10.sp, color = Color(0xFFDC2626))
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "AI 诊断",
                                        tint = PrimaryBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部操作区：一键生成交付报告
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onGenerateReport,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.6.dp, PrimaryBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlack),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "一键生成现场验收报告", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI 报文透视与逆向解码工作台弹窗 (AiPacketInspectorDialog)
 */
@Composable
private fun AiPacketInspectorDialog(
    packet: MqttLogPacket,
    result: String,
    isInspecting: Boolean,
    thinkingText: String,
    onDismiss: () -> Unit,
    onSaveAsRule: (name: String, desc: String) -> Unit,
    onContinueInChat: () -> Unit,
    onCopyResult: () -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    var showSaveRuleModal by remember { mutableStateOf(false) }
    var ruleNameInput by remember { mutableStateOf(packet.topic.substringAfterLast('/') + " 协议规则") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = maxHeight)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
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
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI 报文透视与解码",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                    }
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

                Spacer(modifier = Modifier.height(10.dp))

                // Scroll Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Packet meta
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "主题: ${packet.topic}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "原始载荷: ${packet.payload}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = OnSurfaceVariantGray
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Loading or Markdown Output
                    if (isInspecting && result.isBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ClaudeOrbitLoading(modifier = Modifier.size(36.dp))
                            Text(
                                text = "正在匹配协议知识库与逆向切片...",
                                style = TextStyle(fontSize = 12.5.sp, color = OnSurfaceVariantGray)
                            )
                        }
                    } else {
                        SelectionContainer {
                            MarkdownRenderer(
                                content = result,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OutlineVariantLight, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCopyResult,
                        enabled = result.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "复制结果", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = { showSaveRuleModal = true },
                        enabled = result.isNotBlank(),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "沉淀为规则", fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = onContinueInChat,
                        enabled = result.isNotBlank(),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "深入追问", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    if (showSaveRuleModal) {
        AlertDialog(
            onDismissRequest = { showSaveRuleModal = false },
            title = { Text("沉淀为此主题协议规则", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "将当前报文解码特征保存至本地私有协议库，后续该设备或主题的所有报文将自动应用该规则：",
                        fontSize = 12.sp,
                        color = OnSurfaceVariantGray
                    )
                    OutlinedTextField(
                        value = ruleNameInput,
                        onValueChange = { ruleNameInput = it },
                        label = { Text("协议规则名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAsRule(ruleNameInput, result)
                        showSaveRuleModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
                ) {
                    Text("保存沉淀", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRuleModal = false }) {
                    Text("取消", color = PrimaryBlack)
                }
            }
        )
    }
}

