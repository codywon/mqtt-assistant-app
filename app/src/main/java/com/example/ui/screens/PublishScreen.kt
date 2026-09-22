package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.model.PublishPreset
import com.example.util.MqttTopicUtil
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
fun PublishScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val presets by viewModel.publishPresets.collectAsState()
    val searchQuery by viewModel.presetSearchQuery.collectAsState()

    var activeDialogPreset by remember { mutableStateOf<PublishPreset?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val filteredPresets = presets.filter {
        searchQuery.isBlank() ||
            it.topic.contains(searchQuery.trim(), ignoreCase = true) ||
            it.payload.contains(searchQuery.trim(), ignoreCase = true) ||
            it.name.contains(searchQuery.trim(), ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Action Row: Search bar + Add Preset button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow.copy(alpha = 0.5f))
                        .border(0.6.dp, SurfaceContainerDefault, RoundedCornerShape(8.dp))
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
                        value = searchQuery,
                        onValueChange = { viewModel.presetSearchQuery.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pub_search_input"),
                        textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                        cursorBrush = SolidColor(PrimaryBlack),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索主题 / 名称 / 载荷...",
                                    style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                            tint = OutlineGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.presetSearchQuery.value = "" }
                        )
                    }
                }

                // Add Preset Button
                Button(
                    onClick = {
                        isCreatingNew = true
                        activeDialogPreset = PublishPreset(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            topic = "",
                            qos = 0,
                            retain = false,
                            payload = ""
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("publish_add_new_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "新增配置",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 3. Preset List
        if (filteredPresets.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "未匹配到相关发布配置" else "暂无发布预设，点击右上角「新增配置」添加",
                        style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                    )
                }
            }
        } else {
            items(filteredPresets, key = { it.id }) { preset ->
                PublishPresetCard(
                    preset = preset,
                    onDirectSend = { viewModel.directPublishPreset(preset) },
                    onEdit = {
                        isCreatingNew = false
                        activeDialogPreset = preset
                    },
                    onDuplicate = {
                        isCreatingNew = true
                        val dupName = if (preset.name.isNotBlank()) {
                            "${preset.name} (副本)"
                        } else {
                            "${preset.topic.substringAfterLast('/')} (副本)"
                        }
                        activeDialogPreset = preset.copy(
                            id = UUID.randomUUID().toString(),
                            name = dupName
                        )
                        viewModel.showToast("已复刻配置，可直接在此基础上修改")
                    },
                    onCopy = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("MQTT Payload", preset.payload))
                        viewModel.showToast("已复制载荷内容")
                    },
                    onDelete = { viewModel.deletePreset(preset.id) }
                )
            }
        }


    }

    // Modal Edit & Quick Send Dialog (Google / Claude / OpenAI Clean Aesthetics)
    activeDialogPreset?.let { preset ->
        PublishConfigModalDialog(
            initialPreset = preset,
            isNew = isCreatingNew,
            onDismiss = { activeDialogPreset = null },
            onSave = { updated ->
                viewModel.saveOrUpdatePreset(updated)
                activeDialogPreset = null
            },
            onDirectPublish = { updated ->
                viewModel.directPublishPreset(updated)
                activeDialogPreset = null
            },
            onDelete = {
                viewModel.deletePreset(preset.id)
                activeDialogPreset = null
            }
        )
    }
}

