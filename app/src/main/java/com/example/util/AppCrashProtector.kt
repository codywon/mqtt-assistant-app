package com.example.util

import android.content.Context
import android.util.Log

/**
 * 生产级应用全局崩溃防御与守护中心：
 * 1. 拦截主线程与后台工作线程中的未捕获异常，防止因 Android 12+ 前台服务启动限制 (ForegroundServiceStartNotAllowedException)、
 *    通知栏 PendingIntent 栈异常或系统窗口焦点争抢引发应用闪退；
 * 2. 区分致命异常与系统可忽略的瞬态异常，实施平稳优雅降级；
 * 3. 详细输出崩溃日志堆栈，便于排查定位。
 */
object AppCrashProtector {
    private const val TAG = "AppCrashProtector"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception intercepted in thread [${thread.name}]", throwable)

            val isIgnorableFgsException = isForegroundServiceRestriction(throwable)
            val isIgnorableNotificationException = isNotificationSecurityException(throwable)

            if (isIgnorableFgsException || isIgnorableNotificationException) {
                Log.w(TAG, "Successfully intercepted and suppressed non-fatal system restriction exception: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }

            // 对于其它未知未捕获致命异常，记录详细日志后交由系统默认处理器处理
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "AppCrashProtector initialized successfully")
    }

    private fun isForegroundServiceRestriction(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val name = current.javaClass.name
            val msg = current.message ?: ""
            if (name.contains("ForegroundServiceStartNotAllowedException") ||
                name.contains("ForegroundServiceDidNotStartInTimeException") ||
                msg.contains("ForegroundService") ||
                msg.contains("startForegroundService() not allowed") ||
                msg.contains("Starting FGS with type dataSync caller")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isNotificationSecurityException(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val msg = current.message ?: ""
            if (msg.contains("Bad notification posted") ||
                msg.contains("calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
