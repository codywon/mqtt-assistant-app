package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
    onDeleteProtocol: (String) -> Unit,
    onBatchImportProtocols: (String) -> Unit = {},
    onExportProtocols: (() -> Unit)? = null,
    onCopyProtocolsToken: (() -> Unit)? = null,
    onImportProtocolsFromClipboard: (() -> Unit)? = null,
    onImportProtocolsFromFile: ((Uri) -> Unit)? = null,
    onlyProtocol: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(if (onlyProtocol) 1 else 0) }

    // JSON 协议与物模型文件导入选择器
    val protocolJsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportProtocolsFromFile?.invoke(it) }
    }
    val launchFilePicker = {
        try {
            protocolJsonPickerLauncher.launch("application/json")
        } catch (_: Exception) {
            try {
                protocolJsonPickerLauncher.launch("*/*")
            } catch (_: Exception) {}
        }
    }

    // Tab 1 state
    val coroutineScope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var contextWindow by remember { mutableIntStateOf(initialConfig.contextWindow) }
    var customContextWindowText by remember { mutableStateOf(initialConfig.contextWindow.toString()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var modelFetchMessage by remember { mutableStateOf("") }

    // Tab 2 state
    var editingProtocol by remember { mutableStateOf<ProtocolKnowledge?>(null) }
    var isCreatingProtocol by remember { mutableStateOf(false) }
    var showBatchImportDialog by remember { mutableStateOf(false) }
    var batchImportText by remember { mutableStateOf("") }

    if (showBatchImportDialog) {
        AlertDialog(
            onDismissRequest = { showBatchImportDialog = false },
            title = {
                Text(
                    text = "粘贴导入协议规则",
                    style = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "支持粘贴导出的 JSON 协议规则/TSL物模型，或自然语言协议说明（多条间可用 --- 分割）：",
                        style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = batchImportText,
                            onValueChange = { batchImportText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack, lineHeight = 16.sp),
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { inner ->
                                if (batchImportText.isEmpty()) {
                                    Text(
                                        text = "例如粘贴导出的 JSON 文件内容，或纯文本格式:\n【智能断路器】topic: power/breaker/+/data\n说明: 第3-4字节当前电压(0.1V)，第5-6字节当前电流(0.01A)，第7-8字节有功功率。\n---\n【温湿度传感器】topic: sensor/temp/data\n说明: 第3-4字节摄氏度温度，第5字节湿度百分比。",
                                        style = TextStyle(fontSize = 11.sp, color = OutlineGray, lineHeight = 15.sp)
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (batchImportText.isNotBlank()) {
                            onBatchImportProtocols(batchImportText)
                            batchImportText = ""
                            showBatchImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack, contentColor = OnPrimaryWhite),
                    shape = RoundedCornerShape(8.dp),
                    enabled = batchImportText.isNotBlank()
                ) {
                    Text("一键解析并导入", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchImportDialog = false }) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(0.9.dp, OutlineVariantLight),
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (onlyProtocol) "硬件私有协议库" else "AI 助手与协议知识库",
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

                // Tab Switcher (仅在非纯协议模式下展示)
                if (!onlyProtocol) {
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
                                    text = "私有协议知识库 (${protocols.size})",
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
                }

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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

                            // Model Name Input with dynamic /v1/models fetch
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "模型名称",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                                    )
                                    Box {
                                        TextButton(
                                            onClick = {
                                                if (baseUrl.isBlank()) {
                                                    modelFetchMessage = "请先输入 Base URL"
                                                    return@TextButton
                                                }
                                                isFetchingModels = true
                                                modelFetchMessage = ""
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val clean = baseUrl.trim().trimEnd('/')
                                                        val endpoint = if (clean.endsWith("/v1")) "$clean/models" else "$clean/v1/models"
                                                        val url = URL(endpoint)
                                                        val conn = (url.openConnection() as HttpURLConnection).apply {
                                                            requestMethod = "GET"
                                                            connectTimeout = 8000
                                                            readTimeout = 12000
                                                            if (apiKey.isNotBlank()) {
                                                                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                                                            }
                                                        }
                                                        val code = conn.responseCode
                                                        if (code in 200..299) {
                                                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                                                            val json = JSONObject(body)
                                                            val dataArray = json.optJSONArray("data") ?: json.optJSONArray("models")
                                                            val list = mutableListOf<String>()
                                                            if (dataArray != null) {
                                                                for (i in 0 until dataArray.length()) {
                                                                    val item = dataArray.opt(i)
                                                                    if (item is JSONObject) {
                                                                        val id = item.optString("id", "").ifBlank { item.optString("name", "") }
                                                                        if (id.isNotBlank()) list.add(id)
                                                                    } else if (item is String && item.isNotBlank()) {
                                                                        list.add(item)
                                                                    }
                                                                }
                                                            }
                                                            val sorted = list.distinct().sorted()
                                                            withContext(Dispatchers.Main) {
                                                                isFetchingModels = false
                                                                if (sorted.isNotEmpty()) {
                                                                    fetchedModels = sorted
                                                                    showModelDropdown = true
                                                                } else {
                                                                    modelFetchMessage = "接口未返回模型列表"
                                                                }
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                isFetchingModels = false
                                                                modelFetchMessage = "拉取失败 (HTTP $code)"
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            isFetchingModels = false
                                                            modelFetchMessage = "拉取超时或接口无效"
                                                        }
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            if (isFetchingModels) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = PrimaryBlack)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("拉取中...", fontSize = 11.sp, color = OnSurfaceVariantGray)
                                            } else {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryBlack)
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("拉取模型列表", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showModelDropdown,
                                            onDismissRequest = { showModelDropdown = false },
                                            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).background(SurfaceContainerLowest)
                                        ) {
                                            for (m in fetchedModels) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = m,
                                                            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = PrimaryBlack)
                                                        )
                                                    },
                                                    onClick = {
                                                        modelName = m
                                                        showModelDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

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
                                        value = modelName,
                                        onValueChange = { modelName = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.5.sp,
                                            color = PrimaryBlack
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(PrimaryBlack),
                                        decorationBox = { inner ->
                                            if (modelName.isEmpty()) {
                                                Text(
                                                    text = "如: deepseek-chat 或 gpt-4o",
                                                    style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                    if (fetchedModels.isNotEmpty()) {
                                        IconButton(
                                            onClick = { showModelDropdown = !showModelDropdown },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "选择",
                                                tint = OutlineGray
                                            )
                                        }
                                    }
                                }

                                if (modelFetchMessage.isNotBlank()) {
                                    Text(
                                        text = modelFetchMessage,
                                        style = TextStyle(fontSize = 10.5.sp, color = Color(0xFFDC2626))
                                    )
                                }
                            }

                            // Context Window Selector (Pi-Agent 内存管理)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "模型上下文窗口",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                                    )
                                    Text(
                                        text = "超 70% 自动压缩",
                                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                                    )
                                }

                                // 预设快捷选择：严格仅保留 128K, 256K, 1M 三档规格
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val windows = listOf(
                                        131072 to "128K",
                                        262144 to "256K",
                                        1048576 to "1M"
                                    )
                                    windows.forEach { (w, label) ->
                                        val isSelected = contextWindow == w
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) PrimaryBlack else SurfaceContainerLow)
                                                .clickable {
                                                    contextWindow = w
                                                    customContextWindowText = w.toString()
                                                }
                                                .padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) OnPrimaryWhite else OnSurfaceVariantGray
                                                )
                                            )
                                        }
                                    }
                                }

                                // 手工自由输入框 (支持 1M, 2M 或任意数值)
                                SettingInputField(
                                    label = "自定义上下文 Tokens",
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
                                onBatchImport = { showBatchImportDialog = true },
                                onImportFile = if (onImportProtocolsFromFile != null) launchFilePicker else null,
                                onExportProtocols = onExportProtocols,
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
    onBatchImport: () -> Unit,
    onImportFile: (() -> Unit)? = null,
    onExportProtocols: (() -> Unit)? = null,
    onEdit: (ProtocolKnowledge) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 顶部操作栏：规则统计与新增主操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "协议规则库 (${protocols.size})",
                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                )
                Text(
                    text = "MQTT 主题与 TSL 协议规则",
                    style = TextStyle(fontSize = 10.5.sp, color = OnSurfaceVariantGray)
                )
            }
            Button(
                onClick = onAddNew,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlack,
                    contentColor = OnPrimaryWhite
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("新增协议", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 2. 快捷工具栏：导入 JSON | 导出备份 | 粘贴文本 (三键并排等宽，职责清晰，无任何重复)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onImportFile != null) {
                OutlinedButton(
                    onClick = onImportFile,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.9.dp, OutlineGray.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlack),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导入 JSON", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (onExportProtocols != null) {
                OutlinedButton(
                    onClick = onExportProtocols,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.9.dp, OutlineGray.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlack),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出备份", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onBatchImport,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.9.dp, OutlineGray.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlack),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("粘贴文本", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 3. 协议规则列表或空状态
        if (protocols.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerLow.copy(alpha = 0.5f))
                    .padding(vertical = 36.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = OutlineGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无已配置的私有协议规则",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "您可以直接导入已导出的 .json 规则文件\n或在上方点击「粘贴文本」/「新增协议」快速添加",
                        style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (onImportFile != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onImportFile,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlack,
                                contentColor = OnPrimaryWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("选取本地 JSON 文件导入", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in protocols) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow.copy(alpha = 0.7f)),
                        border = BorderStroke(0.6.dp, OutlineVariantLight),
                        modifier = Modifier.fillMaxWidth()
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
                                Text(
                                    text = p.name,
                                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceContainerLowest)
                                            .clickable { onEdit(p) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = PrimaryBlack, modifier = Modifier.size(13.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceContainerLowest)
                                            .clickable { onDelete(p.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = androidx.compose.ui.graphics.Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                                    }
                                }
                            }

                            // 主题标签 Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceContainerLowest)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Topic: ${p.topicFilter}",
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PrimaryBlack)
                                )
                            }

                            // 澄清说明
                            Text(
                                text = p.description,
                                style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 样例 Hex (如果有)
                            if (p.sampleHex.isNotBlank()) {
                                Text(
                                    text = "Hex 样例: ${p.sampleHex}",
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = OutlineGray),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
            text = if (initial.id.isNotBlank()) "编辑协议规则说明" else "录入新硬件私有协议",
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
        )

        SettingInputField(
            label = "协议名称",
            value = name,
            placeholder = "例如: 智能断路器 / 传感器 Hex 协议",
            onValueChange = { name = it }
        )

        SettingInputField(
            label = "适用 MQTT 主题",
            value = topicFilter,
            placeholder = "例如: power/breaker/+/data 或 #",
            onValueChange = { topicFilter = it }
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "协议规则描述",
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
                                text = "人话写明即可，例如:\n- 第1-2字节为固定头 AA 55\n- 第3-4字节为当前电压 (单位 0.1V)\n- 第5-6字节为当前电流 (单位 0.01A)\n- 第7-8字节为有功功率 (单位 1W)",
                                style = TextStyle(fontSize = 11.5.sp, color = OutlineGray, lineHeight = 15.sp)
                            )
                        }
                        inner()
                    }
                )
            }
        }

        SettingInputField(
            label = "样例 Hex 报文",
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
                    if (description.isNotBlank()) {
                        val computedName = name.trim().ifBlank {
                            val firstLine = description.lines().firstOrNull { it.isNotBlank() }?.removePrefix("#")?.removePrefix("【")?.removeSuffix("】")?.trim() ?: "设备协议"
                            firstLine.take(15)
                        }
                        val computedTopic = topicFilter.trim().ifBlank { "#" }
                        onSave(
                            initial.copy(
                                name = computedName,
                                topicFilter = computedTopic,
                                description = description.trim(),
                                sampleHex = sampleHex.trim()
                            )
                        )
                    }
                },
                enabled = description.isNotBlank(),
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
