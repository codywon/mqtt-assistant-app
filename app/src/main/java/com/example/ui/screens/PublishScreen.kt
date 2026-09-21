package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.viewmodel.MqttAssistantViewModel
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
                                    text = "搜索配置主题或载荷...",
                                    style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
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
                            name = "新配置",
                            topic = "device/control/TH02",
                            qos = 0,
                            retain = false,
                            payload = "{\n  \"action\": \"status\"\n}"
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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
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
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("preset_card_${preset.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Topic and QoS / Retain Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.topic,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                }
            }

            // Row 2: Payload Preview Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceContainerLow)
                    .clickable { onCopy() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = preset.payload.replace("\n", " ").trim(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = OnSurfaceVariantGray
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 3: Remark name tag + Character count + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (preset.name.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlack
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "${preset.payload.length} 字符",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = OnSurfaceVariantGray
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy
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

                    // Edit
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

                    // Delete
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

                    Spacer(modifier = Modifier.width(2.dp))

                    // Solid Black Send Button (Wide enough, no wrapping)
                    Button(
                        onClick = onDirectSend,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("direct_send_btn_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "发送",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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

                // QoS & Retain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "服务质量 (QoS)",
                            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerLow)
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0, 1, 2).forEach { q ->
                                val selected = qos == q
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selected) PrimaryBlack else Color.Transparent)
                                        .clickable { qos = q }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "QoS $q",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "保留消息 (Retain)",
                            style = TextStyle(fontSize = 11.5.sp, color = OnSurfaceVariantGray)
                        )
                        Switch(
                            checked = retain,
                            onCheckedChange = { retain = it },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimaryWhite,
                                checkedTrackColor = PrimaryBlack,
                                uncheckedThumbColor = OnSurfaceVariantGray,
                                uncheckedTrackColor = SurfaceContainerLow
                            )
                        )
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
                            .height(115.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = PrimaryBlack
                            ),
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isNew) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("删除", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, OutlineVariantLight),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("取消", style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariantGray))
                        }
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
                            .weight(1.2f)
                            .height(36.dp)
                    ) {
                        Text("保存配置", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
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
                            .height(36.dp)
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
                                fontSize = 12.sp,
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
