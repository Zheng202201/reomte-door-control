package com.zheng.remotedoor.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zheng.remotedoor.MainActivity
import com.zheng.remotedoor.R
import com.zheng.remotedoor.RemoteDoorApp
import com.zheng.remotedoor.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    companion object {
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val LANDSCAPE_VIDEO_WEIGHT = 0.58f
    }

    private var _binding: FragmentHomeBinding? = null
    private val mqttManager get() = RemoteDoorApp.instance.mqttManager

    private var streamStartTime = 0L
    private var hasReceivedFrame = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVideoAspectRatio()
        setupStreamControls()
        setupDoorControls()
        observeMqttState()
        restoreCurrentState()
        applyInitialVideoHeight()
    }

    private fun setupVideoAspectRatio() {
        val container = binding()?.videoContainer ?: return
        container.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                updateVideoContainerSize(right - left)
            }
        }
    }

    private fun applyInitialVideoHeight() {
        binding()?.root?.post { updateVideoContainerSize() }
    }

    private fun resolveContainerWidth(): Int {
        val container = binding()?.videoContainer ?: return 0
        if (container.width > 0) return container.width

        val root = binding()?.root ?: return 0
        val horizontalPadding = root.paddingLeft + root.paddingRight
        if (root.width > 0) {
            return root.width - horizontalPadding
        }

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val contentWidth = screenWidth - horizontalPadding
        return if (isLandscape) {
            (contentWidth * LANDSCAPE_VIDEO_WEIGHT).toInt()
        } else {
            contentWidth
        }
    }

    private fun updateVideoContainerSize(width: Int = resolveContainerWidth()) {
        val container = binding()?.videoContainer ?: return
        if (width <= 0) return
        val targetHeight = (width * VIDEO_HEIGHT.toFloat() / VIDEO_WIDTH).toInt()
        val lp = container.layoutParams
        if (lp.height != targetHeight) {
            lp.height = targetHeight
            container.layoutParams = lp
        }
    }

    private fun setupStreamControls() {
        binding()?.fabToggleStream?.setOnClickListener {
            val enabled = !mqttManager.streamEnabled.value
            if (enabled) {
                streamStartTime = System.currentTimeMillis()
                hasReceivedFrame = false
                mqttManager.setStreamEnabled(true)
                updateStreamUi(enabled = true, waiting = true)
                (activity as? MainActivity)?.startAutoCloseTimer(
                    seconds = 60,
                    onTick = { sec ->
                        binding()?.tvCountdown?.apply {
                            text = getString(R.string.auto_close_countdown, sec)
                            visibility = View.VISIBLE
                        }
                    },
                    onFinish = { mqttManager.setStreamEnabled(false) }
                )
            } else {
                mqttManager.setStreamEnabled(false)
                (activity as? MainActivity)?.cancelAutoCloseTimer()
            }
        }
    }

    private fun setupDoorControls() {
        val b = binding() ?: return
        val doorCommands = mapOf(
            b.btnIronUp to "iron_up",
            b.btnIronDown to "iron_down",
            b.btnIronStop to "iron_stop",
            b.btnGlassOpen to "glass_open",
            b.btnGlassClose to "glass_close",
            b.btnGlassInOut to "glass_in_out",
            b.btnGlassOnlyOut to "glass_only_out",
            b.btnLightLeft to "light_left",
            b.btnLightRight to "light_right"
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

    private fun observeMqttState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.streamEnabled.collect { enabled ->
                    updateStreamFab(enabled)
                    if (!enabled) {
                        binding()?.tvCountdown?.visibility = View.GONE
                        hasReceivedFrame = false
                        clearVideoDisplay()
                        updateStreamUi(enabled = false, waiting = false)
                    } else if (!hasReceivedFrame) {
                        updateStreamUi(enabled = true, waiting = true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.latestFrame.collect { bitmap ->
                    if (!mqttManager.streamEnabled.value) {
                        clearVideoDisplay()
                        return@collect
                    }
                    if (bitmap != null) {
                        hasReceivedFrame = true
                        showVideoFrame(bitmap)
                        updateStreamUi(enabled = true, waiting = false)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.frameCount.collect { updateFps() }
            }
        }
    }

    private fun updateStreamFab(enabled: Boolean) {
        val fab = binding()?.fabToggleStream ?: return
        if (enabled) {
            fab.setImageResource(R.drawable.ic_stream_stop)
            fab.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.fab_stream_on)
            fab.contentDescription = getString(R.string.stop_stream)
        } else {
            fab.setImageResource(R.drawable.ic_stream_play)
            fab.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.fab_stream_off)
            fab.contentDescription = getString(R.string.start_stream)
        }
    }

    private fun updateFps() {
        val b = binding() ?: return
        if (!mqttManager.streamEnabled.value) {
            b.tvFps.text = getString(R.string.fps_idle)
            b.tvFps.setTextColor(color(R.color.stream_inactive))
            return
        }
        val elapsed = (System.currentTimeMillis() - streamStartTime) / 1000.0
        val fps = if (elapsed > 0) mqttManager.frameCount.value / elapsed else 0.0
        b.tvFps.text = getString(R.string.fps_label, fps)
        val fpsColor = when {
            fps >= 2.0 -> R.color.stream_active
            fps > 0.0 -> R.color.stream_waiting
            else -> R.color.stream_waiting
        }
        b.tvFps.setTextColor(color(fpsColor))
    }

    private fun updateStreamUi(enabled: Boolean, waiting: Boolean) {
        val b = binding() ?: return
        when {
            !enabled -> {
                b.tvStreamStatus.text = getString(R.string.stream_off)
                b.tvStreamStatus.setTextColor(color(R.color.stream_inactive))
                b.tvFps.text = getString(R.string.fps_idle)
                b.tvFps.setTextColor(color(R.color.stream_inactive))
            }
            waiting -> {
                b.tvStreamStatus.text = getString(R.string.stream_waiting)
                b.tvStreamStatus.setTextColor(color(R.color.stream_waiting))
                updateFps()
            }
            else -> {
                b.tvStreamStatus.text = getString(R.string.stream_on)
                b.tvStreamStatus.setTextColor(color(R.color.stream_active))
                updateFps()
            }
        }
    }

    private fun showVideoFrame(bitmap: Bitmap) {
        val b = binding() ?: return
        b.ivVideo.setImageBitmap(bitmap)
        b.ivVideo.visibility = View.VISIBLE
        b.tvPlaceholder.visibility = View.GONE
    }

    private fun clearVideoDisplay() {
        val b = binding() ?: return
        b.ivVideo.setImageDrawable(null)
        b.ivVideo.visibility = View.GONE
        b.tvPlaceholder.visibility = View.VISIBLE
        b.tvPlaceholder.text = getString(R.string.video_placeholder)
    }

    private fun restoreCurrentState() {
        val enabled = mqttManager.streamEnabled.value
        updateStreamFab(enabled)
        binding()?.tvCountdown?.visibility = if (enabled) View.VISIBLE else View.GONE

        if (enabled) {
            streamStartTime = System.currentTimeMillis()
            val frame = mqttManager.latestFrame.value
            if (frame != null) {
                hasReceivedFrame = true
                showVideoFrame(frame)
                updateStreamUi(enabled = true, waiting = false)
            } else {
                updateStreamUi(enabled = true, waiting = true)
            }
        } else {
            clearVideoDisplay()
            updateStreamUi(enabled = false, waiting = false)
        }
    }

    private fun binding(): FragmentHomeBinding? = _binding

    private fun color(resId: Int): Int =
        ContextCompat.getColor(requireContext(), resId)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
