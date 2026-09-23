package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AiAgentConfig
import com.example.model.ProtocolKnowledge
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest

/**
 * AI 智能助手设置与协议澄清工作台弹窗：
 * Tab 1: 大模型连接配置 (OpenAI/DeepSeek/Qwen/Ollama 兼容接口)
 * Tab 2: 硬件私有协议澄清工作台 (录入/管理 Hex 字节解析与业务语义)
 */
@Composable
fun AiSettingsDialog(
    initialConfig: AiAgentConfig,
    protocols: List<ProtocolKnowledge>,
    onDismiss: () -> Unit,
    onSaveConfig: (AiAgentConfig) -> Unit,
    onSaveProtocol: (ProtocolKnowledge) -> Unit,
    onDeleteProtocol: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Tab 1 state
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var contextWindow by remember { mutableIntStateOf(initialConfig.contextWindow) }
    var customContextWindowText by remember { mutableStateOf(initialConfig.contextWindow.toString()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Tab 2 state
    var editingProtocol by remember { mutableStateOf<ProtocolKnowledge?>(null) }
    var isCreatingProtocol by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(0.8.dp, OutlineVariantLight),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 助手与协议澄清工作台",
                        style = TextStyle(
                            fontSize = 16.sp,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceContainerLowest,
                    contentColor = PrimaryBlack,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryBlack,
                            height = 2.5.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.8.dp)
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "模型服务配置",
                                style = TextStyle(
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) PrimaryBlack else OnSurfaceVariantGray
                                )
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "协议澄清知识库 (${protocols.size})",
                                style = TextStyle(
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) PrimaryBlack else OnSurfaceVariantGray
                                )
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (selectedTab == 0) {
                        // ==========================================
                        // Tab 0: Model Configuration
                        // ==========================================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quick Template Chips
                            Text(
                                text = "快捷预置服务商",
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickTemplateChip("DeepSeek 官方") {
                                    baseUrl = "https://api.deepseek.com"
                                    modelName = "deepseek-chat"
                                }
                                QuickTemplateChip("通义千问") {
                                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                                    modelName = "qwen-plus"
                                }
                                QuickTemplateChip("OpenAI") {
                                    baseUrl = "https://api.openai.com/v1"
                                    modelName = "gpt-4o"
                                }
                                QuickTemplateChip("本地 Ollama") {
                                    baseUrl = "http://10.0.2.2:11434/v1"
                                    modelName = "qwen2.5:7b"
                                }
                            }

                            // Base URL Input
                            SettingInputField(
                                label = "接口基础地址 (Base URL)",
                                value = baseUrl,
                                placeholder = "例如: https://api.deepseek.com",
                                onValueChange = { baseUrl = it }
                            )

                            // API Key Input
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "API Key",
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainerLow)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = OutlineGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BasicTextField(
                                        value = apiKey,
                                        onValueChange = { apiKey = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.5.sp,
                                            color = PrimaryBlack
                                        ),
                                        singleLine = true,
                                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        cursorBrush = SolidColor(PrimaryBlack),
                                        decorationBox = { inner ->
                                            if (apiKey.isEmpty()) {
                                                Text(
                                                    text = "sk-xxxxxxxxxxxxxxxx",
                                                    style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                    IconButton(
                                        onClick = { isApiKeyVisible = !isApiKeyVisible },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "显隐",
                                            tint = OutlineGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Model Name Input
                            SettingInputField(
                                label = "模型名称 (Model ID)",
                                value = modelName,
                                placeholder = "如: deepseek-chat 或 deepseek-reasoner",
                                onValueChange = { modelName = it }
                            )

                            // Context Window Selector (Pi-Agent 内存管理)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "模型上下文窗口 (Context Window)",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                                    )
                                    Text(
                                        text = "超 70% 自动记忆压缩",
                                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                                    )
                                }

                                // 预设快捷选择 (包含 128K, 256K, 512K, 1M)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val windows = listOf(
                                        32768 to "32K",
                                        65536 to "64K",
                                        131072 to "128K",
                                        262144 to "256K",
                                        524288 to "512K",
                                        1048576 to "1M"
                                    )
                                    windows.forEach { (w, label) ->
                                        val isSelected = contextWindow == w
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) PrimaryBlack else SurfaceContainerLow)
                                                .clickable {
                                                    contextWindow = w
                                                    customContextWindowText = w.toString()
                                                }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = TextStyle(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) OnPrimaryWhite else OnSurfaceVariantGray
                                                )
                                            )
                                        }
                                    }
                                }

                                // 手工自由输入框 (支持 1M, 2M 或任意大小)
                                SettingInputField(
                                    label = "自定义上下文 Tokens (支持手工输入任意数值)",
                                    value = customContextWindowText,
                                    placeholder = "如: 1048576 (1M) 或 2097152 (2M)",
                                    onValueChange = { input ->
                                        customContextWindowText = input.filter { it.isDigit() }
                                        val parsed = customContextWindowText.toIntOrNull()
                                        if (parsed != null && parsed > 0) {
                                            contextWindow = parsed
                                        }
                                    }
                                )
                            }

                            // Pi Agent ReAct 架构提示卡片
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceContainerLow,
                                border = BorderStroke(0.6.dp, OutlineVariantLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "⚡ Pi Agent 范式 ReAct 引擎已启用",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "具备标准的 Thought → Action → Observation 闭环；当历史记录与海量报文占用达到 70% 水位时，系统将自动进行事实提炼压缩，保障长效会话永不溢出。",
                                        style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, lineHeight = 15.sp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Save Button
                            Button(
                                onClick = {
                                    onSaveConfig(
                                        initialConfig.copy(
                                            apiKey = apiKey.trim(),
                                            baseUrl = baseUrl.trim(),
                                            modelName = modelName.trim(),
                                            contextWindow = contextWindow,
                                            compactionThreshold = 0.7
                                        )
                                    )
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlack,
                                    contentColor = OnPrimaryWhite
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                            ) {
                                Text("保存模型配置", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }
                    } else {
                        // ==========================================
                        // Tab 1: Protocol Clarification Workbench
                        // ==========================================
                        if (isCreatingProtocol || editingProtocol != null) {
                            val activeProto = editingProtocol ?: ProtocolKnowledge(name = "", topicFilter = "", description = "")
                            ProtocolEditView(
                                initial = activeProto,
                                onSave = { saved ->
                                    onSaveProtocol(saved)
                                    editingProtocol = null
                                    isCreatingProtocol = false
                                },
                                onCancel = {
                                    editingProtocol = null
                                    isCreatingProtocol = false
                                }
                            )
                        } else {
                            ProtocolListView(
                                protocols = protocols,
                                onAddNew = { isCreatingProtocol = true },
                                onEdit = { editingProtocol = it },
                                onDelete = onDeleteProtocol
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTemplateChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryBlack
            )
        )
    }
}

@Composable
private fun SettingInputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerLow)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    color = PrimaryBlack
                ),
                singleLine = true,
                cursorBrush = SolidColor(PrimaryBlack),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun ProtocolListView(
    protocols: List<ProtocolKnowledge>,
    onAddNew: () -> Unit,
    onEdit: (ProtocolKnowledge) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已配置的私有设备协议澄清",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
            )
            Button(
                onClick = onAddNew,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlack,
                    contentColor = OnPrimaryWhite
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("新增协议", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (protocols.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = OutlineGray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "暂无协议澄清规则\n点击上方「新增协议」为人话描述网关 Hex 字段",
                        style = TextStyle(fontSize = 12.sp, color = OutlineGray, lineHeight = 16.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            for (p in protocols) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow.copy(alpha = 0.6f)),
                    border = BorderStroke(0.6.dp, OutlineVariantLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = p.name,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onEdit(p) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = OnSurfaceVariantGray, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onDelete(p.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = OnSurfaceVariantGray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Text(
                            text = "匹配主题: ${p.topicFilter}",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = PrimaryBlack)
                        )
                        Text(
                            text = p.description,
                            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray, lineHeight = 15.sp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolEditView(
    initial: ProtocolKnowledge,
    onSave: (ProtocolKnowledge) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var topicFilter by remember { mutableStateOf(initial.topicFilter) }
    var description by remember { mutableStateOf(initial.description) }
    var sampleHex by remember { mutableStateOf(initial.sampleHex) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (initial.id.isNotBlank()) "编辑协议澄清说明" else "录入新硬件私有协议",
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
        )

        SettingInputField(
            label = "协议名称",
            value = name,
            placeholder = "例如: 体征网关血压计 Hex 协议",
            onValueChange = { name = it }
        )

        SettingInputField(
            label = "适用 MQTT 主题 (支持通配符)",
            value = topicFilter,
            placeholder = "例如: vital/gateway/+/data",
            onValueChange = { topicFilter = it }
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "协议澄清描述 (人话讲解各字节物理含义)",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerLow)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack, lineHeight = 16.sp),
                    cursorBrush = SolidColor(PrimaryBlack),
                    decorationBox = { inner ->
                        if (description.isEmpty()) {
                            Text(
                                text = "人话写明即可，例如:\n- 第1-2字节为固定头 AA 55\n- 第5字节是收缩压高压(mmHg)，正常90-139\n- 第6字节是舒张压低压(mmHg)，正常60-89\n- 第7字节是心率(次/分)",
                                style = TextStyle(fontSize = 11.5.sp, color = OutlineGray, lineHeight = 15.sp)
                            )
                        }
                        inner()
                    }
                )
            }
        }

        SettingInputField(
            label = "样例 Hex 报文 (可选)",
            value = sampleHex,
            placeholder = "例如: AA 55 01 02 88 50 4B 00",
            onValueChange = { sampleHex = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消", color = OnSurfaceVariantGray, fontSize = 12.5.sp)
            }

            Button(
                onClick = {
                    if (name.isNotBlank() && topicFilter.isNotBlank() && description.isNotBlank()) {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                topicFilter = topicFilter.trim(),
                                description = description.trim(),
                                sampleHex = sampleHex.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && topicFilter.isNotBlank() && description.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlack,
                    contentColor = OnPrimaryWhite
                )
            ) {
                Text("保存协议", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}
