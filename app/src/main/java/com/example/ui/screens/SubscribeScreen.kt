package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SubscriptionItem
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
fun SubscribeScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val subscriptions by viewModel.subscriptions.collectAsState()
    val searchQuery by viewModel.subSearchQuery.collectAsState()

    val runningCount = subscriptions.count { it.isEnabled }
    val totalCount = subscriptions.size

    // 智能提取前缀胶囊（仅在订阅数 >= 5 且存在多个不同前缀时轻量呈现）
    val topicPrefixes = remember(subscriptions) {
        subscriptions
            .mapNotNull {
                val clean = it.topic.trim()
                if (clean.contains('/')) {
                    val p = clean.substringBefore('/').trim()
                    if (p.isNotEmpty() && !p.startsWith("+") && !p.startsWith("#") && !p.startsWith("$")) p else null
                } else null
            }
            .groupingBy { it }
            .eachCount()
    }
    var selectedPrefix by remember { mutableStateOf("全部") }

    val filteredList = subscriptions.filter {
        val query = searchQuery.trim()
        val matchesQuery = query.isBlank() ||
            it.topic.contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true)
        val matchesPrefix = selectedPrefix == "全部" ||
            it.topic.startsWith("$selectedPrefix/") ||
            it.topic == selectedPrefix
        matchesQuery && matchesPrefix
    }

    // State for modal dialog (creating or editing subscription)
    var isDialogVisible by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SubscriptionItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SubscriptionItem?>(null) }

    fun openCreateDialog() {
        editingItem = null
        isDialogVisible = true
    }

    fun openEditDialog(item: SubscriptionItem) {
        editingItem = item
        isDialogVisible = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // 顶部操作栏：极简设计，搜索框 + 新建订阅按钮，无杂乱底色
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search field
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
                        onValueChange = { viewModel.subSearchQuery.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sub_search_input"),
                        textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                        cursorBrush = SolidColor(PrimaryBlack),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索已订阅主题...",
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
                                .clickable { viewModel.subSearchQuery.value = "" }
                        )
                    }
                }

                // Add button: 统一纯净样式
                Button(
                    onClick = { openCreateDialog() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("open_add_sub_dialog_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = OnPrimaryWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "新建订阅",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryWhite
                        )
                    )
                }
            }
        }

        // 智能前缀筛选胶囊 (仅当订阅数 >= 5 且存在多个前缀分类时呈现，少即是多，极简克制)
        if (subscriptions.size >= 5 && topicPrefixes.size >= 2) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isAllSelected = selectedPrefix == "全部"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAllSelected) PrimaryBlack else SurfaceContainerLow,
                            border = if (isAllSelected) null else BorderStroke(0.6.dp, SurfaceContainerDefault),
                            modifier = Modifier.clickable { selectedPrefix = "全部" }
                        ) {
                            Text(
                                text = "全部 (${subscriptions.size})",
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) Color.White else OnSurfaceVariantGray
                                ),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(topicPrefixes.entries.toList()) { (prefix, count) ->
                        val isSelected = selectedPrefix == prefix
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) PrimaryBlack else SurfaceContainerLow,
                            border = if (isSelected) null else BorderStroke(0.6.dp, SurfaceContainerDefault),
                            modifier = Modifier.clickable { selectedPrefix = prefix }
                        ) {
                            Text(
                                text = "$prefix ($count)",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else OnSurfaceVariantGray
                                ),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Subscription Cards List (Fully clickable for editing)
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "未匹配到相关主题" else "暂无订阅主题，点击右上角「新建订阅」添加",
                        style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                    )
                }
            }
        } else {
            items(filteredList, key = { it.id }) { sub ->
                SubscriptionItemCard(
                    item = sub,
                    onCardClick = { openEditDialog(sub) },
                    onToggle = { viewModel.toggleSubscription(sub.id) },
                    onCopy = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("topic", sub.topic))
                        viewModel.showToast("已复制主题: ${sub.topic}")
                    },
                    onEdit = { openEditDialog(sub) },
                    onDelete = { itemToDelete = sub }
                )
            }
        }
    }

    // Confirmation Dialog for Safe Deletion (Prevents accidental deletion)
    itemToDelete?.let { target ->
        val displayName = if (target.name.isNotBlank()) "${target.name} (${target.topic})" else target.topic
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = SurfaceContainerLowest,
            title = {
                Text(
                    text = "确认删除订阅？",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                )
            },
            text = {
                Text(
                    text = "确定要删除订阅「$displayName」吗？删除后将停止接收该主题的报文推送并移除相关统计。",
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
                        viewModel.removeSubscription(target.id)
                        itemToDelete = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    )
                ) {
                    Text("确认删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("取消", color = OnSurfaceVariantGray)
                }
            }
        )
    }

    // Modal Dialog for Editing or Adding a Subscription
    if (isDialogVisible) {
        SubscriptionConfigModalDialog(
            initialItem = editingItem,
            onDismiss = { isDialogVisible = false },
            onSave = { savedItem ->
                viewModel.saveOrUpdateSubscription(savedItem, editingItem?.topic)
                isDialogVisible = false
            },
            onDelete = { itemId ->
                val target = editingItem
                isDialogVisible = false
                if (target != null) {
                    itemToDelete = target
                } else {
                    viewModel.removeSubscription(itemId)
                }
            }
        )
    }
}

