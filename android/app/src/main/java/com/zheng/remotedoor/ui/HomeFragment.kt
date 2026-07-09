package com.zheng.remotedoor.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
        val panel = binding()?.includeVideoPanel?.root ?: return
        panel.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0) {
                updateVideoContainerSize(width)
            }
        }
    }

    private fun applyInitialVideoHeight() {
        val root = binding()?.root ?: return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                updateVideoContainerSize()
            }
        })
        root.post { updateVideoContainerSize() }
    }

    private fun resolveContainerWidth(): Int {
        val root = binding()?.root ?: return 0
        val panel = binding()?.includeVideoPanel?.root
        val horizontalPadding = root.paddingLeft + root.paddingRight
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        panel?.width?.takeIf { it > 0 }?.let { return it }

        if (isLandscape) {
            val videoSection = root.findViewById<View>(R.id.videoSection)
            videoSection?.width?.takeIf { it > 0 }?.let { return it }
        }

        root.width.takeIf { it > 0 }?.let {
            return it - horizontalPadding
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val contentWidth = screenWidth - horizontalPadding
        return if (isLandscape) {
            (contentWidth * LANDSCAPE_VIDEO_WEIGHT).toInt()
        } else {
            contentWidth
        }
    }

    private fun updateVideoContainerSize(width: Int = resolveContainerWidth()) {
        val panel = binding()?.includeVideoPanel?.root ?: return
        if (width <= 0) return
        val targetHeight = (width * VIDEO_HEIGHT.toFloat() / VIDEO_WIDTH).toInt()
        val lp = panel.layoutParams
        if (lp.height != targetHeight) {
            lp.height = targetHeight
            panel.layoutParams = lp
        }
    }

    private fun setupStreamControls() {
        binding()?.includeVideoPanel?.fabToggleStream?.setOnClickListener {
            if (mqttManager.streamEnabled.value) {
                stopVideoStream()
                return@setOnClickListener
            }
            startVideoStream()
        }
    }

    private fun startVideoStream() {
        val fab = binding()?.includeVideoPanel?.fabToggleStream ?: return
        fab.isEnabled = false
        updateStreamUi(enabled = true, waiting = true)

        viewLifecycleOwner.lifecycleScope.launch {
            val connected = mqttManager.ensureConnected()
            if (!connected) {
                fab.isEnabled = true
                updateStreamUi(enabled = false, waiting = false)
                val message = mqttManager.lastError.value ?: getString(R.string.mqtt_reconnect_failed)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val success = mqttManager.setStreamEnabled(true)
            fab.isEnabled = true
            if (!success) {
                updateStreamUi(enabled = false, waiting = false)
                val message = mqttManager.lastError.value ?: getString(R.string.mqtt_stream_failed)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                return@launch
            }

            streamStartTime = System.currentTimeMillis()
            hasReceivedFrame = false
            (activity as? MainActivity)?.startAutoCloseTimer(
                seconds = 60,
                onTick = { sec ->
                    binding()?.includeVideoPanel?.tvCountdown?.apply {
                        text = getString(R.string.auto_close_countdown, sec)
                        visibility = View.VISIBLE
                    }
                },
                onFinish = { stopVideoStream() }
            )
        }
    }

    private fun stopVideoStream() {
        viewLifecycleOwner.lifecycleScope.launch {
            mqttManager.setStreamEnabled(false)
            (activity as? MainActivity)?.cancelAutoCloseTimer()
        }
    }

    private fun setupDoorControls() {
        val d = binding()?.includeDoorControls ?: return
        val doorCommands = mapOf(
            d.btnIronUp to "iron_up",
            d.btnIronDown to "iron_down",
            d.btnIronStop to "iron_stop",
            d.btnGlassOpen to "glass_open",
            d.btnGlassClose to "glass_close",
            d.btnGlassInOut to "glass_in_out",
            d.btnGlassOnlyOut to "glass_only_out",
            d.btnLightLeft to "light_left",
            d.btnLightRight to "light_right"
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
                        binding()?.includeVideoPanel?.tvCountdown?.visibility = View.GONE
                        hasReceivedFrame = false
                        clearVideoDisplay()
                        updateStreamUi(enabled = false, waiting = false)
                        (activity as? MainActivity)?.cancelAutoCloseTimer()
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
        val fab = binding()?.includeVideoPanel?.fabToggleStream ?: return
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
        val status = binding()?.includeVideoStatus ?: return
        if (!mqttManager.streamEnabled.value) {
            status.tvFps.text = getString(R.string.fps_idle)
            status.tvFps.setTextColor(color(R.color.stream_inactive))
            return
        }
        val elapsed = (System.currentTimeMillis() - streamStartTime) / 1000.0
        val fps = if (elapsed > 0) mqttManager.frameCount.value / elapsed else 0.0
        status.tvFps.text = getString(R.string.fps_label, fps)
        val fpsColor = when {
            fps >= 2.0 -> R.color.stream_active
            fps > 0.0 -> R.color.stream_waiting
            else -> R.color.stream_waiting
        }
        status.tvFps.setTextColor(color(fpsColor))
    }

    private fun updateStreamUi(enabled: Boolean, waiting: Boolean) {
        val status = binding()?.includeVideoStatus ?: return
        when {
            !enabled -> {
                status.tvStreamStatus.text = getString(R.string.stream_off)
                status.tvStreamStatus.setTextColor(color(R.color.stream_inactive))
                status.tvFps.text = getString(R.string.fps_idle)
                status.tvFps.setTextColor(color(R.color.stream_inactive))
            }
            waiting -> {
                status.tvStreamStatus.text = getString(R.string.stream_waiting)
                status.tvStreamStatus.setTextColor(color(R.color.stream_waiting))
                updateFps()
            }
            else -> {
                status.tvStreamStatus.text = getString(R.string.stream_on)
                status.tvStreamStatus.setTextColor(color(R.color.stream_active))
                updateFps()
            }
        }
    }

    private fun showVideoFrame(bitmap: Bitmap) {
        val video = binding()?.includeVideoPanel ?: return
        video.ivVideo.setImageBitmap(bitmap)
        video.ivVideo.visibility = View.VISIBLE
        video.tvPlaceholder.visibility = View.GONE
    }

    private fun clearVideoDisplay() {
        val video = binding()?.includeVideoPanel ?: return
        video.ivVideo.setImageDrawable(null)
        video.ivVideo.visibility = View.GONE
        video.tvPlaceholder.visibility = View.VISIBLE
        video.tvPlaceholder.text = getString(R.string.video_placeholder)
    }

    private fun restoreCurrentState() {
        val enabled = mqttManager.streamEnabled.value
        updateStreamFab(enabled)
        binding()?.includeVideoPanel?.tvCountdown?.visibility = if (enabled) View.VISIBLE else View.GONE

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
