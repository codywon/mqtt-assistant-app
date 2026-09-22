package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.mqtt.MqttClientManager
import com.example.service.MqttBackgroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 穿透 Doze 深度休眠的精准脉冲心跳广播接收器：
 * 1. 由 AlarmManager 定期触发唤醒，即使 CPU 挂起休眠也能精准唤醒系统；
 * 2. 接收时持有短暂的 PARTIAL_WAKE_LOCK（10秒），保障发完 MQTT PINGREQ 或自愈重连；
 * 3. 避开 Android 8.0+ 后台启动服务的限制，直接在 Receiver 中执行非阻塞协程心跳；
 * 4. 全版本兼容调度，在 Android 12+ 自动检查 canScheduleExactAlarms 并优雅降级，杜绝崩溃。
 */
class AlarmPulseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmPulseReceiver"
        const val ACTION_ALARM_PULSE = "com.example.receiver.ACTION_ALARM_PULSE"
        private const val REQUEST_CODE = 2002
        private const val PULSE_INTERVAL_MS = 30_000L // 30秒精准脉冲，保活 NAT 与 MQTT 状态

        /**
         * 排期下一次精准心跳脉冲
         */
        fun scheduleNextPulse(context: Context, delayMillis: Long = PULSE_INTERVAL_MS) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, AlarmPulseReceiver::class.java).apply {
                    action = ACTION_ALARM_PULSE
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
                val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        // 降级保护：在没有精确闹钟权限时使用 setAndAllowWhileIdle，绝不崩溃且依然具备 Doze 唤醒能力
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled next alarm pulse in ${delayMillis / 1000}s")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule next pulse", e)
            }
        }

        /**
         * 取消当前排期的心跳脉冲
         */
        fun cancelPulse(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, AlarmPulseReceiver::class.java).apply {
                    action = ACTION_ALARM_PULSE
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_NO_CREATE
                }
                val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
                if (pi != null) {
                    alarmManager.cancel(pi)
                    pi.cancel()
                }
                Log.d(TAG, "Cancelled alarm pulse")
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling pulse", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM_PULSE) return
        Log.d(TAG, "Alarm pulse triggered: waking up to maintain MQTT long-connection")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MqttAssistant:PulseWakeLock"
        )
        // 获取临时唤醒锁维持 10 秒，确保 Socket I/O 完成
        wakeLock?.acquire(10_000L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (MqttClientManager.isConnected) {
                    Log.d(TAG, "Pulse active: sending MQTT PINGREQ frame to broker...")
                    MqttClientManager.pingOrKeepAlive()
                } else if (!MqttClientManager.isConnecting) {
                    Log.d(TAG, "Pulse detected disconnected socket in sleep: restoring connection...")
                    val res = MqttBackgroundService.ensureMqttConnected(context.applicationContext)
                    if (res.isSuccess) {
                        Log.i(TAG, "Pulse successfully reconnected MQTT broker in sleep!")
                    } else {
                        Log.w(TAG, "Pulse reconnect attempt failed: ${res.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error handling alarm pulse execution", e)
            } finally {
                // 无论成功与否，只要后台服务处于运行状态，自动调度下一次脉冲
                if (MqttBackgroundService.isRunning) {
                    scheduleNextPulse(context)
                }
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