@Composable
private fun SubscriptionItemCard(
    item: SubscriptionItem,
    onCardClick: () -> Unit,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEnabled) SurfaceContainerLowest else SurfaceContainerLowest.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.8.dp, OutlineVariantLight.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("sub_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Colored status dot + Title (Remark name if available, otherwise Topic) + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Custom theme dot (Colored only here per user strict rule!)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (item.isEnabled) Color(item.dotColorHex) else OutlineVariantLight)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (item.name.isNotBlank()) {
                        Text(
                            text = item.name,
                            style = TextStyle(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isEnabled) PrimaryBlack else OnSurfaceVariantGray
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = item.topic,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                color = if (item.isEnabled) PrimaryBlack else OnSurfaceVariantGray
                            ),
                            softWrap = true
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Switch
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .scale(0.82f)
                        .testTag("sub_toggle_${item.id}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceContainerLowest,
                        checkedTrackColor = PrimaryBlack,
                        uncheckedThumbColor = SurfaceContainerLowest,
                        uncheckedTrackColor = SurfaceContainerLow
                    )
                )
            }

            // Row 2: Full topic (Auto-wrap monospace) when Remark Name is present
            if (item.name.isNotBlank()) {
                Text(
                    text = item.topic,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        color = if (item.isEnabled) OnSurfaceDark else OutlineGray
                    ),
                    softWrap = true
                )
            }

            // Row 3 (Bottom): Left area: QoS + Retain + Message Count; Right area: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom left: QoS badge, Retain Handling, and message metrics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // QoS Badge (User rule: QoS 0 placed in bottom-left before message metrics)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "QoS ${item.qos}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceDark
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Retain Handling badge
                    if (item.retainHandling > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "R${item.retainHandling}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceVariantGray
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Text(
                        text = "· ${if (item.isEnabled) "${item.msgCount} 报文" else "已暂停"}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.isEnabled) OnSurfaceVariantGray else OutlineGray
                        )
                    )
                }

                // Bottom right: Compact action buttons (复制、编辑、删除)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制主题",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑订阅",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除订阅",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Production-grade Subscription Editor Modal Dialog supporting:
 * 1. Standard MQTT Wildcard topic insertion and validation (+ and #)
 * 2. Theme Dot Color picker (8 standard IoT palette colors)
 * 3. QoS level selection (0, 1, 2)
 * 4. Retain handling mode configuration
 * 5. Activation state toggle and deletion
 */
@Composable
private fun SubscriptionConfigModalDialog(
    initialItem: SubscriptionItem?,
    onDismiss: () -> Unit,
    onSave: (SubscriptionItem) -> Unit,
    onDelete: (String) -> Unit
) {
    val isEditMode = initialItem != null

    var topic by remember { mutableStateOf(initialItem?.topic ?: "") }
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var qos by remember { mutableIntStateOf(initialItem?.qos ?: 0) }
    var retainHandling by remember { mutableIntStateOf(initialItem?.retainHandling ?: 0) }
    var isEnabled by remember { mutableStateOf(initialItem?.isEnabled ?: true) }
    var dotColorHex by remember {
        mutableLongStateOf(initialItem?.dotColorHex ?: 0xFF10B981)
    }

    // Real-time MQTT topic syntax check
    val validationResult = remember(topic) {
        if (topic.isBlank()) null else MqttTopicUtil.validateSubscriptionTopic(topic)
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
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header (极简现代，与新增发布弹窗完全看齐)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "编辑订阅主题" else "新建订阅主题",
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

                // 1. 订阅主题 (Topic)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "订阅主题 (Topic)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                        )
                        if (validationResult != null && !validationResult.isValid) {
                            Text(
                                text = validationResult.errorMessage ?: "语法错误",
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_dialog_topic_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (topic.isEmpty()) {
                                    Text(
                                        text = "例如: sensor/+/status 或 device/#",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // 紧凑高级的快速通配符小药丸 (+ / #) 与 清空按钮
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceContainerDefault)
                                    .clickable {
                                        val trimmed = topic.trim()
                                        topic = if (trimmed.isEmpty()) "+"
                                        else if (trimmed.endsWith("/")) "$trimmed+"
                                        else "$trimmed/+"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                    .background(SurfaceContainerDefault)
                                    .clickable {
                                        val trimmed = topic.trim()
                                        topic = if (trimmed.isEmpty()) "#"
                                        else if (trimmed.endsWith("/")) "$trimmed#"
                                        else "$trimmed/#"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                            if (topic.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "清空",
                                    tint = OutlineGray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { topic = "" }
                                )
                            }
                        }
                    }
                }

                // 2. 备注名称 (别名)
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_dialog_name_input"),
                            textStyle = TextStyle(fontSize = 12.5.sp, color = PrimaryBlack),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (name.isEmpty()) {
                                    Text(
                                        text = "可选，如：网关 / 雷达",
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

                // 3. 服务质量 (QoS) - 严格对齐与居中
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        listOf(
                            0 to "QoS 0 最多一次",
                            1 to "QoS 1 至少一次",
                            2 to "QoS 2 恰好一次"
                        ).forEach { (qVal, qLabel) ->
                            val selected = qos == qVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrimaryBlack else Color.Transparent)
                                    .clickable { qos = qVal },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = qLabel,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. 保留消息处理机制 (Retain Handling) - 严格对齐与居中
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "保留消息处理机制 (Retain Handling)",
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
                        listOf(
                            0 to "0: 建立时发送",
                            1 to "1: 仅初次发送",
                            2 to "2: 不发送保留"
                        ).forEach { (rVal, rLabel) ->
                            val selected = retainHandling == rVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrimaryBlack else Color.Transparent)
                                    .clickable { retainHandling = rVal },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rLabel,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }

                // 5. 主题颜色标识 (精致等宽微胶囊轨道)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "主题颜色标识",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MqttTopicUtil.IOT_DOT_COLORS.forEach { (colorHex, colorName) ->
                            val isSelected = dotColorHex == colorHex
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 22.dp else 16.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .clickable { dotColorHex = colorHex }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, PrimaryBlack, CircleShape)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. 激活开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "立即激活订阅",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimaryWhite,
                            checkedTrackColor = PrimaryBlack,
                            uncheckedThumbColor = OnSurfaceVariantGray,
                            uncheckedTrackColor = SurfaceContainerLow
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 7. 底部操作按钮 (取消、删除、保存，高度统一 38dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        TextButton(
                            onClick = { onDelete(initialItem!!.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "删除", fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariantGray),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(text = "取消", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val trimmedTopic = topic.trim()
                                val valid = MqttTopicUtil.validateSubscriptionTopic(trimmedTopic)
                                if (!valid.isValid) {
                                    return@Button
                                }
                                val savedItem = SubscriptionItem(
                                    id = initialItem?.id ?: UUID.randomUUID().toString(),
                                    topic = trimmedTopic,
                                    qos = qos,
                                    msgCount = initialItem?.msgCount ?: 0,
                                    lastTimeText = initialItem?.lastTimeText ?: "刚刚",
                                    isEnabled = isEnabled,
                                    dotColorHex = dotColorHex,
                                    name = name.trim(),
                                    retainHandling = retainHandling
                                )
                                onSave(savedItem)
                            },
                            enabled = topic.isNotBlank() && (validationResult?.isValid != false),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlack,
                                contentColor = OnPrimaryWhite,
                                disabledContainerColor = SurfaceContainerLow,
                                disabledContentColor = OutlineGray
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("sub_dialog_save_btn")
                        ) {
                            Text(
                                text = if (isEditMode) "保存修改" else "保存订阅",
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

