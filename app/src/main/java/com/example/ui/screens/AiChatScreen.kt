package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
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
    var showProtocolDialog by remember { mutableStateOf(false) }
    var showSessionMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<com.example.model.AiChatSession?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val pendingQueue by viewModel.pendingAiPromptQueue.collectAsState()
    val listState = rememberLazyListState()

    // 消息更新或流式吐字时自动滚动到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, actionStatus) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val context = LocalContext.current

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importExternalExcel(context, uri)
        }
    }

    val launchExcelPicker = remember {
        {
            try {
                excelPickerLauncher.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "*/*"
                    )
                )
            } catch (_: Exception) {
                viewModel.showToast("未能调起系统文件选择器")
            }
        }
    }
    if (showProtocolDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            protocols = protocols,
            onDismiss = { showProtocolDialog = false },
            onSaveConfig = { viewModel.updateAiConfig(it) },
            onSaveProtocol = { viewModel.saveProtocolKnowledge(it) },
            onDeleteProtocol = { viewModel.deleteProtocolKnowledge(it) },
            onBatchImportProtocols = { viewModel.importBatchProtocols(it) },
            onExportProtocols = { viewModel.exportProtocolsToJson(context) },
            onCopyProtocolsToken = { viewModel.copyProtocolsToken(context) },
            onImportProtocolsFromClipboard = { viewModel.importProtocolsFromClipboard(context) },
            onlyProtocol = true
        )
    }

    // 未配置 API Key 应急配置弹窗
    if (showSettingsDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            protocols = protocols,
            onDismiss = { showSettingsDialog = false },
            onSaveConfig = { viewModel.updateAiConfig(it) },
            onSaveProtocol = { viewModel.saveProtocolKnowledge(it) },
            onDeleteProtocol = { viewModel.deleteProtocolKnowledge(it) },
            onBatchImportProtocols = { viewModel.importBatchProtocols(it) },
            onExportProtocols = { viewModel.exportProtocolsToJson(context) },
            onCopyProtocolsToken = { viewModel.copyProtocolsToken(context) },
            onImportProtocolsFromClipboard = { viewModel.importProtocolsFromClipboard(context) },
            onlyProtocol = false
        )
    }

    // 删除会话二次确认弹窗
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "删除对话确认",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
            },
            text = {
                Text(
                    text = "确认删除对话「${sessionToDelete?.title}」？\n删除后该对话的所有历史记录与分析数据将永久删除，不可恢复。",
                    style = TextStyle(fontSize = 13.5.sp, color = OnSurfaceDark, lineHeight = 19.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDeleteId = sessionToDelete?.id
                        sessionToDelete = null
                        if (toDeleteId != null) {
                            viewModel.deleteAiSession(toDeleteId)
                        }
                    }
                ) {
                    Text("确认删除", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 清空当前消息二次确认弹窗
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "清空对话记录",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
            },
            text = {
                Text(
                    text = "确认清空当前对话的所有消息吗？此操作无法撤销。",
                    style = TextStyle(fontSize = 13.5.sp, color = OnSurfaceDark)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAiMessages()
                    }
                ) {
                    Text("清空", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp)
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
                                                        sessionToDelete = session
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
                                        showClearConfirmDialog = true
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
                                            sessionToDelete = currentSession
                                        }
                                    )
                                }
                                 HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text("📂 导入外部 Excel 日志", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        launchExcelPicker()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val hasAccess = viewModel.hasAllFilesAccess(context)
                                        Text(
                                            if (hasAccess) "🛡️ 所有文件权限已开启" else "🛡️ 授予所有文件管理权限",
                                            fontSize = 13.5.sp,
                                            color = if (hasAccess) Color(0xFF10B981) else PrimaryBlack
                                        )
                                    },
                                    leadingIcon = {
                                        val hasAccess = viewModel.hasAllFilesAccess(context)
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (hasAccess) Color(0xFF10B981) else PrimaryBlack
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (viewModel.hasAllFilesAccess(context)) {
                                            viewModel.showToast("已拥有所有文件访问权限，Agent 可自动扫描所有公共下载目录")
                                        } else {
                                            viewModel.openAllFilesAccessSettings(context)
                                        }
                                    }
                                )
                                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text("生成现场验收报告", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.generateFieldAcceptanceReport()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("协议澄清规则库", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showProtocolDialog = true
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
                    onImportExcel = launchExcelPicker,
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
                            thinkingContent = if (msg.id == messages.lastOrNull()?.id && msg.role == "assistant" && msg.isThinking) thinkingText else msg.reasoningContent,
                            onCopy = { text, label -> viewModel.copyToClipboard(text, label) },
                            onRetry = { errorId -> viewModel.retryAiMessage(errorId) }
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
                // 追问等待队列状态条
                if (pendingQueue.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = PrimaryBlack)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "已排队 ${pendingQueue.size} 条追问，待当前任务完成后顺延执行...",
                                style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceDark),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "清空",
                            style = TextStyle(fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold),
                            modifier = Modifier
                                .clickable { viewModel.clearPendingAiQueue() }
                                .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                }

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
                                    text = if (isResponding) "当前回复中，输入可排队追问..." else "询问网关报文、体征异常、Excel趋势...",
                                    style = TextStyle(fontSize = 13.5.sp, color = OutlineGray)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSend = inputText.isNotBlank()
                    if (canSend) {
                        // 有输入内容时，无论是否正在生成均支持发送（生成中自动进入追问队列）
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack)
                                .clickable {
                                    val toSend = inputText
                                    inputText = ""
                                    viewModel.sendAiMessage(toSend)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else if (isResponding) {
                        // 输入框无内容且正在生成时，显示停止按钮
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
                        // 禁用发送态
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerDefault),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = OutlineGray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
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
    onImportExcel: () -> Unit,
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
                    tag = "工程交付",
                    title = "现场验收工程报告",
                    desc = "一键生成通信验收报告",
                    prompt = "请全面盘点当前 MQTT Broker 采集到的所有网关数据、内存实时流与通信质量，生成一份标准的《MQTT 工业物联网现场验收与排查工程报告》。请调用工具查询真实数据，报告必须包含：1. 现场工程概况；2. 网关与设备在线清单及吞吐；3. 通信质量与连通性评估；4. 业务指标与私有协议解码审计；5. 整改建议与交付验收结论。",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                SuggestionCard(
                    tag = "异常巡检",
                    title = "现场体征排查",
                    desc = "排查心跳失联与告警",
                    prompt = "基于内存实时流与本地私有协议库，全面排查各网关与设备的心跳失联、报错报文与异常体征数据",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuggestionCard(
                    tag = "双向联调",
                    title = "自然语言 Mock 发包",
                    desc = "AI 构造心跳并一键下发",
                    prompt = "帮我构造一条心跳上报 Mock 报文，并调用 publish_mqtt_message 直接发布到当前网关主题",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                SuggestionCard(
                    tag = "离线归档",
                    title = "Excel 穿透分析",
                    desc = "读取已归档或导入日志",
                    prompt = "查看已转储或导入的 Excel 历史报文，流式分析总行数与热门主题宏观画像",
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 外部微信/电脑 Excel 文件一键免权限导入快捷条
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onImportExcel)
                .background(SurfaceContainerLow)
                .border(0.6.dp, OutlineVariantLight, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = PrimaryBlack)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "外部微信/电脑 Excel 文件找不到？点此免权限导入",
                style = TextStyle(fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
            )
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
    thinkingContent: String,
    onCopy: (String, String) -> Unit,
    onRetry: (String) -> Unit
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
            modifier = Modifier.widthIn(max = 315.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            val hasContent = message.content.isNotBlank()

            // 1. Tool Calling 或 理解意图状态卡片 (仅当正在执行动作，或正文尚未产生且非报错时展示)
            val effectiveStatus = if (actionStatus.isNotBlank()) {
                actionStatus
            } else if (!hasContent && !isUser && !message.isError) {
                "🤖 正在理解意图..."
            } else {
                ""
            }

            if (!isUser && effectiveStatus.isNotBlank() && (!hasContent || actionStatus.isNotBlank())) {
                AiActionOrbitStatusCard(
                    statusText = effectiveStatus,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // 2. Reasoning Thinking Block (DeepSeek-R1 / Qwen 等思考链)
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

            // 3. 消息主体气泡 (彻底消除双气泡！正文未到达前绝不渲染下方空气泡)
            if (isUser || hasContent) {
                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) PrimaryBlack else if (message.isError) Color(0xFFFEF2F2) else SurfaceContainerLowest
                    ),
                    border = if (isUser) null else BorderStroke(0.8.dp, if (message.isError) Color(0xFFFCA5A5) else OutlineVariantLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        SelectionContainer {
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

                        // AI 回复卡片底部操作栏 (复制全文)
                        if (!isUser && !message.isError && message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onCopy(message.content, "AI回复") }
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制",
                                        tint = OnSurfaceVariantGray,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "复制",
                                        style = TextStyle(fontSize = 10.sp, color = OnSurfaceVariantGray)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 网络中断 / 异常重试胶囊按钮 (用户可随时一键重试)
            if (!isUser && message.isError) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaryBlack)
                        .clickable { onRetry(message.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试",
                        tint = OnPrimaryWhite,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "网络异常 · 点击重试",
                        style = TextStyle(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryWhite
                        )
                    )
                }
            }
        }
    }
}
