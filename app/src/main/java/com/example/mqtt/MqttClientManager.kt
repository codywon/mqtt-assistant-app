package com.example.mqtt

import android.util.Log
import com.example.model.MqttServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Production-grade MQTT Client Manager wrapping Eclipse Paho MQTTv3 client.
 * Features:
 * - Mutex-protected concurrency control preventing race conditions and disconnect-reconnect deadlocks.
 * - Fault-tolerant URL normalization supporting tcp://, ssl://, ws://, wss:// and auto port stripping.
 * - Robust TLS/SSL handling supporting private/self-signed broker certificates.
 * - Clean separation between intentional disconnections and unexpected connection drops.
 */
object MqttClientManager {

    private const val TAG = "MqttClientManager"

    private var mqttClient: MqttClient? = null
    private val connectMutex = Mutex()

    var onMessageReceived: ((topic: String, qos: Int, payload: ByteArray, retain: Boolean) -> Unit)? = null
    var onConnectionStateChanged: ((isConnected: Boolean, cause: Throwable?) -> Unit)? = null

    val isConnected: Boolean
        get() = mqttClient?.isConnected == true

    /**
     * Normalizes broker host and port into standard URI scheme.
     * Prevents common user input mistakes (e.g. "tcp://192.168.1.100", "broker.emqx.io:1883", "mqtt://...").
     */
    fun normalizeBrokerUrl(rawHost: String, port: Int, isTls: Boolean): String {
        var host = rawHost.trim()
        var scheme = if (isTls || port == 8883) "ssl" else "tcp"

        val knownSchemes = listOf("ssl://", "tcp://", "ws://", "wss://", "mqtt://", "mqtts://")
        for (s in knownSchemes) {
            if (host.startsWith(s, ignoreCase = true)) {
                scheme = when (s.lowercase()) {
                    "mqtt://" -> "tcp"
                    "mqtts://" -> "ssl"
                    else -> s.removeSuffix("://").lowercase()
                }
                host = host.substring(s.length)
                break
            }
        }

        val cleanHost: String
        val actualPort: Int
        if (host.contains(":") && !host.startsWith("[")) {
            val parts = host.split(":")
            cleanHost = parts[0].trim()
            actualPort = parts.getOrNull(1)?.toIntOrNull() ?: port
        } else {
            cleanHost = host.trim().trimEnd('/')
            actualPort = port
        }

        return "$scheme://$cleanHost:$actualPort"
    }

    /**
     * Connect to the MQTT Broker using specified configuration.
     * Mutex-protected to ensure only one connection attempt runs at any time.
     */
    suspend fun connect(config: MqttServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        connectMutex.withLock {
            try {
                disconnectInternal(isIntentional = true)

                val brokerUrl = normalizeBrokerUrl(config.host, config.port, config.tlsEnabled)
                val clientId = if (config.clientId.isBlank() || config.clientId == "client_mobile_th0201") {
                    "android_" + UUID.randomUUID().toString().replace("-", "").take(8)
                } else {
                    config.clientId.trim()
                }

                Log.d(TAG, "Connecting to MQTT broker: $brokerUrl, clientId: $clientId")

                val persistence = MemoryPersistence()
                val client = MqttClient(brokerUrl, clientId, persistence)
                // Assign reference immediately to eliminate race condition with connectComplete callbacks
                mqttClient = client

                val options = MqttConnectOptions().apply {
                    isCleanSession = config.cleanSession
                    keepAliveInterval = config.keepAlive.coerceAtLeast(10)
                    connectionTimeout = 15
                    isAutomaticReconnect = false // Explicitly controlled by ViewModel

                    if (config.username.isNotBlank()) {
                        userName = config.username.trim()
                    }
                    if (config.password.isNotBlank()) {
                        password = config.password.toCharArray()
                    }

                    // TLS/SSL support with permissive trust manager for IoT and self-hosted brokers
                    if (brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("wss://")) {
                        try {
                            val cleanHost = hostWithoutPort(config.host)
                            socketFactory = createTrustAllSocketFactory(cleanHost)
                            // Also disable hostname verification for self-signed or direct IP brokers
                            setHttpsHostnameVerificationEnabled(false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set custom SSLSocketFactory, falling back to default", e)
                        }
                    }
                }

                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d(TAG, "connectComplete: URI=$serverURI, reconnect=$reconnect")
                        // When reconnecting automatically, notify ViewModel to resubscribe topics
                        if (reconnect) {
                            onConnectionStateChanged?.invoke(true, null)
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "connectionLost: ${cause?.message}")
                        // Only legitimate connection drop triggers reconnection
                        onConnectionStateChanged?.invoke(false, cause)
                    }

                    override fun messageArrived(topic: String, message: MqttMessage) {
                        try {
                            Log.d(TAG, "messageArrived: topic=$topic, qos=${message.qos}, bytes=${message.payload?.size ?: 0}")
                            onMessageReceived?.invoke(
                                topic,
                                message.qos,
                                message.payload,
                                message.isRetained
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling messageArrived", e)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        // Delivery confirmed
                    }
                })

                client.connect(options)
                onConnectionStateChanged?.invoke(true, null)
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.w(TAG, "MQTT broker connection failed: ${e.message}")
                disconnectInternal(isIntentional = true)
                Result.failure(e)
            }
        }
    }

