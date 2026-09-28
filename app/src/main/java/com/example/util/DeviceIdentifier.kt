package com.example.util

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * 设备硬件专属标识与 MQTT Client ID 智能分配工具
 *
 * 工业级设计考量：
 * 1. 绝不使用 WifiInfo.getMacAddress()（Android 6.0+ 全面封禁，所有设备统一返回 02:00:00:00:00:00 会造成 100% 顶下线冲突）
 * 2. 优先基于合规免权限的 Settings.Secure.ANDROID_ID（每台物理设备独一无二的 64 位十六进制码）
 * 3. 兜底结合应用级持久化随机 Installation ID，确保多台手机同时安装或导入相同备份时，绝不撞车互相踢下线！
 */
object DeviceIdentifier {

    private const val PREFS_NAME = "mqtt_assistant_device_id"
    private const val KEY_INSTALL_ID = "key_persistent_install_id"

    /**
     * 获取本机专属的短特征码（6位小写十六进制字符串，如 "a3f89b"）
     */
    fun getShortDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )?.trim()?.lowercase()

            // 过滤已知模拟器默认假值或异常空值
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c" && androidId.length >= 6) {
                androidId.takeLast(6)
            } else {
                getOrGeneratePersistentId(context)
            }
        } catch (_: Exception) {
            getOrGeneratePersistentId(context)
        }
    }

    /**
     * 生成与本机硬件绑定的稳定默认 Client ID（如 "client_mobile_a3f89b"）
     */
    fun generateDefaultClientId(context: Context, prefix: String = "client_mobile"): String {
        val shortId = getShortDeviceId(context)
        return "${prefix}_$shortId"
    }

    /**
     * 生成带一次性随机后缀的 Client ID（如 "client_mobile_9e21df"）
     */
    fun generateRandomClientId(prefix: String = "client_mobile"): String {
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
        return "${prefix}_$randomSuffix"
    }

    private fun getOrGeneratePersistentId(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
            sp.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }
}
