package com.zheng.remotedoor

import android.app.Application
import com.zheng.remotedoor.mqtt.MqttManager

class RemoteDoorApp : Application() {

    lateinit var mqttManager: MqttManager
        private set

    lateinit var prefsManager: PrefsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        mqttManager = MqttManager()
        prefsManager = PrefsManager(this)
    }

    companion object {
        lateinit var instance: RemoteDoorApp
            private set
    }
}
