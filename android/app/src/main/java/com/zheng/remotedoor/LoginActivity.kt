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

    private var loginAttemptStarted = false
    private var isAutoLoggingIn = false

    private var pendingHost = ""
    private var pendingPort = 0
    private var pendingUsername = ""
    private var pendingPassword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        populateLoginForm()
        binding.btnLogin.setOnClickListener { attemptManualLogin() }
        observeConnectionState()

        if (prefs.hasSavedCredentials()) {
            startAutoLogin()
        } else {
            showLoginForm()
        }
    }

    private fun populateLoginForm() {
        binding.etHost.setText(prefs.mqttHost)
        binding.etPort.setText(prefs.mqttPort.toString())
        binding.cbRemember.isChecked = prefs.rememberCredentials
        binding.etUsername.setText(prefs.username)
        binding.etPassword.setText(prefs.password)
    }

    private fun startAutoLogin() {
        isAutoLoggingIn = true
        showAutoLoginUi()
        connect(
            host = prefs.mqttHost,
            port = prefs.mqttPort,
            username = prefs.username,
            password = prefs.password
        )
    }

    private fun attemptManualLogin() {
        isAutoLoggingIn = false
        val host = binding.etHost.text.toString().trim()
        val port = binding.etPort.text.toString().trim().toIntOrNull()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (host.isEmpty() || port == null || username.isEmpty() || password.isEmpty()) {
            binding.tvError.text = "请填写完整的连接信息"
            binding.tvError.visibility = View.VISIBLE
            return
        }

        binding.tvError.visibility = View.GONE
        showManualLoginLoading()
        connect(host, port, username, password)
    }

    private fun connect(host: String, port: Int, username: String, password: String) {
        pendingHost = host
        pendingPort = port
        pendingUsername = username
        pendingPassword = password
        loginAttemptStarted = true
        mqttManager.connect(host, port, username, password)
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.connectionState.collect { state ->
                    when (state) {
                        MqttManager.ConnectionState.CONNECTING -> onConnecting()
                        MqttManager.ConnectionState.CONNECTED -> onConnected()
                        MqttManager.ConnectionState.DISCONNECTED -> onDisconnected()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqttManager.lastError.collect { error ->
                    if (!error.isNullOrBlank() &&
                        loginAttemptStarted &&
                        mqttManager.connectionState.value == MqttManager.ConnectionState.DISCONNECTED
                    ) {
                        showLoginError(error)
                    }
                }
            }
        }
    }

    private fun onConnecting() {
        if (!loginAttemptStarted) return
        binding.btnLogin.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        if (isAutoLoggingIn) {
            binding.tvAutoLoginStatus.text = getString(R.string.auto_login_in_progress)
        }
    }

    private fun onConnected() {
        prefs.saveLoginCredentials(
            pendingHost,
            pendingPort,
            pendingUsername,
            pendingPassword
        )
        binding.progressBar.visibility = View.GONE
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun onDisconnected() {
        if (!loginAttemptStarted) return

        binding.btnLogin.isEnabled = true
        binding.progressBar.visibility = View.GONE

        if (isAutoLoggingIn) {
            isAutoLoggingIn = false
            showLoginForm()
            showLoginError(getString(R.string.auto_login_failed))
        }
    }

    private fun showLoginError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showAutoLoginUi() {
        binding.loginTitle.visibility = View.VISIBLE
        binding.autoLoginPanel.visibility = View.VISIBLE
        binding.loginForm.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun showLoginForm() {
        binding.loginTitle.visibility = View.VISIBLE
        binding.autoLoginPanel.visibility = View.GONE
        binding.loginForm.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        populateLoginForm()
    }

    private fun showManualLoginLoading() {
        binding.autoLoginPanel.visibility = View.GONE
        binding.loginForm.visibility = View.VISIBLE
    }
}
