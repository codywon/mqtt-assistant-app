package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.MqttConnectionState
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest

@Composable
fun AppTopBar(
    isConnected: Boolean = true,
    connectionState: MqttConnectionState = if (isConnected) MqttConnectionState.CONNECTED else MqttConnectionState.DISCONNECTED,
    brokerHost: String,
    reconnectCountdown: Int = 0,
    onBadgeClick: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onSwitchBroker: () -> Unit = onOpenAiChat
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, spotColor = Color.Black.copy(alpha = 0.05f)),
        color = SurfaceContainerLowest.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "MQTT Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            letterSpacing = (-0.3).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Broker badge (单一权威连接状态与一键重连/断开控制)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceContainerLow)
                            .clickable(onClick = onBadgeClick)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("top_broker_badge")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.5.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        MqttConnectionState.CONNECTED -> Color(0xFF10B981)
                                        MqttConnectionState.CONNECTING,
                                        MqttConnectionState.RECONNECTING -> PrimaryBlack.copy(alpha = 0.4f)
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val badgeText = when {
                            brokerHost.isBlank() -> "未配置 Broker · 点击添加"
                            connectionState == MqttConnectionState.CONNECTED -> brokerHost
                            connectionState == MqttConnectionState.CONNECTING -> "正在连接..."
                            connectionState == MqttConnectionState.RECONNECTING -> "重连中 (${reconnectCountdown}s)"
                            connectionState == MqttConnectionState.DISCONNECTED -> "$brokerHost · 点击连接"
                            connectionState == MqttConnectionState.ERROR -> "$brokerHost · 连接失败"
                            else -> "未连接 · 点击连接"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (connectionState == MqttConnectionState.CONNECTED) PrimaryBlack else OnSurfaceVariantGray
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 右侧：AI 智能数据分析入口图标 (AutoAwesome 星辉)
                IconButton(
                    onClick = onOpenAiChat,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("top_ai_chat_btn")
                        .semantics { contentDescription = "AI 智能数据分析" }
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI 数据分析",
                        tint = PrimaryBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
        }
    }
}

@Composable
fun AppBottomNavBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, spotColor = Color.Black.copy(alpha = 0.08f)),
        color = SurfaceContainerLowest.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navTabs = listOf(
                    AppScreen.LiveLogs,
                    AppScreen.Publish,
                    AppScreen.Subscribe,
                    AppScreen.Settings
                )
                navTabs.forEach { screen ->
                    val isSelected = currentScreen == screen
                    val iconVector = when (screen) {
                        AppScreen.LiveLogs -> Icons.Default.Inbox
                        AppScreen.Publish -> Icons.AutoMirrored.Filled.Send
                        AppScreen.Subscribe -> Icons.Default.Podcasts
                        AppScreen.Settings -> Icons.Default.Tune
                        AppScreen.AiChat -> Icons.Default.AutoAwesome
                    }

                    NavBarItem(
                        icon = iconVector,
                        label = screen.navLabel,
                        isSelected = isSelected,
                        testTag = screen.testTag,
                        onClick = { onNavigate(screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val color = if (isSelected) PrimaryBlack else OnSurfaceVariantGray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(64.dp)
            .height(52.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Tab,
                onClick = onClick
            )
            .testTag(testTag)
            .semantics { contentDescription = label }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = color
            )
        )
    }
}
