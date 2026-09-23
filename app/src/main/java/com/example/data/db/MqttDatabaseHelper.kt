package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.AiChatMessage
import com.example.model.AiChatSession
import com.example.model.BrokerProfile
import com.example.model.MqttLogPacket
import com.example.model.ProtocolKnowledge
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

    private val insertedCountSinceTrim = java.util.concurrent.atomic.AtomicInteger(0)

    companion object {
        const val DATABASE_NAME = "mqtt_assistant.db"
        const val DATABASE_VERSION = 3

        // Tables
        private const val TABLE_BROKERS = "tbl_broker_profiles"
        private const val TABLE_SUBS = "tbl_subscriptions"
        private const val TABLE_PRESETS = "tbl_publish_presets"
        private const val TABLE_PACKETS = "tbl_mqtt_packets"
        private const val TABLE_SETTINGS = "tbl_app_settings"
        private const val TABLE_PROTOCOLS = "tbl_protocol_knowledge"
        private const val TABLE_AI_SESSIONS = "tbl_ai_sessions"
        private const val TABLE_AI_MESSAGES = "tbl_ai_messages"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
        } catch (_: Exception) {}
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
        // Index for fast sorting by arrival time and fast group-by/filtering
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_created ON $TABLE_PACKETS(created_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_topic ON $TABLE_PACKETS(topic)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_category ON $TABLE_PACKETS(category)")

        // 5. App settings key-value table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )

        // 6. Protocol Knowledge Clarification table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PROTOCOLS (
                id TEXT PRIMARY KEY,
                name TEXT,
                topicFilter TEXT,
                description TEXT,
                sampleHex TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        // 7. AI Chat Sessions table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AI_SESSIONS (
                id TEXT PRIMARY KEY,
                title TEXT,
                created_at INTEGER,
                updated_at INTEGER
            )
            """.trimIndent()
        )

        // 8. AI Chat Messages history table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AI_MESSAGES (
                id TEXT PRIMARY KEY,
                sessionId TEXT DEFAULT 'default',
                role TEXT,
                content TEXT,
                reasoningContent TEXT,
                toolCallsJson TEXT,
                toolCallId TEXT,
                timestamp INTEGER,
                isError INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_AI_SESSIONS (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    created_at INTEGER,
                    updated_at INTEGER
                )
                """.trimIndent()
            )
            try {
                db.execSQL("ALTER TABLE $TABLE_AI_MESSAGES ADD COLUMN sessionId TEXT DEFAULT 'default'")
            } catch (_: Exception) {}
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_topic ON $TABLE_PACKETS(topic)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packet_category ON $TABLE_PACKETS(category)")
        } catch (_: Exception) {}
        onCreate(db)
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

        // 水位线节流机制：只有累积插入超过 200 条时才触发低频检查，彻底消除每 16ms 执行昂贵全表子查询的 I/O 浪费
        val currentPending = insertedCountSinceTrim.addAndGet(packets.size)
        if (maxBuffer > 0 && currentPending >= 200) {
            insertedCountSinceTrim.set(0)
            try {
                val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_PACKETS", null)
                val totalCount = if (countCursor.moveToFirst()) countCursor.getLong(0) else 0L
                countCursor.close()

                if (totalCount > maxBuffer) {
                    db.execSQL(
                        """
                        DELETE FROM $TABLE_PACKETS WHERE id NOT IN (
                            SELECT id FROM $TABLE_PACKETS ORDER BY created_at DESC LIMIT $maxBuffer
                        )
                        """.trimIndent()
                    )
                }
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

    /**
     * 游标流式逐行读取导出（消除 OOM 风险）
     * 边扫游标边回调，单次在内存中仅常驻 1 个对象，内存占用恒定 < 1MB
     */
    fun exportPacketsStream(
        limit: Int = 10000,
        maxCreatedAt: Long = Long.MAX_VALUE,
        consumer: (packet: MqttLogPacket, createdAt: Long) -> Unit
    ): Int {
        val db = readableDatabase
        val selection = if (maxCreatedAt < Long.MAX_VALUE) "created_at <= ?" else null
        val selectionArgs = if (maxCreatedAt < Long.MAX_VALUE) arrayOf(maxCreatedAt.toString()) else null
        val cursor = db.query(
            TABLE_PACKETS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "created_at ASC",
            limit.toString()
        )
        var count = 0
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
                consumer(packet, createdAt)
                count++
            }
        }
        return count
    }

    fun deletePacketsBefore(maxCreatedAt: Long): Int {
        val db = writableDatabase
        val deleted = db.delete(TABLE_PACKETS, "created_at <= ?", arrayOf(maxCreatedAt.toString()))
        vacuumDatabase()
        return deleted
    }

    fun clearAllPackets() {
        val db = writableDatabase
        db.delete(TABLE_PACKETS, null, null)
        vacuumDatabase()
    }

    fun vacuumDatabase() {
        try {
            val db = writableDatabase
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

    // =========================================================================
    // Safe Read-Only SQL Query Execution Engine for SI Agent
    // =========================================================================

    fun executeReadOnlyQuery(sql: String): List<Map<String, String>> {
        val trimmed = sql.trim().trimEnd(';').trim()
        val upper = trimmed.uppercase()

        if (!upper.startsWith("SELECT")) {
            throw IllegalArgumentException("安全拦截：AI Agent 只允许执行 SELECT 检索操作")
        }
        if (trimmed.contains(";")) {
            throw IllegalArgumentException("安全拦截：禁止注入多条 SQL 语句")
        }

        val forbiddenKeywords = listOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "TRUNCATE", "ATTACH", "DETACH", "PRAGMA", "REPLACE", "EXEC"
        )
        for (kw in forbiddenKeywords) {
            if (Regex("\\b$kw\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
                throw IllegalArgumentException("安全拦截：检测到敏感或变更指令 $kw")
            }
        }

        // 内存熔断防护：若未写 LIMIT，自动加上 LIMIT 100；最大限制 200 条
        val finalSql = if (!Regex("\\bLIMIT\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            "$trimmed LIMIT 100"
        } else {
            trimmed
        }

        val db = readableDatabase
        val result = mutableListOf<Map<String, String>>()
        val cursor = db.rawQuery(finalSql, null)
        cursor.use { c ->
            val colNames = c.columnNames
            var count = 0
            while (c.moveToNext() && count < 200) {
                val row = mutableMapOf<String, String>()
                for (i in colNames.indices) {
                    val colName = colNames[i]
                    val value = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> "NULL"
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
                        Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> "[BLOB ${c.getBlob(i).size}B]"
                        else -> c.getString(i) ?: ""
                    }
                    row[colName] = value
                }
                result.add(row)
                count++
            }
        }
        return result
    }

    // =========================================================================
    // Protocol Knowledge Clarification CRUD
    // =========================================================================

    fun saveProtocolKnowledge(item: ProtocolKnowledge) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", item.id)
            put("name", item.name)
            put("topicFilter", item.topicFilter)
            put("description", item.description)
            put("sampleHex", item.sampleHex)
            put("created_at", item.createdAt)
        }
        db.insertWithOnConflict(TABLE_PROTOCOLS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadAllProtocolKnowledge(): List<ProtocolKnowledge> {
        val db = readableDatabase
        val list = mutableListOf<ProtocolKnowledge>()
        val cursor = db.query(
            TABLE_PROTOCOLS,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    ProtocolKnowledge(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        topicFilter = c.getString(c.getColumnIndexOrThrow("topicFilter")),
                        description = c.getString(c.getColumnIndexOrThrow("description")),
                        sampleHex = c.getString(c.getColumnIndexOrThrow("sampleHex")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun deleteProtocolKnowledge(id: String) {
        writableDatabase.delete(TABLE_PROTOCOLS, "id = ?", arrayOf(id))
    }

    // =========================================================================
    // AI Chat Sessions & Messages History CRUD
    // =========================================================================

    fun saveAiSession(session: AiChatSession) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", session.id)
            put("title", session.title)
            put("created_at", session.createdAt)
            put("updated_at", session.updatedAt)
        }
        db.insertWithOnConflict(TABLE_AI_SESSIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadAllAiSessions(): List<AiChatSession> {
        val db = readableDatabase
        val list = mutableListOf<AiChatSession>()
        val cursor = db.query(
            TABLE_AI_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    AiChatSession(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
        }
        return list
    }

    fun updateAiSessionTitle(sessionId: String, title: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title)
            put("updated_at", System.currentTimeMillis())
        }
        db.update(TABLE_AI_SESSIONS, cv, "id = ?", arrayOf(sessionId))
    }

    fun deleteAiSession(sessionId: String) {
        val db = writableDatabase
        db.delete(TABLE_AI_SESSIONS, "id = ?", arrayOf(sessionId))
        db.delete(TABLE_AI_MESSAGES, "sessionId = ?", arrayOf(sessionId))
    }

    fun saveAiMessage(msg: AiChatMessage) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", msg.id)
            put("sessionId", msg.sessionId)
            put("role", msg.role)
            put("content", msg.content)
            put("reasoningContent", msg.reasoningContent)
            put("toolCallsJson", msg.toolCallsJson)
            put("toolCallId", msg.toolCallId)
            put("timestamp", msg.timestamp)
            put("isError", if (msg.isError) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_AI_MESSAGES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        // 更新所属会话的活跃时间戳
        val updateCv = ContentValues().apply {
            put("updated_at", msg.timestamp)
        }
        db.update(TABLE_AI_SESSIONS, updateCv, "id = ?", arrayOf(msg.sessionId))
    }

    fun loadAiMessages(sessionId: String = "default", limit: Int = 100): List<AiChatMessage> {
        val db = readableDatabase
        val list = mutableListOf<AiChatMessage>()
        val selection = if (sessionId == "default") "sessionId = ? OR sessionId IS NULL" else "sessionId = ?"
        val cursor = db.query(
            TABLE_AI_MESSAGES,
            null,
            selection,
            arrayOf(sessionId),
            null,
            null,
            "timestamp ASC",
            limit.toString()
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val sIdCol = c.getColumnIndex("sessionId")
                val sId = if (sIdCol >= 0 && !c.isNull(sIdCol)) c.getString(sIdCol) else "default"
                list.add(
                    AiChatMessage(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = sId,
                        role = c.getString(c.getColumnIndexOrThrow("role")),
                        content = c.getString(c.getColumnIndexOrThrow("content")),
                        reasoningContent = c.getString(c.getColumnIndexOrThrow("reasoningContent")),
                        toolCallsJson = c.getString(c.getColumnIndexOrThrow("toolCallsJson")),
                        toolCallId = c.getString(c.getColumnIndexOrThrow("toolCallId")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        isError = c.getInt(c.getColumnIndexOrThrow("isError")) == 1
                    )
                )
            }
        }
        return list
    }

    fun deleteAiMessage(id: String) {
        val db = writableDatabase
        db.delete(TABLE_AI_MESSAGES, "id = ?", arrayOf(id))
    }

    fun clearAiMessages(sessionId: String? = null) {
        val db = writableDatabase
        if (sessionId != null) {
            db.delete(TABLE_AI_MESSAGES, "sessionId = ?", arrayOf(sessionId))
        } else {
            db.delete(TABLE_AI_MESSAGES, null, null)
        }
    }
}
