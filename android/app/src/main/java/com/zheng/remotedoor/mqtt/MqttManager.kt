package com.zheng.remotedoor.mqtt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hivemq.client.mqtt.MqttClient
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
import java.util.UUID
import java.util.concurrent.TimeUnit

class MqttManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: Mqtt3AsyncClient? = null

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
        scope.launch {
            try {
                disconnectInternal()
                _connectionState.value = ConnectionState.CONNECTING
                _lastError.value = null

                val clientId = "android_${UUID.randomUUID().toString().take(8)}"
                val mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(host)
                    .serverPort(port)
                    .automaticReconnect()
                    .initialDelay(2, TimeUnit.SECONDS)
                    .maxDelay(30, TimeUnit.SECONDS)
                    .applyAutomaticReconnect()
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
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _lastError.value = "MQTT 连接失败: ${connAck.returnCode}"
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _lastError.value = e.message ?: "连接异常"
            }
        }
    }

    private fun subscribeTopics(mqttClient: Mqtt3AsyncClient) {
        mqttClient.subscribeWith()
            .topicFilter(MqttConfig.TOPIC_VIDEO)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                val payload = publish.payloadAsBytes
                if (payload.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(payload, 0, payload.size)
                    if (bitmap != null) {
                        _latestFrame.value = bitmap
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

    fun setStreamEnabled(enabled: Boolean) {
        scope.launch {
            publish(MqttConfig.TOPIC_VIDEO_CONTROL, if (enabled) "on" else "off")
            _streamEnabled.value = enabled
            if (!enabled) {
                _latestFrame.value = null
                _frameCount.value = 0
            }
        }
    }

    fun sendDoorCommand(command: String) {
        scope.launch { publish(MqttConfig.TOPIC_DOOR_CONTROL, command) }
    }

    fun sendLightCommand(command: String) {
        scope.launch { publish(MqttConfig.TOPIC_LIGHT_CONTROL, command) }
    }

    private suspend fun publish(topic: String, message: String) {
        val mqttClient = client ?: run {
            _lastError.value = "MQTT 未连接"
            return
        }
        try {
            mqttClient.publishWith()
                .topic(topic)
                .payload(message.toByteArray())
                .qos(MqttQos.AT_MOST_ONCE)
                .send()
                .get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            _lastError.value = "发送失败: ${e.message}"
        }
    }

    fun disconnect() {
        scope.launch { disconnectInternal() }
    }

    private suspend fun disconnectInternal() {
        try {
            if (_streamEnabled.value) {
                publish(MqttConfig.TOPIC_VIDEO_CONTROL, "off")
            }
            client?.disconnect()?.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            client = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _streamEnabled.value = false
            _latestFrame.value = null
            _frameCount.value = 0
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
