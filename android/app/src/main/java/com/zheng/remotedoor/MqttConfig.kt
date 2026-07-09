package com.zheng.remotedoor

object MqttConfig {
    const val DEFAULT_HOST = "47.122.129.16"
    const val DEFAULT_PORT = 1883

    const val TOPIC_VIDEO = "esp32/camera/video"
    const val TOPIC_VIDEO_CONTROL = "esp32/camera/control"
    const val TOPIC_VIDEO_STATUS = "esp32/camera/status"
    const val TOPIC_DOOR_CONTROL = "esp32/door/control"
    const val TOPIC_LIGHT_CONTROL = "esp32/light/control"
}