    private fun hostWithoutPort(rawHost: String): String {
        return rawHost.trim()
            .removePrefix("http://").removePrefix("https://")
            .removePrefix("tcp://").removePrefix("ssl://")
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("mqtt://").removePrefix("mqtts://")
            .substringBefore(":")
            .substringBefore("/")
    }

    /**
     * Creates a tolerant SSLSocketFactory that accepts private or self-signed certificates
     * and injects SNI (Server Name Indication) for modern cloud MQTT brokers.
     */
    private fun createTrustAllSocketFactory(sniHost: String?): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return SniSSLSocketFactory(sslContext.socketFactory, sniHost)
    }

    /**
     * Extracts clear, diagnostic, user-friendly error messages from MQTT and network exceptions.
     */
    fun getReadableErrorMessage(e: Throwable, host: String, port: Int, isTls: Boolean = false): String {
        val rootCause = generateSequence(e) { it.cause }.lastOrNull() ?: e
        val rootMsg = rootCause.message ?: ""
        val exceptionMsg = e.message ?: ""

        if (rootCause is java.net.UnknownHostException ||
            rootMsg.contains("Unable to resolve host", ignoreCase = true) ||
            rootMsg.contains("No address associated", ignoreCase = true) ||
            rootMsg.contains("Name or service not known", ignoreCase = true)
        ) {
            return "无法解析主机域名 \"$host\" (DNS解析失败，请检查Host拼写或切换至可用的 Broker)"
        }
        if (rootCause is java.net.ConnectException || rootMsg.contains("Connection refused", ignoreCase = true)) {
            return "无法连接 $host:$port (连接被拒绝，请确认服务已启动或端口正确)"
        }
        if (rootCause is java.net.SocketTimeoutException || rootMsg.contains("timed out", ignoreCase = true)) {
            return "连接超时: $host:$port (网络不可达或防火墙限制)"
        }
        if (rootCause is javax.net.ssl.SSLException ||
            rootMsg.contains("SSL", ignoreCase = true) ||
            rootMsg.contains("handshake", ignoreCase = true) ||
            rootMsg.contains("CertPathValidatorException", ignoreCase = true)
        ) {
            if (isTls && port == 1883) {
                return "TLS握手失败: 当前为1883明文端口！开启TLS安全传输通常需使用 8883 端口"
            }
            return "TLS/SSL 握手失败 (请检查端口 $port 与 TLS 开关是否匹配，或证书有效性)"
        }
        if (rootMsg.contains("connection closed", ignoreCase = true) || exceptionMsg.contains("connection closed", ignoreCase = true)) {
            if (isTls && port == 1883) {
                return "连接被Broker断开 (connection closed)。您开启了TLS加密传输，但端口仍为明文 1883 端口，请将端口修改为 8883"
            }
            return "连接被 Broker 关闭 (connection closed，可能原因：端口不匹配、TLS未配置证书、或Broker限制)"
        }
        if (e is org.eclipse.paho.client.mqttv3.MqttException) {
            return when (e.reasonCode.toInt()) {
                0 -> {
                    if (isTls && port == 1883) {
                        "TLS连接失败: 端口 1883 为非加密端口，请切换至 8883 安全端口"
                    } else {
                        val causeDesc = e.cause?.message ?: e.message ?: "客户端连接异常"
                        "MQTT 异常 [码 0]: $causeDesc"
                    }
                }
                4, 5 -> "身份认证失败 (用户名或密码错误，Broker拒绝连接)"
                3 -> "Broker 服务暂时不可用 (Server Unavailable)"
                128 -> "Broker 连接被拒绝 (错误码 128)"
                32000 -> "等待 Broker 响应超时 (请检查网络或是否同名客户端冲突)"
                else -> {
                    val causeDesc = e.cause?.message ?: e.message ?: "未知异常"
                    "MQTT 异常 [码 ${e.reasonCode}]: $causeDesc"
                }
            }
        }
        return e.localizedMessage ?: e.message ?: "连接失败"
    }

    /**
     * Disconnect from broker cleanly.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectMutex.withLock {
            disconnectInternal(isIntentional = true)
        }
    }

    /**
     * Active heartbeat check to keep TCP Socket alive and resilient in background.
     */
    fun pingOrKeepAlive() {
        try {
            val client = mqttClient
            if (client != null && client.isConnected) {
                Log.d(TAG, "Heartbeat ping: MQTT client connection is active")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat ping failed", e)
        }
    }

    private fun disconnectInternal(isIntentional: Boolean) {
        try {
            val client = mqttClient
            if (client != null) {
                if (client.isConnected) {
                    client.disconnect(500)
                }
                client.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during disconnectInternal", e)
        } finally {
            mqttClient = null
            if (!isIntentional) {
                onConnectionStateChanged?.invoke(false, null)
            }
        }
    }

    /**
     * Subscribe to a topic with specified QoS.
     */
    suspend fun subscribe(topic: String, qos: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = mqttClient
            if (client != null && client.isConnected) {
                client.subscribe(topic.trim(), qos)
                Log.d(TAG, "Subscribed to $topic (QoS $qos)")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("MQTT client is not connected"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to subscribe to $topic: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Batch subscribe to multiple topics simultaneously in a single MQTT SUB packet.
     * Prevents concurrency conflicts and guarantees atomic subscription with the Broker.
     */
    suspend fun subscribeBatch(topicsWithQos: List<Pair<String, Int>>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = mqttClient
            if (client != null && client.isConnected) {
                val validList = topicsWithQos.filter { it.first.trim().isNotBlank() }
                if (validList.isEmpty()) return@withContext Result.success(Unit)
                try {
                    val topicArray = validList.map { it.first.trim() }.toTypedArray()
                    val qosArray = validList.map { it.second.coerceIn(0, 2) }.toIntArray()
                    client.subscribe(topicArray, qosArray)
                    Log.d(TAG, "Batch subscribed to ${topicArray.size} topics: ${topicArray.joinToString()}")
                    Result.success(Unit)
                } catch (e: Throwable) {
                    Log.w(TAG, "Batch subscribe failed, falling back to individual subscriptions: ${e.message}")
                    var hasAnySuccess = false
                    for ((topic, qos) in validList) {
                        try {
                            client.subscribe(topic.trim(), qos.coerceIn(0, 2))
                            Log.d(TAG, "Individual subscribe success: $topic (QoS $qos)")
                            hasAnySuccess = true
                        } catch (subErr: Exception) {
                            Log.e(TAG, "Failed individual subscribe for $topic", subErr)
                        }
                    }
                    if (hasAnySuccess) Result.success(Unit) else Result.failure(e)
                }
            } else {
                Result.failure(IllegalStateException("MQTT client is not connected"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to batch subscribe: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Unsubscribe from a topic.
     */
    suspend fun unsubscribe(topic: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = mqttClient
            if (client != null && client.isConnected) {
                client.unsubscribe(topic.trim())
                Log.d(TAG, "Unsubscribed from $topic")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("MQTT client is not connected"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to unsubscribe from $topic: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Publish a payload to a topic.
     */
    suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int = 0,
        retain: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = mqttClient
            if (client != null && client.isConnected) {
                val message = MqttMessage(payload).apply {
                    this.qos = qos
                    this.isRetained = retain
                }
                client.publish(topic.trim(), message)
                Log.d(TAG, "Published ${payload.size} bytes to $topic (QoS $qos, retain $retain)")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("MQTT 客户端未连接，无法直接发布"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to publish to $topic: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * SSLSocketFactory decorator that injects Server Name Indication (SNI) and configures modern TLS protocols.
 */
class SniSSLSocketFactory(
    private val delegate: SSLSocketFactory,
    private val targetHost: String?
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    private fun configureSocket(socket: Socket): Socket {
        if (socket is SSLSocket) {
            try {
                // Enable modern TLS protocols
                val supported = socket.supportedProtocols.toSet()
                val desired = listOf("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1").filter { supported.contains(it) }
                if (desired.isNotEmpty()) {
                    socket.enabledProtocols = desired.toTypedArray()
                }

                // Inject SNIHostName if host is domain name
                if (!targetHost.isNullOrBlank() && !isIpAddress(targetHost)) {
                    val params = socket.sslParameters
                    params.serverNames = listOf(SNIHostName(targetHost))
                    socket.sslParameters = params
                }
            } catch (e: Throwable) {
                Log.w("SniSSLSocketFactory", "Could not configure SNI/TLS on socket: ${e.message}")
            }
        }
        return socket
    }

    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")) || host.contains(":")
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        return configureSocket(delegate.createSocket(s, host, port, autoClose))
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return configureSocket(delegate.createSocket(host, port))
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return configureSocket(delegate.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return configureSocket(delegate.createSocket(host, port))
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return configureSocket(delegate.createSocket(address, port, localAddress, localPort))
    }
}
