package com.zheng.remotedoor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zheng.remotedoor.MainActivity
import com.zheng.remotedoor.MqttConfig
import com.zheng.remotedoor.R
import com.zheng.remotedoor.RemoteDoorApp
import com.zheng.remotedoor.databinding.FragmentSettingsBinding
import com.zheng.remotedoor.databinding.IncludeSettingsRowBinding
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
        bindAccountInfo()
        bindTopicInfo()
        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }
        observeConnectionState()
    }

    private fun bindAccountInfo() {
        bindRow(
            binding.rowUsername,
            getString(R.string.settings_label_username),
            prefs.username.ifBlank { getString(R.string.settings_value_empty) }
        )
        bindRow(
            binding.rowPassword,
            getString(R.string.settings_label_password),
            if (prefs.password.isNotBlank()) {
                getString(R.string.settings_password_masked)
            } else {
                getString(R.string.settings_value_empty)
            }
        )
        bindRow(
            binding.rowServer,
            getString(R.string.settings_label_server),
            "${prefs.mqttHost}:${prefs.mqttPort}"
        )
    }

    private fun bindTopicInfo() {
        bindRow(
            binding.rowTopicVideo,
            getString(R.string.settings_label_topic_video),
            MqttConfig.TOPIC_VIDEO
        )
        bindRow(
            binding.rowTopicDoor,
            getString(R.string.settings_label_topic_door),
            MqttConfig.TOPIC_DOOR_CONTROL
        )
        bindRow(
            binding.rowTopicLight,
            getString(R.string.settings_label_topic_light),
            MqttConfig.TOPIC_LIGHT_CONTROL
        )
    }

    private fun bindRow(
        row: IncludeSettingsRowBinding,
        label: String,
        value: String
    ) {
        row.tvLabel.text = label
        row.tvValue.text = value
    }

    private fun observeConnectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.connectionState.collect { state ->
                    updateConnectionState(state)
                }
            }
        }
    }

    private fun updateConnectionState(state: MqttManager.ConnectionState) {
        val (text, colorRes) = when (state) {
            MqttManager.ConnectionState.CONNECTED -> {
                getString(R.string.settings_state_connected) to R.color.stream_active
            }
            MqttManager.ConnectionState.CONNECTING -> {
                getString(R.string.settings_state_connecting) to R.color.stream_waiting
            }
            MqttManager.ConnectionState.DISCONNECTED -> {
                getString(R.string.settings_state_disconnected) to R.color.stream_inactive
            }
        }
        binding.tvConnectionState.text = text
        binding.tvConnectionState.setTextColor(
            ContextCompat.getColor(requireContext(), colorRes)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
