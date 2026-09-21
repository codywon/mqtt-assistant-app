package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BrokerProfile
import com.example.ui.theme.OutlineGray
import java.util.UUID
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MqttConnectionState
import com.example.ui.theme.ErrorContainerRed
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnErrorContainerRed
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SecondaryContainerMint
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.viewmodel.MqttAssistantViewModel

@Composable
fun SettingsScreen(
    viewModel: MqttAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.serverConfig.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val reconnectCountdown by viewModel.reconnectCountdown.collectAsState()
    val isSaving by viewModel.isSavingSettings.collectAsState()
    val brokerProfiles by viewModel.brokerProfiles.collectAsState()
    val activeBrokerId by viewModel.activeBrokerId.collectAsState()

    var showBrokerDialog by remember { mutableStateOf(false) }
    var editingBroker by remember { mutableStateOf<BrokerProfile?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showProtocolDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Current Active Server Glance Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "当前连接服务器",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }

                    // Connected badge (Strict Monochrome)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (config.isConnected) PrimaryBlack else OutlineGray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.isConnected) "已连接" else "未连接",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (config.isConnected) PrimaryBlack else OnSurfaceVariantGray
                            )
                        )
                    }
                }

                // Inner card details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Broker 节点",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                        )
                        Text(
                            text = "${config.host}:${config.port}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Client ID",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                        )
                        Text(
                            text = config.clientId,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = PrimaryBlack
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "传输协议",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                        )
                        Text(
                            text = "${config.protocol} (TCP)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = PrimaryBlack
                            )
                        )
                    }
                }

                // Action buttons: 规范统一的 42.dp 与 10.dp 圆角
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (brokerProfiles.isNotEmpty()) {
                                val currentIndex = brokerProfiles.indexOfFirst { it.id == activeBrokerId }
                                val nextIndex = (currentIndex + 1).coerceAtLeast(0) % brokerProfiles.size
                                viewModel.selectBroker(brokerProfiles[nextIndex].id)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("switch_node_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "切换节点 (${brokerProfiles.size}可用)",
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { viewModel.toggleConnection() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (config.isConnected) SurfaceContainerDefault else PrimaryBlack,
                            contentColor = if (config.isConnected) ErrorRed else OnPrimaryWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("disconnect_btn")
                    ) {
                        Icon(
                            imageVector = if (config.isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                            contentDescription = null,
                            tint = if (config.isConnected) ErrorRed else OnPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (config.isConnected) "断开连接" else "重新连接",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                color = if (config.isConnected) ErrorRed else OnPrimaryWhite,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // 1.5 Multi-Broker Management Cluster Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("multi_broker_cluster_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "多 Broker 节点集群 (${brokerProfiles.size}个)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Button(
                        onClick = {
                            editingBroker = BrokerProfile(
                                id = UUID.randomUUID().toString(),
                                name = "自定义节点",
                                host = "",
                                port = 1883,
                                clientId = "client_mobile_" + UUID.randomUUID().toString().take(6),
                                username = "",
                                password = ""
                            )
                            showBrokerDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_broker_node_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = OnPrimaryWhite, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新增节点", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnPrimaryWhite)
                    }
                }

                Text(
                    text = "点击任意节点卡片即可一键切换并自动重连，支持独立保存各节点的主机、端口及鉴权凭证。",
                    style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariantGray, fontSize = 12.sp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brokerProfiles.forEach { profile ->
                        val isSelected = profile.id == activeBrokerId
                        BrokerNodeCard(
                            profile = profile,
                            isSelected = isSelected,
                            isConnected = isSelected && config.isConnected,
                            onSelect = {
                                if (!isSelected) {
                                    viewModel.selectBroker(profile.id)
                                }
                            },
                            onEdit = {
                                editingBroker = profile
                                showBrokerDialog = true
                            },
                            onDelete = {
                                viewModel.deleteBroker(profile.id)
                            },
                            canDelete = brokerProfiles.size > 1
                        )
                    }
                }
            }
        }

        // 2. Server Base Config Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = OnPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "服务器基础配置",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 15.sp
                        )
                    )
                }

                // Host address
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "主机地址 *",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = config.host,
                            onValueChange = { viewModel.updateHost(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setting_host_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true
                        )
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Port & KeepAlive Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(2f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "端口 *",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = config.port.toString(),
                                onValueChange = { viewModel.updatePort(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_port_input"),
                                textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                                cursorBrush = SolidColor(PrimaryBlack),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(3f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "保活心跳",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = config.keepAlive.toString(),
                                onValueChange = { viewModel.updateKeepAlive(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_keepalive_input"),
                                textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                                cursorBrush = SolidColor(PrimaryBlack),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Text(
                                text = "秒",
                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                            )
                        }
                    }
                }

                // Client ID
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "客户端标识",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = config.clientId,
                            onValueChange = { viewModel.updateClientId(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setting_clientid_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (config.clientId.isEmpty()) {
                                    Text(
                                        text = "留空自动生成",
                                        style = TextStyle(fontSize = 13.sp, color = OnSurfaceVariantGray.copy(alpha = 0.5f))
                                    )
                                }
                                innerTextField()
                            }
                        )
                        IconButton(
                            onClick = { viewModel.generateRandomClientId() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = "随机生成",
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Authentication Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = OnPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "认证信息",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 15.sp
                        )
                    )
                }

                // Username
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "用户名",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = config.username,
                            onValueChange = { viewModel.updateUsername(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_username_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true
                        )
                    }
                }

                // Password
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "密码",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = config.password,
                            onValueChange = { viewModel.updatePassword(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setting_password_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            cursorBrush = SolidColor(PrimaryBlack),
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
                        )
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "切换密码可见",
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Advanced Options
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = OnPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "高级选项",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 15.sp
                        )
                    )
                }

                // Protocol Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "协议版本",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "MQTT Standard Specification",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SurfaceContainerLow)
                                .clickable { showProtocolDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("protocol_dropdown_btn")
                        ) {
                            Text(
                                text = config.protocol,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryBlack
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = OnSurfaceVariantGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showProtocolDropdown,
                            onDismissRequest = { showProtocolDropdown = false }
                        ) {
                            listOf("MQTT 3.1.1", "MQTT 5.0", "MQTT 3.1").forEach { proto ->
                                DropdownMenuItem(
                                    text = { Text(proto) },
                                    onClick = {
                                        viewModel.updateProtocol(proto)
                                        showProtocolDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Clean Session
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "清除会话",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "断开时不保留未完成的 QOS 消息与离线订阅",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.cleanSession,
                        onCheckedChange = { viewModel.toggleCleanSession() },
                        modifier = Modifier.testTag("clean_session_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // Auto Reconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动重连",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "弱网或断线后启用指数避让算法持续重试",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.autoReconnect,
                        onCheckedChange = { viewModel.toggleAutoReconnect() },
                        modifier = Modifier.testTag("auto_reconnect_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // TLS / SSL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TLS 安全加密传输",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "启用 SSL/TLS 加密通道 (自动信任自签名证书)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.tlsEnabled,
                        onCheckedChange = { viewModel.toggleTls() },
                        modifier = Modifier.testTag("tls_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }
            }
        }

        // 5. Background Keep-Alive & Auto-Reconnect Engine (Production Grade)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "后台常驻与断线重连",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "生产级引擎",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                    }
                }

                // Status Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceContainerLow)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "连接引擎状态",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                        val statusDesc = when (connectionState) {
                            MqttConnectionState.CONNECTED -> "已连接 · ${config.host}:${config.port}"
                            MqttConnectionState.CONNECTING -> "正在建立 TCP/MQTT 会话..."
                            MqttConnectionState.RECONNECTING -> "意外断线 · 自动重连中 (${reconnectCountdown}s)"
                            MqttConnectionState.DISCONNECTED -> "已断开连接"
                            MqttConnectionState.ERROR -> "连接遇到异常"
                        }
                        Text(
                            text = statusDesc,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        )
                    }

                    Button(
                        onClick = { viewModel.toggleConnection() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("reconnect_action_btn")
                    ) {
                        Text(
                            text = if (connectionState == MqttConnectionState.CONNECTED) "断开测试" else "立即重连",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // 1. Auto Reconnect switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动断线重连",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "弱网、网络切换或服务端重启时，自动以退避算法重试并恢复订阅",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.autoReconnect,
                        onCheckedChange = { viewModel.toggleAutoReconnect() },
                        modifier = Modifier.testTag("settings_auto_reconnect_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 2. Foreground Keep-Alive Service
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "后台常驻保活服务 (Foreground Service)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "启用 Android 前台保活通知与定时心跳，防止切到后台或锁屏被系统冻结",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.backgroundKeepAliveEnabled,
                        onCheckedChange = { viewModel.toggleBackgroundKeepAlive(context) },
                        modifier = Modifier.testTag("settings_background_service_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 3. CPU WakeLock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CPU 唤醒锁 (WakeLock)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "息屏待机状态下保持 CPU 微唤醒，确保长连接心跳与报文毫秒级响应",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.wakeLockEnabled,
                        onCheckedChange = { viewModel.toggleWakeLock() },
                        modifier = Modifier.testTag("settings_wakelock_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // 4. Ignore Battery Optimizations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "忽略电池优化 (防系统杀后台)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "申请无限制后台电源策略，彻底杜绝程序最小化被厂商系统冻结",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("去设置", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 7. Log & Data Storage
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = OnPrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "日志与本地缓存",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceContainerDefault)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SharedPreferences",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                }

                // Auto rotate toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动轮转备份",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        )
                        Text(
                            text = "达到存储阈值后自动压缩落盘",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                    Switch(
                        checked = config.autoRotate,
                        onCheckedChange = { viewModel.toggleAutoRotate() },
                        modifier = Modifier.testTag("auto_rotate_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceContainerLowest,
                            checkedTrackColor = PrimaryBlack
                        )
                    )
                }

                // Metrics cards (1:1 绝对等高与严格对称排版)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "缓存报警阈值",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray, fontSize = 11.5.sp)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${config.bufferThreshold}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = PrimaryBlack
                                )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "条",
                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                            )
                        }
                        Text(
                            text = "超限循环覆盖",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "已占用空间",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray, fontSize = 11.5.sp)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${config.usedSpaceMb}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = PrimaryBlack
                                )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "MB",
                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariantGray)
                            )
                        }
                        Text(
                            text = "累计 ${config.packetCount} 条报文",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnSurfaceVariantGray
                            )
                        )
                    }
                }

                // Clear history button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "清理历史收发包记录",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariantGray)
                    )
                    Button(
                        onClick = { viewModel.clearAllData() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorContainerRed,
                            contentColor = OnErrorContainerRed
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("clear_all_data_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "清空全部数据",
                            style = TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }

        // 6. Save & Meta Action
        Button(
            onClick = { viewModel.saveAndApplySettings() },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlack,
                contentColor = OnPrimaryWhite
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("save_settings_btn")
        ) {
            if (isSaving) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "正在保存参数...",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "保存并应用配置",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                )
            }
        }

        // Footer version info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = OnSurfaceVariantGray,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "MQTT Assistant v1.0.0 (Android Native)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }
            Text(
                text = "Powered by codywon",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = OnSurfaceVariantGray.copy(alpha = 0.7f)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Modal dialog for editing or adding a broker profile
    if (showBrokerDialog && editingBroker != null) {
        BrokerProfileEditDialog(
            initialBroker = editingBroker!!,
            onDismiss = {
                showBrokerDialog = false
                editingBroker = null
            },
            onSave = { updated ->
                viewModel.saveOrUpdateBroker(updated)
                showBrokerDialog = false
                editingBroker = null
            }
        )
    }
}

