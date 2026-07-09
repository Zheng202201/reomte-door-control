package com.zheng.remotedoor

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("remote_door_prefs", Context.MODE_PRIVATE)

    var mqttHost: String
        get() = prefs.getString(KEY_HOST, MqttConfig.DEFAULT_HOST) ?: MqttConfig.DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var mqttPort: Int
        get() = prefs.getInt(KEY_PORT, MqttConfig.DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var rememberCredentials: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    companion object {
        private const val KEY_HOST = "mqtt_host"
        private const val KEY_PORT = "mqtt_port"
        private const val KEY_USERNAME = "mqtt_username"
        private const val KEY_PASSWORD = "mqtt_password"
        private const val KEY_REMEMBER = "remember_credentials"
    }
}
