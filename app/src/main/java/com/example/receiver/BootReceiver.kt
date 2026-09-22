package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.MqttStorageRepository
import com.example.service.MqttBackgroundService

/**
 * 开机自启动与进程拉起守护广播接收器：
 * 监听系统开机广播、快速开机、软件包覆盖安装以及网络恢复等广播。
 * 若用户开启了“开机自启动与进程守护”开关，并且配置了有效的 Broker 节点，
 * 自动拉起前台保活服务与 MQTT 会话引擎，达成如微信般的开机/被杀即刻守护自愈能力。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "onReceive broadcast action: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validActions) {
            val storage = MqttStorageRepository.getInstance(context.applicationContext)
            val isAutoStartEnabled = storage.loadAutoStartEnabled()
            Log.d(TAG, "isAutoStartEnabled: $isAutoStartEnabled")

            if (isAutoStartEnabled) {
                val profiles = storage.loadBrokerProfiles()
                val activeId = storage.loadActiveBrokerId()
                val activeBroker = profiles.find { it.id == activeId } ?: profiles.firstOrNull()

                if (activeBroker != null && activeBroker.host.isNotBlank()) {
                    val brokerHost = "${activeBroker.host}:${activeBroker.port}"
                    Log.i(TAG, "Auto-starting MqttBackgroundService on host: $brokerHost")
                    MqttBackgroundService.startKeepAlive(context.applicationContext, brokerHost)
                } else {
                    Log.w(TAG, "Auto-start enabled but no valid broker profile configured.")
                }
            }
        }
    }
}
