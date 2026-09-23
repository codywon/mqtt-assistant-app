package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiChatMessage
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
import com.example.viewmodel.MqttAssistantViewModel

/**
 * 完整沉浸式 ChatGPT App 风格的 AI 智能数据分析界面：
 * 1. 顶部专业 TopBar（返回、标题、在线指示绿点、清空会话、设置/协议工作台）；
 * 2. 消息流支持流式打字机输出、Tool-Calling 实时状态芯片、DeepSeek-R1 思考链折叠展开；
 * 3. 底部胶囊输入框自适应扩展，支持发送、停止响应与快捷场景引导 Pill。
 */
@Composable
fun AiChatScreen(
    viewModel: MqttAssistantViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.aiMessages.collectAsState()
    val isResponding by viewModel.isAiResponding.collectAsState()
    val actionStatus by viewModel.currentAiActionStatus.collectAsState()
    val thinkingText by viewModel.currentAiThinkingText.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()
    val protocols by viewModel.protocolKnowledgeList.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 消息更新或流式吐字时自动滚动到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, actionStatus) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showSettingsDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            protocols = protocols,
            onDismiss = { showSettingsDialog = false },
            onSaveConfig = { viewModel.updateAiConfig(it) },
            onSaveProtocol = { viewModel.saveProtocolKnowledge(it) },
            onDeleteProtocol = { viewModel.deleteProtocolKnowledge(it) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
    ) {
        // ==========================================
        // 1. ChatGPT-Style TopBar
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 1.dp, spotColor = Color.Black.copy(alpha = 0.05f)),
            color = SurfaceContainerLowest.copy(alpha = 0.98f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = PrimaryBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SI 数据分析专家",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlack
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (aiConfig.apiKey.isNotBlank()) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                            }
                            Text(
                                text = if (aiConfig.apiKey.isNotBlank()) "${aiConfig.modelName} · SQLite & Excel 工具已挂载" else "未配置 API Key · 点击右侧设置",
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariantGray
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top Action Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.clearAiMessages() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空会话",
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(34.dp),
                            enabled = true
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置与协议澄清",
                                tint = PrimaryBlack,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
            }
        }

        // ==========================================
        // 2. Chat Conversation Message Stream
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (messages.isEmpty()) {
                // Empty Welcome View with Suggestion Pills
                AiEmptyWelcomeView(
                    onPillClick = { suggestion ->
                        viewModel.sendAiMessage(suggestion)
                    },
                    onOpenSettings = { showSettingsDialog = true },
                    isConfigured = aiConfig.apiKey.isNotBlank()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AiMessageBubble(
                            message = msg,
                            isLastMessage = (msg.id == messages.lastOrNull()?.id),
                            actionStatus = if (msg.id == messages.lastOrNull()?.id && msg.role == "assistant") actionStatus else "",
                            thinkingContent = if (msg.id == messages.lastOrNull()?.id && msg.role == "assistant" && msg.isThinking) thinkingText else msg.reasoningContent
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // ==========================================
        // 3. ChatGPT-Style Floating Input Bar
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            color = SurfaceContainerLowest,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = PrimaryBlack,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(PrimaryBlack),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "询问网关报文、体征异常、Excel趋势...",
                                    style = TextStyle(fontSize = 13.5.sp, color = OutlineGray)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isResponding) {
                        // Stop Generating Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack)
                                .clickable { viewModel.stopAiResponse() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止响应",
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        // Send Button
                        val canSend = inputText.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (canSend) PrimaryBlack else SurfaceContainerDefault)
                                .clickable(enabled = canSend) {
                                    val toSend = inputText
                                    inputText = ""
                                    viewModel.sendAiMessage(toSend)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend) OnPrimaryWhite else OutlineGray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Disclaimer caption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SI Agent 具备只读检索本地 SQLite 与历史归档 Excel 能力",
                        style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
                    )
                }
            }
        }
    }
}

/**
 * 欢迎空态卡片与 4 大业务引导胶囊
 */
@Composable
private fun AiEmptyWelcomeView(
    onPillClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    isConfigured: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = PrimaryBlack,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "MQTT 数据智能分析专家",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "随时针对接收到的体征报文、设备流量或归档 Excel 进行深度洞察",
            style = TextStyle(fontSize = 12.5.sp, color = OnSurfaceVariantGray, lineHeight = 17.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (!isConfigured) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFEF3C7))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚠️ 尚未配置大模型 API Key，点击立即配置",
                    style = TextStyle(fontSize = 11.5.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Suggestion Pill Chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionPill("📊 统计当前 SQLite 数据库中各网关收到的报文总数") { onPillClick(it) }
            SuggestionPill("⚠️ 筛查最新收到的体征数据，排查是否有高血压或心率异常") { onPillClick(it) }
            SuggestionPill("📁 查看 Download 目录下已导出的 Excel 历史报文并汇总") { onPillClick(it) }
            SuggestionPill("📖 查看当前已录入的硬件私有 Hex 协议澄清规则") { onPillClick(it) }
        }
    }
}

@Composable
private fun SuggestionPill(text: String, onClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(0.8.dp, OutlineVariantLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(text) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = TextStyle(fontSize = 12.5.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 单条消息气泡（用户纯黑气泡 / AI 纯白高级边框卡片）
 */
@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    isLastMessage: Boolean,
    actionStatus: String,
    thinkingContent: String
) {
    val isUser = message.role == "user"

    var isThinkingExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI 头像星辉
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OnPrimaryWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Tool Calling Action Chip (如果正在调用工具)
            if (!isUser && actionStatus.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionStatus,
                        style = TextStyle(fontSize = 11.5.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
                    )
                }
            }

            // Reasoning Thinking Block (DeepSeek-R1 / Qwen 等思考链)
            if (!isUser && thinkingContent.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow.copy(alpha = 0.5f)),
                    border = BorderStroke(0.6.dp, OutlineVariantLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = OnSurfaceVariantGray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (message.isThinking) "Agent 深度思考中..." else "思考推理过程",
                                    style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, fontWeight = FontWeight.Medium)
                                )
                            }
                            Icon(
                                imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isThinkingExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                text = thinkingContent,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = OnSurfaceDark,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Main Message Bubble
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) PrimaryBlack else SurfaceContainerLowest
                ),
                border = if (isUser) null else BorderStroke(0.8.dp, OutlineVariantLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.content.isBlank() && message.isThinking) {
                        Text(
                            text = "● ● ●",
                            style = TextStyle(fontSize = 12.sp, color = OutlineGray, letterSpacing = 2.sp)
                        )
                    } else {
                        Text(
                            text = message.content,
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                color = if (isUser) OnPrimaryWhite else if (message.isError) Color(0xFFDC2626) else PrimaryBlack,
                                lineHeight = 19.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
