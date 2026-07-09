package com.zheng.remotedoor.mqtt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.zheng.remotedoor.MqttConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class MqttManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: Mqtt3AsyncClient? = null

    private var savedHost: String? = null
    private var savedPort: Int = MqttConfig.DEFAULT_PORT
    private var savedUsername: String? = null
    private var savedPassword: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _streamEnabled = MutableStateFlow(false)
    val streamEnabled: StateFlow<Boolean> = _streamEnabled.asStateFlow()

    private val _cameraStatus = MutableStateFlow("")
    val cameraStatus: StateFlow<String> = _cameraStatus.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private val _frameCount = MutableStateFlow(0)
    val frameCount: StateFlow<Int> = _frameCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun connect(host: String, port: Int, username: String, password: String) {
        savedHost = host
        savedPort = port
        savedUsername = username
        savedPassword = password
        scope.launch { connectInternal(host, port, username, password) }
    }

    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (isClientConnected()) return@withContext true
        val host = savedHost ?: run {
            _lastError.value = "无保存的连接信息"
            return@withContext false
        }
        val username = savedUsername ?: return@withContext false
        val password = savedPassword ?: return@withContext false
        connectInternal(host, savedPort, username, password)
    }

    private suspend fun connectInternal(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Boolean {
        return try {
            if (isClientConnected()) return true

            disconnectInternal(publishOff = false)
            _connectionState.value = ConnectionState.CONNECTING
            _lastError.value = null

            val clientId = "android_${UUID.randomUUID().toString().take(8)}"
            val mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port)
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener {
                    scope.launch {
                        client?.let { subscribeTopics(it) }
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                }
                .addDisconnectedListener {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    resetStreamState()
                }
                .buildAsync()

            val connAck: Mqtt3ConnAck = mqttClient.connectWith()
                .simpleAuth()
                .username(username)
                .password(password.toByteArray())
                .applySimpleAuth()
                .keepAlive(30)
                .send()
                .get(15, TimeUnit.SECONDS)

            if (connAck.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                client = mqttClient
                subscribeTopics(mqttClient)
                _connectionState.value = ConnectionState.CONNECTED
                true
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
                _lastError.value = "MQTT 连接失败: ${connAck.returnCode}"
                false
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _lastError.value = e.message ?: "连接异常"
            false
        }
    }

    private fun isClientConnected(): Boolean {
        val mqttClient = client ?: return false
        return mqttClient.state == MqttClientState.CONNECTED
    }

    private fun subscribeTopics(mqttClient: Mqtt3AsyncClient) {
        mqttClient.subscribeWith()
            .topicFilter(MqttConfig.TOPIC_VIDEO)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                if (!_streamEnabled.value) return@callback
                val payload = publish.payloadAsBytes
                if (payload.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(payload, 0, payload.size)
                    if (bitmap != null) {
                        val flipped = flipHorizontally(bitmap)
                        if (flipped !== bitmap) {
                            bitmap.recycle()
                        }
                        _latestFrame.value?.recycle()
                        _latestFrame.value = flipped
                        _frameCount.value = _frameCount.value + 1
                    }
                }
            }
            .send()

        mqttClient.subscribeWith()
            .topicFilter(MqttConfig.TOPIC_VIDEO_STATUS)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                _cameraStatus.value = publish.payloadAsBytes.toString(Charsets.UTF_8)
            }
            .send()
    }

    suspend fun setStreamEnabled(enabled: Boolean): Boolean {
        if (!enabled) {
            publish(MqttConfig.TOPIC_VIDEO_CONTROL, "off")
            resetStreamState()
            return true
        }
        if (!ensureConnected()) {
            _lastError.value = "MQTT 未连接，无法开启视频"
            return false
        }
        val success = publish(MqttConfig.TOPIC_VIDEO_CONTROL, "on")
        if (success) {
            _streamEnabled.value = true
        } else {
            _lastError.value = _lastError.value ?: "开启视频失败"
        }
        return success
    }

    fun sendDoorCommand(command: String) {
        scope.launch {
            if (ensureConnected()) {
                publish(MqttConfig.TOPIC_DOOR_CONTROL, command)
            }
        }
    }

    fun sendLightCommand(command: String) {
        scope.launch {
            if (ensureConnected()) {
                publish(MqttConfig.TOPIC_LIGHT_CONTROL, command)
            }
        }
    }

    private suspend fun publish(topic: String, message: String): Boolean {
        if (!ensureConnected()) {
            _lastError.value = "MQTT 未连接"
            return false
        }
        val mqttClient = client ?: run {
            _lastError.value = "MQTT 未连接"
            return false
        }
        return try {
            mqttClient.publishWith()
                .topic(topic)
                .payload(message.toByteArray())
                .qos(MqttQos.AT_MOST_ONCE)
                .send()
                .get(10, TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _lastError.value = "发送失败: ${e.message}"
            false
        }
    }

    fun disconnect() {
        scope.launch { disconnectInternal() }
    }

    private suspend fun disconnectInternal(publishOff: Boolean = true) {
        try {
            if (publishOff && _streamEnabled.value) {
                client?.takeIf { it.state == MqttClientState.CONNECTED }?.let { mqttClient ->
                    try {
                        mqttClient.publishWith()
                            .topic(MqttConfig.TOPIC_VIDEO_CONTROL)
                            .payload("off".toByteArray())
                            .qos(MqttQos.AT_MOST_ONCE)
                            .send()
                            .get(5, TimeUnit.SECONDS)
                    } catch (_: Exception) {
                    }
                }
            }
            client?.disconnect()?.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            client = null
            _connectionState.value = ConnectionState.DISCONNECTED
            resetStreamState()
        }
    }

    private fun resetStreamState() {
        _streamEnabled.value = false
        _latestFrame.value?.recycle()
        _latestFrame.value = null
        _frameCount.value = 0
    }

    private fun flipHorizontally(source: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
