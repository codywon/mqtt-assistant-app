package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
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
    val includeFilters by viewModel.includeTopicFilters.collectAsState()
    val excludeFilters by viewModel.excludeTopicFilters.collectAsState()

    val runningCount = subscriptions.count { it.isEnabled }
    val totalCount = subscriptions.size

    val filteredList = subscriptions.filter {
        searchQuery.isBlank() || it.topic.contains(searchQuery.trim(), ignoreCase = true)
    }

    // State for modal dialog (creating or editing subscription)
    var isDialogVisible by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SubscriptionItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SubscriptionItem?>(null) }

    // State for PC-grade Topic Filter Rules (Include / Exclude)
    var isTopicFilterDialogVisible by remember { mutableStateOf(false) }

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

        // Clean, practical action header: Search + Add Subscription + Topic Filters + Quick controls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Search field
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
                                    text = "搜索已订阅...",
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

                // Topic Filter Rules Button (Reference PC Client)
                val totalFiltersCount = includeFilters.size + excludeFilters.size
                Button(
                    onClick = { isTopicFilterDialogVisible = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totalFiltersCount > 0) PrimaryBlack else SurfaceContainerLow,
                        contentColor = if (totalFiltersCount > 0) OnPrimaryWhite else PrimaryBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("open_topic_filter_dialog_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (totalFiltersCount > 0) "过滤 ($totalFiltersCount)" else "过滤",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalFiltersCount > 0) Color.White else PrimaryBlack
                        )
                    )
                }

                // Add button
                Button(
                    onClick = { openCreateDialog() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = OnPrimaryWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("open_add_sub_dialog_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "新建",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // Link self-test & probe button
                IconButton(
                    onClick = { viewModel.testPublishLoopback() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .testTag("sub_test_loopback_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "自测接收",
                        tint = PrimaryBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Active Topic Filter Rules Banner (Monochrome)
        if (includeFilters.isNotEmpty() || excludeFilters.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTopicFilterDialogVisible = true }
                        .testTag("active_topic_filter_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FilterAlt, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(16.dp))
                            val filterSummary = buildString {
                                if (excludeFilters.isNotEmpty()) append("已排除 ${excludeFilters.size} 项 ")
                                if (includeFilters.isNotEmpty()) append("已包含 ${includeFilters.size} 项")
                            }
                            Text(
                                text = "过滤已生效: $filterSummary (支持通配符)",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                            )
                        }
                        Text(
                            text = "配置 >",
                            style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, fontWeight = FontWeight.Medium)
                        )
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
                    onProbe = { viewModel.testPublishLoopback(sub) },
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
                    text = "确定要删除订阅「${target.topic}」吗？删除后将停止接收该主题的报文推送并移除相关统计。",
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
                        viewModel.showToast("已删除订阅: ${target.topic}")
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
                viewModel.removeSubscription(itemId)
                isDialogVisible = false
            }
        )
    }

    // Modal Dialog for PC-Grade Topic Filters (Include / Exclude)
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
}

@Composable
private fun SubscriptionItemCard(
    item: SubscriptionItem,
    onCardClick: () -> Unit,
    onToggle: () -> Unit,
    onProbe: () -> Unit,
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("sub_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Colored status dot + Topic Name + Badges + Switch
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
                    Text(
                        text = item.topic,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = if (item.isEnabled) PrimaryBlack else OnSurfaceVariantGray
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // QoS Badge (Monochrome)
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
                        Spacer(modifier = Modifier.width(4.dp))
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
                }

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

            // Row 2 (Bottom): Alias name (left) + Metrics + Actions (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom left: Alias (if set) and message metrics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (item.name.isNotBlank()) {
                        Text(
                            text = item.name,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryBlack
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "·",
                            style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                        )
                    }
                    Text(
                        text = if (item.isEnabled) "${item.msgCount} 报文" else "已暂停",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.isEnabled) OnSurfaceVariantGray else OutlineGray
                        )
                    )
                }

                // Bottom right: Compact action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onProbe,
                        modifier = Modifier.size(28.dp),
                        enabled = item.isEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "自测探针",
                            tint = if (item.isEnabled) PrimaryBlack else OutlineGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        // Color dot preview in title
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(dotColorHex))
                        )
                        Text(
                            text = if (isEditMode) "编辑订阅主题" else "新建订阅主题",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 17.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)

                // 1. Topic input field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "订阅主题 (Topic Filter)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
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
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = OutlineGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = topic,
                            onValueChange = { topic = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_dialog_topic_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (topic.isEmpty()) {
                                    Text(
                                        text = "例如: devices/+/status 或 sensor/#",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = OutlineGray
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (topic.isNotEmpty()) {
                            IconButton(
                                onClick = { topic = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空",
                                    tint = OutlineGray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // MQTT Wildcard Helper Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "通配符:",
                            style = TextStyle(fontSize = 11.sp, color = OutlineGray)
                        )

                        // Single-level '+' helper
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerLow)
                                .clickable {
                                    val trimmed = topic.trim()
                                    topic = if (trimmed.isEmpty()) "+"
                                    else if (trimmed.endsWith("/")) "$trimmed+"
                                    else "$trimmed/+"
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "+ 单级通配",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryBlack
                                )
                            )
                        }

                        // Multi-level '#' helper
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerLow)
                                .clickable {
                                    val trimmed = topic.trim()
                                    topic = if (trimmed.isEmpty()) "#"
                                    else if (trimmed.endsWith("/")) "$trimmed#"
                                    else "$trimmed/#"
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "# 多级通配 (末端)",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryBlack
                                )
                            )
                        }
                    }
                }

                // 1.5 Alias / Name Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "主题别名 / 备注 (可选)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_dialog_name_input"),
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (name.isEmpty()) {
                                    Text(
                                        text = "例如: 高校设备流、断路器监控",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                // 2. Dot Color Picker (主题圆点颜色选取)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "主题标识颜色 (与实时日志及监控图表联动)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MqttTopicUtil.IOT_DOT_COLORS.forEach { (colorHex, colorName) ->
                            val isSelected = dotColorHex == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选取 $colorName",
                                        tint = OnPrimaryWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. QoS Selector (0, 1, 2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "服务质量等级 (QoS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrimaryBlack else Color.Transparent)
                                    .clickable { qos = qVal }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = qLabel,
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

                // 4. Retain Handling (保留消息处理机制)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "保留消息机制 (Retain Handling)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrimaryBlack else Color.Transparent)
                                    .clickable { retainHandling = rVal }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rLabel,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray
                                    )
                                )
                            }
                        }
                    }
                }

                // 5. Activation Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "立即生效订阅",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "保存后向 Broker 发送 SUBSCRIBE 报文",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.scale(0.85f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack,
                            uncheckedThumbColor = SurfaceContainerLowest,
                            uncheckedTrackColor = SurfaceContainerLow
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Action Buttons: Cancel, Delete (if edit), Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        TextButton(
                            onClick = { onDelete(initialItem!!.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
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
                            colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariantGray)
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlack,
                                contentColor = OnPrimaryWhite,
                                disabledContainerColor = SurfaceContainerLow,
                                disabledContentColor = OutlineGray
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("sub_dialog_save_btn")
                        ) {
                            Text(
                                text = if (isEditMode) "保存修改" else "保存订阅",
                                style = TextStyle(
                                    fontSize = 13.sp,
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

/**
 * PC-Grade Topic Filter Rules Dialog (Includes and Excludes with Wildcard Support).
 * Strictly Monochrome per user requirements.
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "主题过滤条件 (参考 PC 端)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 16.sp
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = OnSurfaceVariantGray, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)

                // Segment Tabs: Exclude Tab (Recommended / Priority) vs Include Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "exclude" to "排除条件 (${excludeFilters.size})",
                        "include" to "包含条件 (${includeFilters.size})"
                    ).forEach { (tabKey, tabLabel) ->
                        val selected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) PrimaryBlack else Color.Transparent)
                                .clickable {
                                    activeTab = tabKey
                                    inputPattern = ""
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) OnPrimaryWhite else OnSurfaceVariantGray
                                )
                            )
                        }
                    }
                }

                // Input field + Add button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = inputPattern,
                            onValueChange = { inputPattern = it },
                            modifier = Modifier.weight(1f).testTag("filter_pattern_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = PrimaryBlack,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack),
                            decorationBox = { innerTextField ->
                                if (inputPattern.isEmpty()) {
                                    Text(
                                        text = if (isExclude) "输入排除主题，支持 + 和 #" else "输入包含主题，支持 + 和 #",
                                        style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (inputPattern.isNotEmpty()) {
                            IconButton(onClick = { inputPattern = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = OutlineGray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

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
                        modifier = Modifier.height(42.dp).testTag("filter_pattern_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Wildcard helper chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "快捷通配符:", style = TextStyle(fontSize = 11.sp, color = OutlineGray))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerLow)
                            .clickable {
                                val t = inputPattern.trim()
                                inputPattern = if (t.isEmpty()) "+" else if (t.endsWith("/")) "$t+" else "$t/+"
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ 单级通配", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PrimaryBlack))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerLow)
                            .clickable {
                                val t = inputPattern.trim()
                                inputPattern = if (t.isEmpty()) "#" else if (t.endsWith("/")) "$t#" else "$t/#"
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("# 多级通配 (末端)", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PrimaryBlack))
                    }
                }

                // Description hint
                Text(
                    text = if (isExclude) {
                        "★ 排除条件优先级最高！匹配排除规则的消息将被直接丢弃隐藏。\n示例: Collect/dlt_data/# 或 college/ping/#"
                    } else {
                        "★ 包含条件：若配置了包含规则，仅接收并记录匹配包含规则的消息。\n示例: sensor/+/temp 或 college/#"
                    },
                    style = TextStyle(fontSize = 11.sp, color = OnSurfaceVariantGray, lineHeight = 16.sp)
                )

                // List header & Clear button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已添加 ${currentList.size} 个${if (isExclude) "排除" else "包含"}条件",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                    )
                    if (currentList.isNotEmpty()) {
                        TextButton(
                            onClick = { if (isExclude) onClearExcludes() else onClearIncludes() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("清空全部", fontSize = 11.sp, color = OnSurfaceVariantGray)
                        }
                    }
                }

                // List of tag filters
                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExclude) "暂无排除条件，添加后将隐藏匹配的日志" else "暂无包含条件，添加后将只显示匹配的日志",
                            style = TextStyle(fontSize = 12.sp, color = OutlineGray)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentList.forEachIndexed { index, pattern ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerLow)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
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
                                IconButton(
                                    onClick = { if (isExclude) onRemoveExclude(index) else onRemoveInclude(index) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "删除条件", tint = OnSurfaceVariantGray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack, contentColor = OnPrimaryWhite),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
