package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BrokerProfile
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistent storage for MQTT Assistant using SharedPreferences and JSON.
 * Ensures all Broker configurations, Subscriptions, and Publish Presets persist
 * across app restarts, reboots, and configuration changes.
 */
class MqttStorageRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mqtt_assistant_storage", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BROKER_PROFILES = "key_broker_profiles"
        private const val KEY_ACTIVE_BROKER_ID = "key_active_broker_id"
        private const val KEY_SUBSCRIPTIONS = "key_subscriptions"
        private const val KEY_PUBLISH_PRESETS = "key_publish_presets"
        private const val KEY_INCLUDE_TOPIC_FILTERS = "key_include_topic_filters"
        private const val KEY_EXCLUDE_TOPIC_FILTERS = "key_exclude_topic_filters"
    }

    // ==========================================
    // 1. Broker Profiles Persistence
    // ==========================================

    fun loadBrokerProfiles(): List<BrokerProfile> {
        val jsonString = prefs.getString(KEY_BROKER_PROFILES, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<BrokerProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BrokerProfile(
                            id = obj.optString("id", "b$i"),
                            name = obj.optString("name", "Broker $i"),
                            host = obj.optString("host", "broker.emqx.io"),
                            port = obj.optInt("port", 1883),
                            clientId = obj.optString("clientId", "client_mobile_th0201"),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            protocol = obj.optString("protocol", "MQTT 3.1.1"),
                            cleanSession = obj.optBoolean("cleanSession", true),
                            tlsEnabled = obj.optBoolean("tlsEnabled", false),
                            keepAlive = obj.optInt("keepAlive", 60)
                        )
                    )
                }
                    return list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return defaultBrokerProfiles()
    }

    fun saveBrokerProfiles(profiles: List<BrokerProfile>) {
        try {
            val array = JSONArray()
            for (p in profiles) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("host", p.host)
                    put("port", p.port)
                    put("clientId", p.clientId)
                    put("username", p.username)
                    put("password", p.password)
                    put("protocol", p.protocol)
                    put("cleanSession", p.cleanSession)
                    put("tlsEnabled", p.tlsEnabled)
                    put("keepAlive", p.keepAlive)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_BROKER_PROFILES, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadActiveBrokerId(): String {
        return prefs.getString(KEY_ACTIVE_BROKER_ID, null) ?: ""
    }

    fun saveActiveBrokerId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_BROKER_ID, id).apply()
    }

    private fun defaultBrokerProfiles(): List<BrokerProfile> = emptyList()

    // ==========================================
    // 2. Subscriptions Persistence
    // ==========================================

    fun loadSubscriptions(): List<SubscriptionItem> {
        if (prefs.contains(KEY_SUBSCRIPTIONS)) {
            val jsonString = prefs.getString(KEY_SUBSCRIPTIONS, null)
            if (!jsonString.isNullOrBlank()) {
                try {
                    val array = JSONArray(jsonString)
                    val list = mutableListOf<SubscriptionItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            SubscriptionItem(
                                id = obj.optString("id", "s$i"),
                                topic = obj.optString("topic", ""),
                                qos = obj.optInt("qos", 0),
                                msgCount = obj.optInt("msgCount", 0),
                                lastTimeText = obj.optString("lastTimeText", "等待数据"),
                                isEnabled = obj.optBoolean("isEnabled", true),
                                dotColorHex = obj.optLong("dotColorHex", 0xFF10B981),
                                name = obj.optString("name", ""),
                                retainHandling = obj.optInt("retainHandling", 0)
                            )
                        )
                    }
                    return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }
        return defaultSubscriptions()
    }

    fun saveSubscriptions(subscriptions: List<SubscriptionItem>) {
        try {
            val array = JSONArray()
            for (sub in subscriptions) {
                val obj = JSONObject().apply {
                    put("id", sub.id)
                    put("topic", sub.topic)
                    put("qos", sub.qos)
                    put("msgCount", sub.msgCount)
                    put("lastTimeText", sub.lastTimeText)
                    put("isEnabled", sub.isEnabled)
                    put("dotColorHex", sub.dotColorHex)
                    put("name", sub.name)
                    put("retainHandling", sub.retainHandling)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun defaultSubscriptions(): List<SubscriptionItem> = emptyList()

    // ==========================================
    // 3. Publish Presets Persistence
    // ==========================================

    fun loadPublishPresets(): List<PublishPreset> {
        if (prefs.contains(KEY_PUBLISH_PRESETS)) {
            val jsonString = prefs.getString(KEY_PUBLISH_PRESETS, null)
            if (!jsonString.isNullOrBlank()) {
                try {
                    val array = JSONArray(jsonString)
                    val list = mutableListOf<PublishPreset>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            PublishPreset(
                                id = obj.optString("id", "p$i"),
                                name = obj.optString("name", "配置 $i"),
                                topic = obj.optString("topic", ""),
                                qos = obj.optInt("qos", 0),
                                retain = obj.optBoolean("retain", false),
                                payload = obj.optString("payload", "{}")
                            )
                        )
                    }
                    return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }
        return defaultPublishPresets()
    }

    fun savePublishPresets(presets: List<PublishPreset>) {
        try {
            val array = JSONArray()
            for (p in presets) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("topic", p.topic)
                    put("qos", p.qos)
                    put("retain", p.retain)
                    put("payload", p.payload)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_PUBLISH_PRESETS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun defaultPublishPresets(): List<PublishPreset> = emptyList()

    // ==========================================
    // 4. Topic Filter Rules (Include / Exclude)
    // ==========================================

    fun loadIncludeTopicFilters(): List<String> {
        val jsonString = prefs.getString(KEY_INCLUDE_TOPIC_FILTERS, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val s = array.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    fun saveIncludeTopicFilters(filters: List<String>) {
        try {
            val array = JSONArray()
            filters.filter { it.isNotBlank() }.forEach { array.put(it.trim()) }
            prefs.edit().putString(KEY_INCLUDE_TOPIC_FILTERS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadExcludeTopicFilters(): List<String> {
        val jsonString = prefs.getString(KEY_EXCLUDE_TOPIC_FILTERS, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val s = array.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    fun saveExcludeTopicFilters(filters: List<String>) {
        try {
            val array = JSONArray()
            filters.filter { it.isNotBlank() }.forEach { array.put(it.trim()) }
            prefs.edit().putString(KEY_EXCLUDE_TOPIC_FILTERS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // 5. KeepAlive & Background Settings Persistence
    // ==========================================

    fun loadBackgroundKeepAlive(): Boolean {
        return prefs.getBoolean("key_background_keepalive_enabled", true)
    }

    fun saveBackgroundKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean("key_background_keepalive_enabled", enabled).apply()
    }

    fun loadWakeLock(): Boolean {
        return prefs.getBoolean("key_wake_lock_enabled", true)
    }

    fun saveWakeLock(enabled: Boolean) {
        prefs.edit().putBoolean("key_wake_lock_enabled", enabled).apply()
    }
}

