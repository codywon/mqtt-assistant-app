package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.BrokerProfile
import com.example.model.MqttLogPacket
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem

/**
 * Production-grade SQLite database helper for MQTT Assistant.
 * Database name: mqtt_assistant.db
 * Provides high-performance, robust, persistent storage for all broker nodes,
 * topic subscriptions, publish presets, system configurations, and incoming/outgoing MQTT packets.
 */
class MqttDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "mqtt_assistant.db"
        const val DATABASE_VERSION = 1

        // Tables
        private const val TABLE_BROKERS = "tbl_broker_profiles"
        private const val TABLE_SUBS = "tbl_subscriptions"
        private const val TABLE_PRESETS = "tbl_publish_presets"
        private const val TABLE_PACKETS = "tbl_mqtt_packets"
        private const val TABLE_SETTINGS = "tbl_app_settings"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Broker profiles table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BROKERS (
                id TEXT PRIMARY KEY,
                name TEXT,
                host TEXT,
                port INTEGER,
                clientId TEXT,
                username TEXT,
                password TEXT,
                protocol TEXT,
                cleanSession INTEGER,
                tlsEnabled INTEGER,
                keepAlive INTEGER
            )
            """.trimIndent()
        )

        // 2. Subscriptions table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SUBS (
                id TEXT PRIMARY KEY,
                topic TEXT,
                qos INTEGER,
                msgCount INTEGER,
                lastTimeText TEXT,
                isEnabled INTEGER,
                dotColorHex INTEGER,
                name TEXT,
                retainHandling INTEGER
            )
            """.trimIndent()
        )

        // 3. Publish presets table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PRESETS (
                id TEXT PRIMARY KEY,
                name TEXT,
                topic TEXT,
                qos INTEGER,
                retain INTEGER,
                payload TEXT
            )
            """.trimIndent()
        )

        // 4. MQTT Packets history table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PACKETS (
                id TEXT PRIMARY KEY,
                topic TEXT,
                qos INTEGER,
                packetSeq TEXT,
                timestamp TEXT,
                payload TEXT,
                devInfo TEXT,
                sizeText TEXT,
                category TEXT,
                dotColorHex INTEGER,
                created_at INTEGER
            )
            """.trimIndent()
        )
        // Index for fast sorting by arrival time
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_created ON $TABLE_PACKETS(created_at DESC)")

        // 5. App settings key-value table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe migration if version upgrades in the future
    }

    // =========================================================================
    // Broker Profiles CRUD
    // =========================================================================

    fun saveBrokerProfiles(profiles: List<BrokerProfile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_BROKERS, null, null)
            for (p in profiles) {
                val cv = ContentValues().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("host", p.host)
                    put("port", p.port)
                    put("clientId", p.clientId)
                    put("username", p.username)
                    put("password", p.password)
                    put("protocol", p.protocol)
                    put("cleanSession", if (p.cleanSession) 1 else 0)
                    put("tlsEnabled", if (p.tlsEnabled) 1 else 0)
                    put("keepAlive", p.keepAlive)
                }
                db.insertWithOnConflict(TABLE_BROKERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadBrokerProfiles(): List<BrokerProfile> {
        val list = mutableListOf<BrokerProfile>()
        val db = readableDatabase
        val cursor: Cursor = db.query(TABLE_BROKERS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    BrokerProfile(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        host = c.getString(c.getColumnIndexOrThrow("host")),
                        port = c.getInt(c.getColumnIndexOrThrow("port")),
                        clientId = c.getString(c.getColumnIndexOrThrow("clientId")),
                        username = c.getString(c.getColumnIndexOrThrow("username")),
                        password = c.getString(c.getColumnIndexOrThrow("password")),
                        protocol = c.getString(c.getColumnIndexOrThrow("protocol")),
                        cleanSession = c.getInt(c.getColumnIndexOrThrow("cleanSession")) == 1,
                        tlsEnabled = c.getInt(c.getColumnIndexOrThrow("tlsEnabled")) == 1,
                        keepAlive = c.getInt(c.getColumnIndexOrThrow("keepAlive"))
                    )
                )
            }
        }
        return list
    }

    // =========================================================================
    // Subscriptions CRUD
    // =========================================================================

    fun saveSubscriptions(subs: List<SubscriptionItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_SUBS, null, null)
            for (s in subs) {
                val cv = ContentValues().apply {
                    put("id", s.id)
                    put("topic", s.topic)
                    put("qos", s.qos)
                    put("msgCount", s.msgCount)
                    put("lastTimeText", s.lastTimeText)
                    put("isEnabled", if (s.isEnabled) 1 else 0)
                    put("dotColorHex", s.dotColorHex)
                    put("name", s.name)
                    put("retainHandling", s.retainHandling)
                }
                db.insertWithOnConflict(TABLE_SUBS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadSubscriptions(): List<SubscriptionItem> {
        val list = mutableListOf<SubscriptionItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SUBS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    SubscriptionItem(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        qos = c.getInt(c.getColumnIndexOrThrow("qos")),
                        msgCount = c.getInt(c.getColumnIndexOrThrow("msgCount")),
                        lastTimeText = c.getString(c.getColumnIndexOrThrow("lastTimeText")),
                        isEnabled = c.getInt(c.getColumnIndexOrThrow("isEnabled")) == 1,
                        dotColorHex = c.getLong(c.getColumnIndexOrThrow("dotColorHex")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        retainHandling = c.getInt(c.getColumnIndexOrThrow("retainHandling"))
                    )
                )
            }
        }
        return list
    }

    // =========================================================================
    // Publish Presets CRUD
    // =========================================================================

    fun savePublishPresets(presets: List<PublishPreset>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_PRESETS, null, null)
            for (p in presets) {
                val cv = ContentValues().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("topic", p.topic)
                    put("qos", p.qos)
                    put("retain", if (p.retain) 1 else 0)
                    put("payload", p.payload)
                }
                db.insertWithOnConflict(TABLE_PRESETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadPublishPresets(): List<PublishPreset> {
        val list = mutableListOf<PublishPreset>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PRESETS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    PublishPreset(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        qos = c.getInt(c.getColumnIndexOrThrow("qos")),
                        retain = c.getInt(c.getColumnIndexOrThrow("retain")) == 1,
                        payload = c.getString(c.getColumnIndexOrThrow("payload"))
                    )
                )
            }
        }
        return list
    }

    // =========================================================================
    // MQTT Packets History CRUD (Persistent Logs)
    // =========================================================================

    fun insertPacket(packet: MqttLogPacket, maxBuffer: Int = 10000) {
        insertPackets(listOf(packet), maxBuffer)
    }

    fun insertPackets(packets: List<MqttLogPacket>, maxBuffer: Int = 10000) {
        if (packets.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (packet in packets) {
                val cv = ContentValues().apply {
                    put("id", packet.id)
                    put("topic", packet.topic)
                    put("qos", packet.qos)
                    put("packetSeq", packet.packetSeq)
                    put("timestamp", packet.timestamp)
                    put("payload", packet.payload)
                    put("devInfo", packet.devInfo)
                    put("sizeText", packet.sizeText)
                    put("category", packet.category)
                    put("dotColorHex", packet.dotColorHex)
                    put("created_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_PACKETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // 仅在数据累积量较大时异步或按需修剪历史记录，避免单条逐次全表扫描
        if (maxBuffer > 0) {
            try {
                db.execSQL(
                    """
                    DELETE FROM $TABLE_PACKETS WHERE id NOT IN (
                        SELECT id FROM $TABLE_PACKETS ORDER BY created_at DESC LIMIT $maxBuffer
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
    }

    fun loadRecentPackets(limit: Int = 300): List<MqttLogPacket> {
        val list = mutableListOf<MqttLogPacket>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PACKETS,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
            limit.toString()
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    MqttLogPacket(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        qos = c.getInt(c.getColumnIndexOrThrow("qos")),
                        packetSeq = c.getString(c.getColumnIndexOrThrow("packetSeq")),
                        timestamp = c.getString(c.getColumnIndexOrThrow("timestamp")),
                        payload = c.getString(c.getColumnIndexOrThrow("payload")),
                        devInfo = c.getString(c.getColumnIndexOrThrow("devInfo")),
                        sizeText = c.getString(c.getColumnIndexOrThrow("sizeText")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        dotColorHex = c.getLong(c.getColumnIndexOrThrow("dotColorHex"))
                    )
                )
            }
        }
        return list
    }

    /**
     * 加载全部报文用于导出 Excel（按创建时间正序排列）
     * 返回 (MqttLogPacket, createdAtMillis)
     */
    fun loadAllPacketsForExport(limit: Int = 10000): List<Pair<MqttLogPacket, Long>> {
        val list = mutableListOf<Pair<MqttLogPacket, Long>>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PACKETS,
            null,
            null,
            null,
            null,
            null,
            "created_at ASC",
            limit.toString()
        )
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val topicCol = c.getColumnIndexOrThrow("topic")
            val qosCol = c.getColumnIndexOrThrow("qos")
            val seqCol = c.getColumnIndexOrThrow("packetSeq")
            val tsCol = c.getColumnIndexOrThrow("timestamp")
            val payloadCol = c.getColumnIndexOrThrow("payload")
            val devInfoCol = c.getColumnIndexOrThrow("devInfo")
            val sizeCol = c.getColumnIndexOrThrow("sizeText")
            val catCol = c.getColumnIndexOrThrow("category")
            val colorCol = c.getColumnIndexOrThrow("dotColorHex")
            val createdCol = c.getColumnIndexOrThrow("created_at")

            while (c.moveToNext()) {
                val packet = MqttLogPacket(
                    id = c.getString(idCol),
                    topic = c.getString(topicCol),
                    qos = c.getInt(qosCol),
                    packetSeq = c.getString(seqCol),
                    timestamp = c.getString(tsCol),
                    payload = c.getString(payloadCol),
                    devInfo = c.getString(devInfoCol),
                    sizeText = c.getString(sizeCol),
                    category = c.getString(catCol),
                    dotColorHex = c.getLong(colorCol)
                )
                val createdAt = c.getLong(createdCol)
                list.add(Pair(packet, createdAt))
            }
        }
        return list
    }

    fun clearAllPackets() {
        val db = writableDatabase
        db.delete(TABLE_PACKETS, null, null)
        try {
            db.execSQL("VACUUM")
        } catch (_: Exception) {}
    }

    fun getPacketCount(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT count(*) FROM $TABLE_PACKETS", null)
        cursor.use { c ->
            if (c.moveToFirst()) {
                return c.getLong(0)
            }
        }
        return 0L
    }

    fun getDatabaseSizeBytes(context: Context): Long {
        var totalSize = 0L
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile != null && dbFile.exists()) {
                totalSize += dbFile.length()
                val walFile = java.io.File(dbFile.path + "-wal")
                if (walFile.exists()) totalSize += walFile.length()
                val shmFile = java.io.File(dbFile.path + "-shm")
                if (shmFile.exists()) totalSize += shmFile.length()
            }
        } catch (_: Exception) {}
        return totalSize
    }

    // =========================================================================
    // Key-Value App Settings
    // =========================================================================

    fun saveSetting(key: String, value: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadSetting(key: String, defaultValue: String = ""): String {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return c.getString(0) ?: defaultValue
            }
        }
        return defaultValue
    }

    fun deleteSetting(key: String) {
        writableDatabase.delete(TABLE_SETTINGS, "key = ?", arrayOf(key))
    }
}
