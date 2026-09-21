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
                if (list.isNotEmpty()) {
                    // Auto-heal legacy unresolvable dummy domains to public EMQX broker
                    val healed = list.map { profile ->
                        if (profile.host == "csms.thestatech.cn") {
                            profile.copy(
                                name = "EMQX 公共节点 (推荐)",
                                host = "broker.emqx.io",
                                port = 1883,
                                username = "",
                                password = ""
                            )
                        } else profile
                    }
                    return healed
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
        return prefs.getString(KEY_ACTIVE_BROKER_ID, null) ?: "b1"
    }

    fun saveActiveBrokerId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_BROKER_ID, id).apply()
    }

    private fun defaultBrokerProfiles(): List<BrokerProfile> = listOf(
        BrokerProfile(
            id = "b1",
            name = "EMQX 开放节点 (推荐)",
            host = "broker.emqx.io",
            port = 1883,
            clientId = "android_client_" + (1000..9999).random(),
            username = "",
            password = "",
            protocol = "MQTT 3.1.1",
            cleanSession = true,
            tlsEnabled = false,
            keepAlive = 60
        ),
        BrokerProfile(
            id = "b2",
            name = "HiveMQ 开放测试节点",
            host = "broker.hivemq.com",
            port = 1883,
            clientId = "hivemq_mobile_test",
            username = "",
            password = "",
            protocol = "MQTT 3.1.1",
            cleanSession = true,
            tlsEnabled = false,
            keepAlive = 60
        ),
        BrokerProfile(
            id = "b3",
            name = "自定义本地/私有节点",
            host = "192.168.1.100",
            port = 1883,
            clientId = "client_custom_node",
            username = "",
            password = "",
            protocol = "MQTT 3.1.1",
            cleanSession = true,
            tlsEnabled = false,
            keepAlive = 60
        )
    )

    // ==========================================
    // 2. Subscriptions Persistence
    // ==========================================

    fun loadSubscriptions(): List<SubscriptionItem> {
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
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    private fun defaultSubscriptions(): List<SubscriptionItem> = listOf(
        SubscriptionItem("s0", "college/#", 0, 0, "等待数据", true, 0xFF10B981, name = "高校设备流"),
        SubscriptionItem("s1", "Collect/#", 0, 0, "等待数据", true, 0xFF006C52, name = "Collect采集"),
        SubscriptionItem("s2", "Set_OTA_url/#", 0, 0, "等待数据", true, 0xFF3980F4, name = "OTA升级"),
        SubscriptionItem("s3", "Voice_Reminder/#", 0, 0, "等待数据", true, 0xFF8B5CF6, name = "语音指令"),
        SubscriptionItem("s4", "scale_config/#", 0, 0, "等待数据", true, 0xFFF59E0B, name = "称重设备"),
        SubscriptionItem("s5", "Sensor_Gateway/Telemetry", 0, 0, "等待数据", false, 0xFF747878, name = "传感器")
    )

    // ==========================================
    // 3. Publish Presets Persistence
    // ==========================================

    fun loadPublishPresets(): List<PublishPreset> {
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
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    private fun defaultPublishPresets(): List<PublishPreset> = listOf(
        PublishPreset(
            id = "p0",
            name = "断路器参数控制",
            topic = "college/breaker/control/THFC012CCCBDDC",
            qos = 0,
            retain = false,
            payload = "{\n  \"schema_version\": 1,\n  \"action\": \"set_config\",\n  \"config_reset\": false\n}"
        ),
        PublishPreset(
            id = "p1",
            name = "OTA固件升级指令",
            topic = "Voice_Reminder/Set_OTA_url/TH02D0CF130657D4",
            qos = 0,
            retain = false,
            payload = "{\n  \"ota_url\": \"https://ota.codywon.top:9443/BSG.bin\",\n  \"size\": 1048576,\n  \"md5\": \"a1b2c3d4e5f60718\"\n}"
        ),
        PublishPreset(
            id = "p2",
            name = "秤重参数配置",
            topic = "scale_config/865269078444663",
            qos = 0,
            retain = false,
            payload = "{\n  \"scale\": \"74.8\",\n  \"device_id\": \"865269078444663\",\n  \"unit\": \"kg\"\n}"
        ),
        PublishPreset(
            id = "p3",
            name = "语音播报指令",
            topic = "Voice_Reminder/Voice_Msg_cmd/TH02D0CF130657D4",
            qos = 0,
            retain = false,
            payload = "{\n  \"id\": \"afb7fb2f48484da5be7bdc6c2b7609df\",\n  \"type\": \"play\",\n  \"vol\": 80,\n  \"msg\": \"设备正常工作中\"\n}"
        ),
        PublishPreset(
            id = "p4",
            name = "音量调节",
            topic = "Voice_Reminder/Device_Vol/TH02D0CF130657D4",
            qos = 0,
            retain = false,
            payload = "{\n  \"vol\": 90\n}"
        ),
        PublishPreset(
            id = "p5",
            name = "设备心跳探测",
            topic = "college/device/ping",
            qos = 0,
            retain = false,
            payload = "{\n  \"ping\": true,\n  \"timestamp\": 1726830000\n}"
        )
    )

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
}
