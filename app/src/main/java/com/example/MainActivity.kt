package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.AppScreen
import com.example.ui.components.AppBottomNavBar
import com.example.ui.components.AppTopBar
import com.example.ui.screens.LiveLogsScreen
import com.example.ui.screens.PublishScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscribeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceCanvas
import com.example.viewmodel.MqttAssistantViewModel
import androidx.activity.viewModels
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val assistantViewModel: MqttAssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        setContent {
            MyApplicationTheme {
                MqttAssistantApp(viewModel = assistantViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        assistantViewModel.onAppResume()
    }
}

@Composable
fun MqttAssistantApp(
    viewModel: MqttAssistantViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppScreen.LiveLogs.route
    val currentScreen = AppScreen.values().find { it.route == currentRoute } ?: AppScreen.LiveLogs

    val serverConfig by viewModel.serverConfig.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val reconnectCountdown by viewModel.reconnectCountdown.collectAsState()
    val vmScreen by viewModel.currentScreen.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync ViewModel navigation requests with NavController
    LaunchedEffect(vmScreen) {
        if (vmScreen.route != currentRoute) {
            navController.navigate(vmScreen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Collect Toast/Snackbar events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SurfaceCanvas,
        topBar = {
            AppTopBar(
                isConnected = serverConfig.isConnected,
                connectionState = connectionState,
                brokerHost = "${serverConfig.host}:${serverConfig.port}",
                reconnectCountdown = reconnectCountdown,
                onBadgeClick = {
                    viewModel.toggleConnection()
                },
                onSwitchBroker = {
                    viewModel.switchToNextBroker()
                }
            )
        },
        bottomBar = {
            AppBottomNavBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    viewModel.navigateTo(screen)
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            ) { data ->
                val message = data.visuals.message
                val isWarning = message.contains("请输入") || message.contains("未连接") || message.contains("失败")
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E293B).copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isWarning) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppScreen.LiveLogs.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            composable(
                route = AppScreen.Publish.route,
                enterTransition = {
                    if (initialState.destination.route == AppScreen.LiveLogs.route) {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(300)
                        )
                    } else {
                        EnterTransition.None
                    }
                }
            ) {
                PublishScreen(viewModel = viewModel)
            }

            composable(route = AppScreen.Subscribe.route) {
                SubscribeScreen(viewModel = viewModel)
            }

            composable(route = AppScreen.LiveLogs.route) {
                LiveLogsScreen(viewModel = viewModel)
            }

            composable(route = AppScreen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Kept for test compatibility (GreetingScreenshotTest).
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
