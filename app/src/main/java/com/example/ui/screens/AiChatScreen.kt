package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val activeRadarTrap by viewModel.activeRadarTrap.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.example.model.MqttConnectionState.CONNECTED
    val livePackets by viewModel.livePackets.collectAsState()
    val tslProtocols by viewModel.tslProtocols.collectAsState()

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
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    val pendingQueue by viewModel.pendingAiPromptQueue.collectAsState()
    val listState = rememberLazyListState()
    val chatCoroutineScope = rememberCoroutineScope()
    var userScrolledUp by remember { mutableStateOf(false) }

    // 智能检测用户是否手动向上翻看历史消息
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 1
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            if (!isAtBottom) {
                userScrolledUp = true
            }
        } else {
            if (isAtBottom) {
                userScrolledUp = false
            }
        }
    }

    // 消息更新、流式吐字、思考展开或动作状态切换时的极速无打断贴底追踪 (确保超长文本底部始终在可视区)
    val lastMsgLength = messages.lastOrNull()?.content?.length ?: 0
    val thinkingLength = thinkingText.length
    LaunchedEffect(messages.size, lastMsgLength, thinkingLength, actionStatus) {
        if (messages.isNotEmpty() && !userScrolledUp) {
            listState.scrollToItem(messages.size, scrollOffset = 100000)
        }
    }

    val context = LocalContext.current

    var attachedFile by remember { mutableStateOf<AttachedFileInfo?>(null) }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val info = getFileInfoFromUri(context, uri)
            if (info != null) {
                attachedFile = info
            }
        }
    }

    val launchAttachmentPicker = remember {
        {
            try {
                attachmentPickerLauncher.launch(
                    arrayOf(
                        "*/*"
                    )
                )
            } catch (_: Exception) {
                viewModel.showToast("未能调起系统文件选择器")
            }
        }
    }

    val launchExcelPicker = launchAttachmentPicker
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
            onImportProtocolsFromFile = { viewModel.importProtocolsFromUri(context, it) },
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
            onImportProtocolsFromFile = { viewModel.importProtocolsFromUri(context, it) },
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

    // 方案B：全盘文件管理权限一键开启引导弹窗 (原地直读，免文件拷贝)
    if (showPermissionGuideDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            title = {
                Text(
                    text = "开启所有文件访问权限",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
            },
            text = {
                Text(
                    text = "开启后，AI 智能体将能够直接原地扫描并分析公共 Download、微信与 QQ 接收的 Excel 报文日志。\n\n✨ 核心优势：\n• 原地直接只读分析，绝不产生重复拷贝；\n• 零多余存储占用，支持万条大表毫秒级穿透；\n• 随拷贝随问，AI 自动感知，彻底免去手动选文件。",
                    style = TextStyle(fontSize = 13.5.sp, color = OnSurfaceDark, lineHeight = 20.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionGuideDialog = false
                        viewModel.openAllFilesAccessSettings(context)
                    }
                ) {
                    Text("前往系统设置开启", color = PrimaryBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuideDialog = false }) {
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
                                    text = { Text("私有协议规则库", fontSize = 13.5.sp, color = PrimaryBlack) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlack)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showProtocolDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (activeRadarTrap != null) "雷达哨兵 (运行中)" else "雷达哨兵 (未布控)",
                                            fontSize = 13.5.sp,
                                            color = if (activeRadarTrap != null) Color(0xFF1D4ED8) else PrimaryBlack
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (activeRadarTrap != null) Color(0xFF1D4ED8) else PrimaryBlack
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (activeRadarTrap != null) {
                                            viewModel.sendAiMessage("请详细汇报当前雷达哨兵的布控规则、拦截目标和拦截记录与详情")
                                        } else {
                                            viewModel.showToast("当前未部署雷达哨兵，可对 AI 说'帮我盯住xx主题的xx异常'进行布控")
                                        }
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
        // 1.5 AI 实时雷达哨兵布控状态常驻条 (与通信层保持绝对同频，彻底解决用户“忘了让它监测什么”)
        // ==========================================
        AnimatedVisibility(
            visible = activeRadarTrap != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val trap = activeRadarTrap
            if (trap != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(0.6.dp, Color(0xFF3B82F6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🤖", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "雷达哨兵运行中",
                                    style = TextStyle(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (trap.capturedCount > 0) Color(0xFFDC2626) else OutlineGray)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "已拦截 ${trap.capturedCount} 条",
                                        style = TextStyle(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                            Text(
                                text = "${trap.topicPattern} · ${trap.conditionDesc}",
                                style = TextStyle(fontSize = 11.sp, color = OnSurfaceDark),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 快捷让 AI 汇报战报
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.sendAiMessage("请汇报当前雷达哨兵拦截的报文情况与异常分析")
                                }
                                .background(Color(0xFFDBEAFE))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "分析战报",
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1D4ED8)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // 撤销布控按钮
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { viewModel.clearRadarTrap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "停止雷达哨兵",
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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
                    isConnected = isConnected,
                    gatewayCount = livePackets.map { it.topic.substringBefore("/") }.distinct().size,
                    tslCount = tslProtocols.size,
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

            // 当用户向上翻看离开底部且存在消息时，显示“回到底部最新”微按钮
            androidx.compose.animation.AnimatedVisibility(
                visible = userScrolledUp && messages.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PrimaryBlack.copy(alpha = 0.92f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.clickable {
                        userScrolledUp = false
                        chatCoroutineScope.launch {
                            listState.scrollToItem(messages.size, scrollOffset = 100000)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "回到底部",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isResponding) "新内容生成中..." else "回到最新",
                            style = TextStyle(fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        )
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

                // 附加文件预览胶囊 (ChatGPT / Codex 风格)
                attachedFile?.let { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (file.extension) {
                                "pdf" -> Icons.Default.Description
                                "xlsx", "xls", "csv" -> Icons.Default.FolderOpen
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryBlack
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = OnSurfaceVariantGray,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除附件",
                            tint = OutlineGray,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { attachedFile = null }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ChatGPT / Codex 风格的 [+] 附件按钮
                    IconButton(
                        onClick = launchAttachmentPicker,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加文件/协议/日志",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

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
                                    text = if (attachedFile != null) {
                                        "输入对「${attachedFile?.name}」的分析要求..."
                                    } else if (isResponding) {
                                        "当前回复中，输入可排队追问..."
                                    } else {
                                        "询问网关报文、分析协议或排查异常..."
                                    },
                                    style = TextStyle(fontSize = 13.5.sp, color = OutlineGray),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val canSend = inputText.isNotBlank() || attachedFile != null
                    if (canSend) {
                        // 有输入内容或附加文件时支持发送
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack)
                                .clickable {
                                    val toSend = inputText.trim()
                                    val currentAttachment = attachedFile
                                    inputText = ""
                                    attachedFile = null

                                    if (currentAttachment != null) {
                                        viewModel.sendAiMessageWithAttachment(
                                            context = context,
                                            uri = currentAttachment.uri,
                                            fileName = currentAttachment.name,
                                            extension = currentAttachment.extension,
                                            userPrompt = toSend
                                        )
                                    } else {
                                        viewModel.sendAiMessage(toSend)
                                    }
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
 * 欢迎空态视图：借鉴 Gemini / Claude 极简工业质感
 * 包含：现场轻态势微徽标 + 四大高能生产力动作胶囊（Action Chips）
 */
private data class AiActionChipItem(
    val icon: String,
    val title: String,
    val hint: String,
    val prompt: String
)

@Composable
private fun AiEmptyWelcomeView(
    isConnected: Boolean,
    gatewayCount: Int,
    tslCount: Int,
    onPillClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    isConfigured: Boolean
) {
    val actionChips = remember {
        listOf(
            AiActionChipItem(
                icon = "⚡",
                title = "报文逆向建库",
                hint = "提取最新帧创建 TSL 物模型",
                prompt = "请分析当前内存中最新收到的数据报文（如雷达、断路器或传感器数据），逆向推导各字段定义、数据类型与业务状态字典，并直接调用 save_tsl_protocol 为我创建并激活 TSL 物模型！"
            ),
            AiActionChipItem(
                icon = "🎯",
                title = "部署雷达哨兵",
                hint = "微秒级拦截异常与关键事件",
                prompt = "请帮我部署 AI 雷达哨兵，重点对现场关键事件进行条件拦截布控（如检测到有人/离床、设备告警越限或状态突变），过滤常规冗余包，一旦命中立即拦截捕获并汇报。"
            ),
            AiActionChipItem(
                icon = "🔍",
                title = "现场极速体检",
                hint = "秒级诊断网关失联与越限",
                prompt = "请调用工具检索当前内存实时数据流，检查各网关通信吞吐、是否有设备离线失联、以及是否存在 TSL 物模型越限报警或异常数据，给出极速体检结论。"
            ),
            AiActionChipItem(
                icon = "💊",
                title = "定制胶囊指标",
                hint = "设定雷达人数/电量核心药丸",
                prompt = "请列出当前已解析协议的核心指标设置情况，并帮我指定消息卡片上微型药丸胶囊的展示字段（如雷达显示人数、断路器显示电量等）。"
            )
        )
    }

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
                .size(52.dp)
                .clip(CircleShape)
                .background(PrimaryBlack.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OnPrimaryWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "有什么我可以帮您分析的？",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack, letterSpacing = (-0.2).sp)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "工业物模型逆向 · AI 条件拦截哨兵 · 现场通信质检",
            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 现场轻态势微徽标（大厂级状态透视）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(0.6.dp, OutlineVariantLight, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.5.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF10B981) else Color(0xFF9CA3AF))
            )
            Text(
                text = if (isConnected) "现场链路已连接" else "未连接 Broker",
                style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
            )
            Text(text = "·", style = TextStyle(fontSize = 10.5.sp, color = OutlineGray))
            Text(
                text = "${if (gatewayCount > 0) gatewayCount else 1} 台网关在线",
                style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
            )
            Text(text = "·", style = TextStyle(fontSize = 10.5.sp, color = OutlineGray))
            Text(
                text = "$tslCount 套物模型生效",
                style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
            )
        }

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

        Spacer(modifier = Modifier.height(20.dp))

        // 四大高能生产力动作胶囊（Action Chips: 紧凑双排，轻量高效）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiActionChip(
                    item = actionChips[0],
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                AiActionChip(
                    item = actionChips[1],
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiActionChip(
                    item = actionChips[2],
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
                AiActionChip(
                    item = actionChips[3],
                    modifier = Modifier.weight(1f),
                    onClick = onPillClick
                )
            }
        }
    }
}

@Composable
private fun AiActionChip(
    item: AiActionChipItem,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerLowest)
            .border(0.7.dp, OutlineVariantLight, RoundedCornerShape(10.dp))
            .clickable { onClick(item.prompt) }
            .padding(horizontal = 10.dp, vertical = 8.5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = item.icon, fontSize = 13.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.hint,
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        color = OnSurfaceVariantGray
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

/**
 * 仿 ChatGPT / Codex 体验的附件元数据模型
 */
data class AttachedFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val extension: String
)

private fun getFileInfoFromUri(context: Context, uri: Uri): AttachedFileInfo? {
    var name = "unknown_file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    val ext = name.substringAfterLast('.', "").lowercase()
    return AttachedFileInfo(uri, name, size, ext)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

