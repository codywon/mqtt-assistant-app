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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.components.AiActionOrbitStatusCard
import com.example.ui.components.AiTypingIndicatorBubble
import com.example.ui.components.ClaudeOrbitLoading
import com.example.ui.components.MarkdownRenderer
import com.example.ui.components.OpenAiWaveDotsLoading
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

    val sessions by viewModel.aiSessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val currentSession = sessions.find { it.id == currentSessionId } ?: sessions.firstOrNull()

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSessionMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSessionTitle by remember { mutableStateOf("") }

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

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = "重命名对话",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
            },
            text = {
                BasicTextField(
                    value = renameSessionTitle,
                    onValueChange = { renameSessionTitle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = TextStyle(fontSize = 14.sp, color = PrimaryBlack),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameSessionTitle.isNotBlank()) {
                            viewModel.renameAiSession(currentSessionId, renameSessionTitle.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("确定", color = PrimaryBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .imePadding()
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
                        .height(54.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Button
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: Session Title & Switcher Dropdown
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSessionMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentSession?.title?.ifBlank { "新对话" } ?: "新对话",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlack
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showSessionMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "切换对话",
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Sessions Dropdown Menu
                        DropdownMenu(
                            expanded = showSessionMenu,
                            onDismissRequest = { showSessionMenu = false },
                            modifier = Modifier
                                .widthIn(min = 230.dp, max = 290.dp)
                                .background(SurfaceContainerLowest)
                        ) {
                            Text(
                                text = "历史对话",
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariantGray),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)

                            sessions.forEach { session ->
                                val isSelected = session.id == currentSessionId
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = session.title,
                                                style = TextStyle(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) PrimaryBlack else OnSurfaceDark
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (sessions.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteAiSession(session.id)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = OutlineGray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.switchAiSession(session.id)
                                        showSessionMenu = false
                                    },
                                    modifier = if (isSelected) Modifier.background(SurfaceContainerLow) else Modifier
                                )
                            }

                            HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = PrimaryBlack,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "新建对话",
                                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.createNewAiSession()
                                    showSessionMenu = false
                                }
                            )
                        }
                    }

                    // Right: New Chat (+) and More Menu (···)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.createNewAiSession() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建对话",
                                tint = PrimaryBlack,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    tint = PrimaryBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(SurfaceContainerLowest)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名当前对话", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        renameSessionTitle = currentSession?.title ?: ""
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空当前消息", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.clearAiMessages()
                                    }
                                )
                                if (sessions.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("删除此对话", fontSize = 13.5.sp, color = Color(0xFFDC2626)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFDC2626))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.deleteAiSession(currentSessionId)
                                        }
                                    )
                                }
                                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text("AI 设置与协议澄清", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showSettingsDialog = true
                                    }
                                )
                            }
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
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(PrimaryBlack.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OnPrimaryWhite,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "有什么我可以帮您分析的？",
            style = TextStyle(fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack, letterSpacing = (-0.2).sp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "内嵌 SI 智能体 · 检索本地 SQLite · 解码硬件私有协议 · 穿透归档 Excel",
            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (!isConfigured) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFEF3C7))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "⚙️ 尚未配置模型 API Key，点击立即配置",
                    style = TextStyle(fontSize = 11.5.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // 2x2 高级极简场景建议卡片网格
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuggestionCard(
                    tag = "流量聚合",
                    title = "网关报文统计",
                    desc = "统计各网关吞吐与频次",
                    prompt = "统计当前 SQLite 数据库中各网关收到的报文总数与频次",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                SuggestionCard(
                    tag = "健康筛查",
                    title = "异常体征排查",
                    desc = "排查超标血压与心率",
                    prompt = "基于私有协议筛查最新收到的体征数据，排查是否有高血压或心率异常",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuggestionCard(
                    tag = "离线分卷",
                    title = "Excel 穿透分析",
                    desc = "读取 Download 归档报文",
                    prompt = "查看系统 Download 目录下已转储的 Excel 历史报文并汇总趋势",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                SuggestionCard(
                    tag = "硬件协议",
                    title = "私有规则核对",
                    desc = "人话测试 Hex 报文结构",
                    prompt = "测试并核对当前硬件私有协议知识库，告诉我支持哪些 Hex 报文规则",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    tag: String,
    title: String,
    desc: String,
    prompt: String,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(0.7.dp, OutlineVariantLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.clickable { onClick(prompt) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariantGray)
                    )
                }
                Text(
                    text = "↗",
                    style = TextStyle(fontSize = 12.sp, color = OutlineGray, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = title,
                style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray),
                maxLines = 1,
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
            // Tool Calling Action Chip (如果正在调用工具，展示 Claude 轨迹公转 + 状态文字)
            if (!isUser && actionStatus.isNotBlank()) {
                AiActionOrbitStatusCard(
                    statusText = actionStatus,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
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
            val hasContent = message.content.isNotBlank()
            val isThinkingOnly = message.content.isBlank() && message.isThinking
            val isActionExecuting = message.content.isBlank() && actionStatus.isNotBlank()

            if (hasContent || isThinkingOnly || isActionExecuting || isUser) {
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
                        if (message.content.isBlank() && (message.isThinking || actionStatus.isNotBlank())) {
                            // 动态灵动波浪与公转轨迹指示器，彻底替换静态的 ● ● ●
                            AiTypingIndicatorBubble()
                        } else {
                            if (isUser) {
                                Text(
                                    text = message.content,
                                    style = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = OnPrimaryWhite,
                                        lineHeight = 19.sp
                                    )
                                )
                            } else {
                                MarkdownRenderer(
                                    content = message.content,
                                    isUser = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
