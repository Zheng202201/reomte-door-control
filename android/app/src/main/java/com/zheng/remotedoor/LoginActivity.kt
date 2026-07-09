package com.zheng.remotedoor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zheng.remotedoor.databinding.ActivityLoginBinding
import com.zheng.remotedoor.mqtt.MqttManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val mqttManager get() = RemoteDoorApp.instance.mqttManager
    private val prefs get() = RemoteDoorApp.instance.prefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etHost.setText(prefs.mqttHost)
        binding.etPort.setText(prefs.mqttPort.toString())
        binding.cbRemember.isChecked = prefs.rememberCredentials

        if (prefs.rememberCredentials) {
            binding.etUsername.setText(prefs.username)
            binding.etPassword.setText(prefs.password)
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.connectionState.collect { state ->
                    when (state) {
                        MqttManager.ConnectionState.CONNECTING -> {
                            binding.btnLogin.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvError.visibility = View.GONE
                        }
                        MqttManager.ConnectionState.CONNECTED -> {
                            binding.progressBar.visibility = View.GONE
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                        MqttManager.ConnectionState.DISCONNECTED -> {
                            binding.btnLogin.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.lastError.collect { error ->
                    if (!error.isNullOrBlank() &&
                        mqttManager.connectionState.value == MqttManager.ConnectionState.DISCONNECTED
                    ) {
                        binding.tvError.text = error
                        binding.tvError.visibility = View.VISIBLE
                        Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun attemptLogin() {
        val host = binding.etHost.text.toString().trim()
        val port = binding.etPort.text.toString().trim().toIntOrNull()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (host.isEmpty() || port == null || username.isEmpty() || password.isEmpty()) {
            binding.tvError.text = "请填写完整的连接信息"
            binding.tvError.visibility = View.VISIBLE
            return
        }

        prefs.mqttHost = host
        prefs.mqttPort = port
        prefs.rememberCredentials = binding.cbRemember.isChecked
        if (binding.cbRemember.isChecked) {
            prefs.username = username
            prefs.password = password
        } else {
            prefs.clearCredentials()
        }

        binding.tvError.visibility = View.GONE
        mqttManager.connect(host, port, username, password)
    }
}