@Composable
private fun PublishPresetCard(
    preset: PublishPreset,
    onDirectSend: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.8.dp, OutlineVariantLight.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("preset_card_${preset.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Part 1: Remark Name (Header) & Topic (Auto-wrap monospace)
            if (preset.name.isNotBlank()) {
                Text(
                    text = preset.name,
                    style = TextStyle(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.topic,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        color = OnSurfaceDark
                    ),
                    softWrap = true
                )
            } else {
                Text(
                    text = preset.topic,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        color = PrimaryBlack
                    ),
                    softWrap = true
                )
            }

            // Part 2: Payload Preview (Clean, rounded light box, click to copy)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceContainerLow.copy(alpha = 0.5f))
                    .clickable { onCopy() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = preset.payload.ifBlank { "（无载荷内容）" }.replace("\n", " ").trim(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 17.sp,
                        color = if (preset.payload.isBlank()) OutlineGray else PrimaryBlack
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Part 3: Bottom Row - Left: QoS + Retain + Character count; Right: Action icons + Send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left area: QoS, Retain, Characters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "QoS ${preset.qos}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceDark
                            )
                        )
                    }

                    if (preset.retain) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Retain",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceDark
                                )
                            )
                        }
                    }

                    Text(
                        text = "· ${preset.payload.length} 字符",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OnSurfaceVariantGray
                        ),
                        maxLines = 1
                    )
                }

                // Right area: Action buttons (Copy, Duplicate, Edit, Delete, Send)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制载荷",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CopyAll,
                            contentDescription = "复刻配置",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑配置",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除配置",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onDirectSend,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("direct_send_btn_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "发送",
                            style = TextStyle(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// Dialog: Strictly Monochrome Black/White/Gray - Big Tech Minimalist
@Composable
private fun PublishConfigModalDialog(
    initialPreset: PublishPreset,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (PublishPreset) -> Unit,
    onDirectPublish: (PublishPreset) -> Unit,
    onDelete: () -> Unit = {}
) {
    var topic by remember { mutableStateOf(initialPreset.topic) }
    var name by remember { mutableStateOf(initialPreset.name) }
    var qos by remember { mutableIntStateOf(initialPreset.qos) }
    var retain by remember { mutableStateOf(initialPreset.retain) }
    var payload by remember { mutableStateOf(initialPreset.payload) }

    val validation = remember(topic) {
        if (topic.isBlank()) null else MqttTopicUtil.validatePublishTopic(topic.trim())
    }

    fun formatJson() {
        val current = payload.trim()
        try {
            if (current.startsWith("{") && current.endsWith("}")) {
                val obj = JSONObject(current)
                payload = obj.toString(2)
            } else if (current.startsWith("[") && current.endsWith("]")) {
                val array = JSONArray(current)
                payload = array.toString(2)
            }
        } catch (_: Exception) {}
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
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "新增发布配置" else "编辑发布配置",
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

                // Topic Input
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "发布主题 (Topic)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                        )
                        if (validation != null && !validation.isValid) {
                            Text(
                                text = validation.errorMessage ?: "语法错误",
                                style = TextStyle(fontSize = 10.sp, color = Color(0xFFDC2626))
                            )
                        }
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
                            value = topic,
                            onValueChange = { topic = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                        if (topic.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "清除",
                                tint = OutlineGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { topic = "" }
                            )
                        }
                    }
                }

                // Remark / Name Input
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "备注名称",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
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
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontSize = 12.5.sp,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (name.isEmpty()) {
                                    Text(
                                        text = "可选，如：开关控制 / 设备状态上报",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (name.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "清除备注",
                                tint = OutlineGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { name = "" }
                            )
                        }
                    }
                }

                // QoS & Retain (严格对齐，选中卡绝对上下几何居中)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 服务质量 (QoS)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "服务质量 (QoS)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0, 1, 2).forEach { q ->
                                val selected = qos == q
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) PrimaryBlack else Color.Transparent)
                                        .clickable { qos = q },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "QoS $q",
                                        style = TextStyle(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 保留消息 (Retain)
                    Column(
                        modifier = Modifier.weight(0.55f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "保留消息 (Retain)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(false to "关", true to "开").forEach { (rVal, rLabel) ->
                                val selected = retain == rVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) PrimaryBlack else Color.Transparent)
                                        .clickable { retain = rVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = rLabel,
                                        style = TextStyle(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Payload Input
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "消息载荷 (JSON / 文本)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { formatJson() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("格式化", style = TextStyle(fontSize = 11.sp, color = PrimaryBlack, fontWeight = FontWeight.Bold))
                            }
                            TextButton(
                                onClick = { payload = "{}" },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("清空", style = TextStyle(fontSize = 11.sp, color = OutlineGray))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 0.7.dp,
                                color = SurfaceContainerDefault,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = PrimaryBlack
                            ),
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Action Buttons
                if (!isNew) {
                    // Row 1: Auxiliary Actions (Delete & Duplicate)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("删除此配置", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {
                                val duplicated = initialPreset.copy(
                                    id = UUID.randomUUID().toString(),
                                    name = if (name.isNotBlank()) "$name (副本)" else "${topic.substringAfterLast('/')} (副本)",
                                    topic = topic.trim(),
                                    qos = qos,
                                    retain = retain,
                                    payload = payload
                                )
                                onSave(duplicated)
                            },
                            enabled = topic.isNotBlank() && (validation?.isValid != false),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, OutlineVariantLight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlack),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CopyAll,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = PrimaryBlack
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("另存为新配置", fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Row 2: Core Actions (Cancel, Save, Send)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (!isNew) 4.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, OutlineVariantLight),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "取消",
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                color = OnSurfaceVariantGray,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            val updated = initialPreset.copy(
                                topic = topic.trim(),
                                name = name.ifBlank { topic.substringAfterLast('/') },
                                qos = qos,
                                retain = retain,
                                payload = payload
                            )
                            onSave(updated)
                        },
                        enabled = topic.isNotBlank() && (validation?.isValid != false),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceContainerLow,
                            contentColor = PrimaryBlack,
                            disabledContainerColor = SurfaceContainerLow.copy(alpha = 0.5f),
                            disabledContentColor = OutlineGray
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "保存配置",
                            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            val updated = initialPreset.copy(
                                topic = topic.trim(),
                                name = name.ifBlank { topic.substringAfterLast('/') },
                                qos = qos,
                                retain = retain,
                                payload = payload
                            )
                            onDirectPublish(updated)
                        },
                        enabled = topic.isNotBlank() && (validation?.isValid != false),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite,
                            disabledContainerColor = SurfaceContainerLow.copy(alpha = 0.5f),
                            disabledContentColor = OutlineGray
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (topic.isNotBlank() && (validation?.isValid != false)) OnPrimaryWhite else OutlineGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "一键发送",
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (topic.isNotBlank() && (validation?.isValid != false)) OnPrimaryWhite else OutlineGray
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