@Composable
private fun BrokerNodeCard(
    profile: BrokerProfile,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceContainerLow else SurfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("broker_node_${profile.id}")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) PrimaryBlack else OutlineVariantLight
                            )
                    )
                    Text(
                        text = profile.name,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PrimaryBlack)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConnected) "已连接" else "已选用",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimaryWhite
                                )
                            )
                        }
                    }
                    if (profile.tlsEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerDefault)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TLS",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlack
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).testTag("edit_broker_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (canDelete && !isSelected) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_broker_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = ErrorRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${profile.host}:${profile.port}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryBlack
                    )
                )
                Text(
                    text = if (profile.username.isNotBlank()) "User: ${profile.username}" else "匿名",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = OnSurfaceVariantGray
                    )
                )
            }
        }
    }
}

@Composable
private fun BrokerProfileEditDialog(
    initialBroker: BrokerProfile,
    onDismiss: () -> Unit,
    onSave: (BrokerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialBroker.name) }
    var host by remember { mutableStateOf(initialBroker.host) }
    var portStr by remember { mutableStateOf(initialBroker.port.toString()) }
    var clientId by remember { mutableStateOf(initialBroker.clientId) }
    var username by remember { mutableStateOf(initialBroker.username) }
    var password by remember { mutableStateOf(initialBroker.password) }
    var protocol by remember { mutableStateOf(initialBroker.protocol) }
    var tlsEnabled by remember { mutableStateOf(initialBroker.tlsEnabled) }
    var cleanSession by remember { mutableStateOf(initialBroker.cleanSession) }
    var keepAliveStr by remember { mutableStateOf(initialBroker.keepAlive.toString()) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialBroker.host.isBlank()) "新增 Broker 节点" else "编辑 Broker 节点",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack,
                            fontSize = 16.sp
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OnSurfaceVariantGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.5.dp)

                // Name
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "节点名称 / 备注",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
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
                            modifier = Modifier.weight(1f).testTag("broker_dialog_name_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = PrimaryBlack),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Host & Port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(2.5f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "服务器主机 (Host)",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
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
                                value = host,
                                onValueChange = { host = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_host_input"),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = PrimaryBlack
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "端口",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
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
                                value = portStr,
                                onValueChange = { portStr = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_port_input"),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = PrimaryBlack
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }
                }

                // Client ID
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "客户端标识 (Client ID)",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        )
                        Text(
                            text = "随机生成",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = PrimaryBlack,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable {
                                clientId = "client_mobile_" + UUID.randomUUID().toString().take(6)
                            }
                        )
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
                            value = clientId,
                            onValueChange = { clientId = it },
                            modifier = Modifier.weight(1f).testTag("broker_dialog_clientid_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = PrimaryBlack
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(PrimaryBlack)
                        )
                    }
                }

                // Username & Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "用户名",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
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
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_username_input"),
                                textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "密码",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
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
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.weight(1f).testTag("broker_dialog_password_input"),
                                textStyle = TextStyle(fontSize = 12.sp, color = PrimaryBlack),
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                cursorBrush = SolidColor(PrimaryBlack)
                            )
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = OutlineGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // TLS & CleanSession toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TLS 加密连接 (MQTTS)",
                        style = TextStyle(fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = tlsEnabled,
                        onCheckedChange = { tlsEnabled = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "清除会话 (Clean Session)",
                        style = TextStyle(fontSize = 12.sp, color = PrimaryBlack, fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = cleanSession,
                        onCheckedChange = { cleanSession = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.6.dp, OutlineGray),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("取消", color = OnSurfaceVariantGray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val p = portStr.toIntOrNull() ?: 1883
                            val ka = keepAliveStr.toIntOrNull() ?: 60
                            val updated = initialBroker.copy(
                                name = name.ifBlank { host },
                                host = host.trim(),
                                port = p,
                                clientId = clientId.trim(),
                                username = username.trim(),
                                password = password,
                                protocol = protocol,
                                tlsEnabled = tlsEnabled,
                                cleanSession = cleanSession,
                                keepAlive = ka
                            )
                            onSave(updated)
                        },
                        enabled = host.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlack,
                            contentColor = OnPrimaryWhite
                        ),
                        modifier = Modifier.height(38.dp).testTag("broker_dialog_save_btn")
                    ) {
                        Text("保存节点", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
