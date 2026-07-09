package com.zheng.remotedoor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zheng.remotedoor.MqttConfig
import com.zheng.remotedoor.RemoteDoorApp
import com.zheng.remotedoor.databinding.FragmentSettingsBinding
import com.zheng.remotedoor.mqtt.MqttManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val prefs get() = RemoteDoorApp.instance.prefsManager
    private val mqttManager get() = RemoteDoorApp.instance.mqttManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvServerInfo.text = buildString {
            append("MQTT: ${prefs.mqttHost}:${prefs.mqttPort}\n")
            append("视频: ${MqttConfig.TOPIC_VIDEO}\n")
            append("门控: ${MqttConfig.TOPIC_DOOR_CONTROL}\n")
            append("灯控: ${MqttConfig.TOPIC_LIGHT_CONTROL}")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.connectionState.collect { state ->
                    binding.tvConnectionState.text = when (state) {
                        MqttManager.ConnectionState.CONNECTED -> "已连接"
                        MqttManager.ConnectionState.CONNECTING -> "连接中..."
                        MqttManager.ConnectionState.DISCONNECTED -> "未连接"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
