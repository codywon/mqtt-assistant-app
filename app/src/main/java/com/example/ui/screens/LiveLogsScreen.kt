package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.MqttLogPacket
import org.json.JSONArray
import org.json.JSONObject
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.viewmodel.MqttAssistantViewModel

@Composable
fun LiveLogsScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val packets by viewModel.livePackets.collectAsState()
    val filterQuery by viewModel.logFilterQuery.collectAsState()
    val selectedCategory by viewModel.logSelectedCategory.collectAsState()
    val isPaused by viewModel.isRecordingPaused.collectAsState()
    val isJsonPretty by viewModel.isJsonPrettyFormat.collectAsState()
    val filterJsonOnly by viewModel.filterJsonOnly.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val includeFilters by viewModel.includeTopicFilters.collectAsState()
    val excludeFilters by viewModel.excludeTopicFilters.collectAsState()

    val dynamicCategories = remember(subscriptions, packets) {
        val list = mutableListOf("全部" to PrimaryBlack)
        val added = mutableSetOf<String>()
        subscriptions.forEach { sub ->
            val title = sub.name.ifBlank { sub.topic.substringBefore('/') }
            if (title.isNotBlank() && added.add(title)) {
                list.add(title to Color(sub.dotColorHex))
            }
        }
        packets.forEach { p ->
            if (p.category.isNotBlank() && added.add(p.category)) {
                list.add(p.category to Color(p.dotColorHex))
            }
        }
        list
    }

    val filteredPackets = packets.filter { packet ->
        val matchesAllowed = MqttTopicUtil.isTopicAllowed(packet.topic, includeFilters, excludeFilters)
        val matchesCategory = selectedCategory == "全部" || packet.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = filterQuery.isBlank() ||
                packet.topic.contains(filterQuery, ignoreCase = true) ||
                packet.payload.contains(filterQuery, ignoreCase = true)
        val trimmed = packet.payload.trim()
        val isJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
        val matchesJsonFilter = !filterJsonOnly || isJson
        matchesAllowed && matchesCategory && matchesQuery && matchesJsonFilter
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Clean Action Header: Search + Format switch + Pause/Resume + Clear
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
                        value = filterQuery,
                        onValueChange = { viewModel.logFilterQuery.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("log_filter_input"),
                        textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                        cursorBrush = SolidColor(PrimaryBlack),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (filterQuery.isEmpty()) {
                                Text(
                                    text = "搜索主题或报文内容...",
                                    style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (filterQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空过滤",
                            tint = OutlineGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.logFilterQuery.value = "" }
                        )
                    }
                }

                // JSON Pretty Toggle Button
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isJsonPretty) PrimaryBlack else SurfaceContainerLow)
                        .clickable { viewModel.toggleJsonPretty() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JSON",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isJsonPretty) OnPrimaryWhite else PrimaryBlack
                        )
                    )
                }

                // Pause / Play toggle
                IconButton(
                    onClick = { viewModel.toggleStreamPause() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .testTag("stream_pause_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "继续" else "暂停",
                        tint = PrimaryBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear logs
                IconButton(
                    onClick = { viewModel.clearLogStream() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .testTag("stream_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "清空报文",
                        tint = OnSurfaceVariantGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live Packet Cards List or Minimal Empty State
        if (filteredPackets.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filterQuery.isNotBlank()) "未匹配到相关报文" else "暂无实时报文 (等待 Broker 消息推送)",
                        style = TextStyle(fontSize = 13.sp, color = OutlineGray)
                    )
                }
            }
        } else {
            items(filteredPackets, key = { it.id }) { packet ->
                LivePacketCard(
                    packet = packet,
                    isJsonPretty = isJsonPretty,
                    onCopyPayload = {
                        val toCopy = if (isJsonPretty) {
                            try {
                                val t = packet.payload.trim()
                                if (t.startsWith("{") && t.endsWith("}")) JSONObject(t).toString(2)
                                else if (t.startsWith("[") && t.endsWith("]")) JSONArray(t).toString(2)
                                else packet.payload
                            } catch (e: Exception) {
                                packet.payload
                            }
                        } else packet.payload
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("payload", toCopy))
                        viewModel.showToast("已复制报文载荷")
                    },
                    onResend = {
                        viewModel.resendLogPacket(packet)
                    },
                    onDetails = {
                        viewModel.showToast("报文详情: ${packet.topic} ${packet.packetSeq}")
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LivePacketCard(
    packet: MqttLogPacket,
    isJsonPretty: Boolean,
    onCopyPayload: () -> Unit,
    onResend: () -> Unit,
    onDetails: () -> Unit
) {
    val (formattedPayload, formatTag) = remember(packet.payload, isJsonPretty) {
        val trimmed = packet.payload.trim()
        if (isJsonPretty) {
            try {
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    JSONObject(trimmed).toString(2) to "JSON"
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    JSONArray(trimmed).toString(2) to "JSON"
                } else {
                    trimmed to "TEXT"
                }
            } catch (e: Exception) {
                trimmed to "TEXT"
            }
        } else {
            val isJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                         (trimmed.startsWith("[") && trimmed.endsWith("]"))
            trimmed to (if (isJson) "JSON" else "TEXT")
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_packet_${packet.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Dot + Topic + Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(packet.dotColorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = packet.topic,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PrimaryBlack
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCopyPayload,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制 Payload",
                        tint = OnSurfaceVariantGray,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Meta Row: [Q0] format tag, seq, timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceContainerDefault)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "[Q${packet.qos}]",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    )
                }

                // Format badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (formatTag == "JSON") AccentEmerald.copy(alpha = 0.15f) else SurfaceContainerDefault)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatTag,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (formatTag == "JSON") AccentEmerald else OnSurfaceVariantGray
                        )
                    )
                }

                Text(
                    text = packet.packetSeq,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
                Text(
                    text = packet.timestamp,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }

            // Code editor payload box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerLow)
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = formattedPayload,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = OnSurfaceDark
                    )
                )
            }

            // Footer info & Resend action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = packet.devInfo,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↺ 重发",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlack
                        ),
                        modifier = Modifier
                            .clickable(onClick = onResend)
                            .padding(4.dp)
                            .testTag("resend_button")
                            .semantics { contentDescription = "↺ 重发" }
                    )

                    Text(
                        text = "↗ 详情",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = OnSurfaceVariantGray
                        ),
                        modifier = Modifier
                            .clickable(onClick = onDetails)
                            .padding(4.dp)
                            .testTag("details_button")
                    )
                }
            }
        }
    }
}
