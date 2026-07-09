package com.zheng.remotedoor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zheng.remotedoor.MainActivity
import com.zheng.remotedoor.R
import com.zheng.remotedoor.RemoteDoorApp
import com.zheng.remotedoor.databinding.FragmentVideoMonitorBinding
import kotlinx.coroutines.launch

class VideoMonitorFragment : Fragment() {

    private var _binding: FragmentVideoMonitorBinding? = null
    private val binding get() = _binding!!
    private val mqttManager get() = RemoteDoorApp.instance.mqttManager

    private var streamStartTime = 0L
    private var lastFrameCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToggleStream.setOnClickListener {
            val enabled = !mqttManager.streamEnabled.value
            if (enabled) {
                streamStartTime = System.currentTimeMillis()
                lastFrameCount = 0
                mqttManager.setStreamEnabled(true)
                (activity as? MainActivity)?.startAutoCloseTimer(
                    seconds = 60,
                    onTick = { sec ->
                        binding.tvCountdown.text = getString(R.string.auto_close_countdown, sec)
                    },
                    onFinish = {
                        mqttManager.setStreamEnabled(false)
                    }
                )
            } else {
                mqttManager.setStreamEnabled(false)
                (activity as? MainActivity)?.cancelAutoCloseTimer()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.streamEnabled.collect { enabled ->
                    binding.btnToggleStream.text =
                        if (enabled) getString(R.string.stop_stream) else getString(R.string.start_stream)
                    binding.tvStreamStatus.text =
                        if (enabled) getString(R.string.stream_on) else getString(R.string.stream_off)
                    if (!enabled) {
                        binding.ivVideo.setImageDrawable(null)
                        binding.tvCountdown.text = getString(R.string.auto_close_countdown, 60)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.latestFrame.collect { bitmap ->
                    if (bitmap != null) {
                        binding.ivVideo.setImageBitmap(bitmap)
                        binding.tvPlaceholder.visibility = View.GONE
                    } else if (!mqttManager.streamEnabled.value) {
                        binding.tvPlaceholder.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.frameCount.collect { count ->
                    val elapsed = (System.currentTimeMillis() - streamStartTime) / 1000.0
                    val fps = if (elapsed > 0 && mqttManager.streamEnabled.value) {
                        count / elapsed
                    } else 0.0
                    binding.tvFps.text = getString(R.string.fps_label, fps)
                    lastFrameCount = count
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.cameraStatus.collect { status ->
                    if (status.isNotBlank()) {
                        binding.tvCameraStatus.text = getString(R.string.camera_status, status)
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
