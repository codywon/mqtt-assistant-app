package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.MqttDatabaseHelper
import com.example.model.BrokerProfile
import com.example.model.MqttLogPacket
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * High-performance Storage Repository for MQTT Assistant.
 * Fully backed by native SQLite database (mqtt_assistant.db).
 * Ensures all Broker profiles, Subscriptions, Publish presets, Topic filters,
 * and Live MQTT message packets persist permanently across app restarts and reboots.
 */
class MqttStorageRepository(context: Context) {

    private val dbHelper = MqttDatabaseHelper(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mqtt_assistant_storage", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE_BROKER_ID = "key_active_broker_id"
        private const val KEY_INCLUDE_TOPIC_FILTERS = "key_include_topic_filters"
        private const val KEY_EXCLUDE_TOPIC_FILTERS = "key_exclude_topic_filters"
        private const val KEY_BG_KEEPALIVE = "key_background_keepalive_enabled"
        private const val KEY_WAKE_LOCK = "key_wake_lock_enabled"
        private const val KEY_LEGACY_MIGRATED = "key_legacy_migrated_to_sqlite"
    }

    init {
        migrateLegacyPreferencesIfNeeded()
    }

    /**
     * Seamlessly migrates existing user data from SharedPreferences to SQLite if present.
     */
    private fun migrateLegacyPreferencesIfNeeded() {
        if (!prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            try {
                // 1. Migrate broker profiles
                val brokerJson = prefs.getString("key_broker_profiles", null)
                if (!brokerJson.isNullOrBlank()) {
                    val array = JSONArray(brokerJson)
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
                    if (list.isNotEmpty()) {
                        dbHelper.saveBrokerProfiles(list)
                    }
                }

                // 2. Migrate subscriptions
                val subJson = prefs.getString("key_subscriptions", null)
                if (!subJson.isNullOrBlank()) {
                    val array = JSONArray(subJson)
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
                    if (list.isNotEmpty()) {
                        dbHelper.saveSubscriptions(list)
                    }
                }

                // 3. Migrate presets
                val presetJson = prefs.getString("key_publish_presets", null)
                if (!presetJson.isNullOrBlank()) {
                    val array = JSONArray(presetJson)
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
                    if (list.isNotEmpty()) {
                        dbHelper.savePublishPresets(list)
                    }
                }

                prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // 1. Broker Profiles (SQLite Backed)
    // ==========================================

    fun loadBrokerProfiles(): List<BrokerProfile> {
        val list = dbHelper.loadBrokerProfiles()
        return list.ifEmpty { defaultBrokerProfiles() }
    }

    fun saveBrokerProfiles(profiles: List<BrokerProfile>) {
        dbHelper.saveBrokerProfiles(profiles)
    }

    fun loadActiveBrokerId(): String {
        return dbHelper.loadSetting(KEY_ACTIVE_BROKER_ID, "")
    }

    fun saveActiveBrokerId(id: String) {
        dbHelper.saveSetting(KEY_ACTIVE_BROKER_ID, id)
    }

    private fun defaultBrokerProfiles(): List<BrokerProfile> = emptyList()

    // ==========================================
    // 2. Subscriptions (SQLite Backed)
    // ==========================================

    fun loadSubscriptions(): List<SubscriptionItem> {
        val list = dbHelper.loadSubscriptions()
        return list.ifEmpty { defaultSubscriptions() }
    }

    fun saveSubscriptions(subscriptions: List<SubscriptionItem>) {
        dbHelper.saveSubscriptions(subscriptions)
    }

    private fun defaultSubscriptions(): List<SubscriptionItem> = emptyList()

    // ==========================================
    // 3. Publish Presets (SQLite Backed)
    // ==========================================

    fun loadPublishPresets(): List<PublishPreset> {
        val list = dbHelper.loadPublishPresets()
        return list.ifEmpty { defaultPublishPresets() }
    }

    fun savePublishPresets(presets: List<PublishPreset>) {
        dbHelper.savePublishPresets(presets)
    }

    private fun defaultPublishPresets(): List<PublishPreset> = emptyList()

    // ==========================================
    // 4. Topic Filter Rules (SQLite Backed)
    // ==========================================

    fun loadIncludeTopicFilters(): List<String> {
        val raw = dbHelper.loadSetting(KEY_INCLUDE_TOPIC_FILTERS, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(";;;").filter { it.isNotBlank() }
    }

    fun saveIncludeTopicFilters(filters: List<String>) {
        val serialized = filters.filter { it.isNotBlank() }.joinToString(";;;")
        dbHelper.saveSetting(KEY_INCLUDE_TOPIC_FILTERS, serialized)
    }

    fun loadExcludeTopicFilters(): List<String> {
        val raw = dbHelper.loadSetting(KEY_EXCLUDE_TOPIC_FILTERS, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(";;;").filter { it.isNotBlank() }
    }

    fun saveExcludeTopicFilters(filters: List<String>) {
        val serialized = filters.filter { it.isNotBlank() }.joinToString(";;;")
        dbHelper.saveSetting(KEY_EXCLUDE_TOPIC_FILTERS, serialized)
    }

    // ==========================================
    // 5. KeepAlive & Background Settings
    // ==========================================

    fun loadBackgroundKeepAlive(): Boolean {
        val v = dbHelper.loadSetting(KEY_BG_KEEPALIVE, "true")
        return v.toBooleanStrictOrNull() ?: true
    }

    fun saveBackgroundKeepAlive(enabled: Boolean) {
        dbHelper.saveSetting(KEY_BG_KEEPALIVE, enabled.toString())
    }

    fun loadWakeLock(): Boolean {
        val v = dbHelper.loadSetting(KEY_WAKE_LOCK, "true")
        return v.toBooleanStrictOrNull() ?: true
    }

    fun saveWakeLock(enabled: Boolean) {
        dbHelper.saveSetting(KEY_WAKE_LOCK, enabled.toString())
    }

    // ==========================================
    // 6. MQTT Live & Historical Packets (SQLite)
    // ==========================================

    fun savePacket(packet: MqttLogPacket) {
        dbHelper.insertPacket(packet)
    }

    fun loadRecentPackets(limit: Int = 300): List<MqttLogPacket> {
        return dbHelper.loadRecentPackets(limit)
    }

    fun clearAllPackets() {
        dbHelper.clearAllPackets()
    }
}
