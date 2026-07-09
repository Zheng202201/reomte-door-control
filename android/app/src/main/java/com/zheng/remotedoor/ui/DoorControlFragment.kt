package com.zheng.remotedoor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.zheng.remotedoor.RemoteDoorApp
import com.zheng.remotedoor.databinding.FragmentDoorControlBinding

class DoorControlFragment : Fragment() {

    private var _binding: FragmentDoorControlBinding? = null
    private val binding get() = _binding!!
    private val mqttManager get() = RemoteDoorApp.instance.mqttManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoorControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val doorCommands = mapOf(
            binding.btnIronUp to "iron_up",
            binding.btnIronDown to "iron_down",
            binding.btnIronStop to "iron_stop",
            binding.btnGlassOpen to "glass_open",
            binding.btnGlassClose to "glass_close",
            binding.btnGlassInOut to "glass_in_out",
            binding.btnGlassOnlyOut to "glass_only_out",
            binding.btnLightLeft to "light_left",
            binding.btnLightRight to "light_right"
        )

        doorCommands.forEach { (button, command) ->
            button.setOnClickListener {
                when (command) {
                    "light_left", "light_right" -> mqttManager.sendLightCommand(command)
                    else -> mqttManager.sendDoorCommand(command)
                }
                Toast.makeText(requireContext(), "已发送: $command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
