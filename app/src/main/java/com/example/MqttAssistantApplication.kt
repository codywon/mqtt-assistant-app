package com.example

import android.app.Application
import android.util.Log
import com.example.util.AppCrashProtector

class MqttAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCrashProtector.init(this)
        Log.i("MqttAssistantApp", "MqttAssistantApplication initialized with CrashProtector")
    }
}
