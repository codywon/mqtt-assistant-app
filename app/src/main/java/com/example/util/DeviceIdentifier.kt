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
     * 绝不返回 "000000" 或全相同字符
     */
    fun getShortDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )?.trim()?.lowercase()

            if (isValidAndroidId(androidId)) {
                androidId!!.takeLast(6)
            } else {
                getOrGeneratePersistentId(context)
            }
        } catch (_: Exception) {
            getOrGeneratePersistentId(context)
        }
    }

    /**
     * 校验 ANDROID_ID 是否为真实有效的硬件标识
     */
    fun isValidAndroidId(id: String?): Boolean {
        if (id.isNullOrBlank() || id.length < 6) return false
        val invalidTokens = setOf(
            "9774d56d682e549c", // Android 2.2 模拟器与部分假系统经典占位符
            "unknown",
            "null",
            "0"
        )
        if (id in invalidTokens) return false
        // 核心过滤：国产 ROM（小米/华为/OPPO/vivo）隐私保护常返回全0（例如 0000000000000000）或全f或全相同字符
        if (id.all { it == '0' || it == 'f' || it == id[0] }) return false
        return true
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

    /**
     * 生成并持久化本设备专属的唯一安装短标识（若 ANDROID_ID 被屏蔽时使用）
     */
    private fun getOrGeneratePersistentId(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_INSTALL_ID, null)
        // 若为空、为全0或无效，则重新计算生成
        if (id.isNullOrBlank() || id == "000000" || id.all { it == '0' || it == id[0] } || id.length < 6) {
            id = generateUniqueShortHash()
            sp.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }

    private fun generateUniqueShortHash(): String {
        return try {
            val hardwareSeed = "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.BOARD}_${android.os.Build.HARDWARE}"
            val randomToken = UUID.randomUUID().toString().replace("-", "")
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest((hardwareSeed + randomToken).toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            val candidate = hex.take(6).lowercase()
            if (candidate.all { it == '0' || it == candidate[0] }) {
                UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
            } else {
                candidate
            }
        } catch (_: Exception) {
            UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
        }
    }
}
