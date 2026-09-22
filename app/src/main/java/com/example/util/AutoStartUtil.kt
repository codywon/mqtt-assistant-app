package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 针对国内各主流厂商手机（小米/红米、华为/荣耀、OPPO、vivo、魅族、三星等）
 * 提供跳转系统“自启动管理 / 进程保活白名单”的辅助工具，
 * 达成如微信般的后台常驻与开机/锁屏随时自启动拉起守护能力。
 */
object AutoStartUtil {

    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()

        // 1. 小米 / 红米 (MIUI / HyperOS)
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        }

        // 2. 华为 / 荣耀 (EMUI / HarmonyOS / MagicOS)
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            )
        }

        // 3. OPPO / OnePlus / Realme (ColorOS)
        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
            )
        }

        // 4. vivo / iQOO (OriginOS / FuntouchOS)
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            )
        }

        // 5. 魅族 (Flyme)
        if (manufacturer.contains("meizu")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.security.SHOW_APPSEC"
                    )
                ).putExtra("packageName", context.packageName)
            )
        }

        // 6. 三星 (One UI)
        if (manufacturer.contains("samsung")) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"
                    )
                )
            )
        }

        // 通用兜底：应用详情页
        intents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )

        var launched = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
                // 尝试下一个 Intent
            }
        }

        if (launched) {
            Toast.makeText(context, "请允许「自启动 / 后台启动」，以保障后台与熄屏随时接收消息", Toast.LENGTH_LONG).show()
        }
    }
}
