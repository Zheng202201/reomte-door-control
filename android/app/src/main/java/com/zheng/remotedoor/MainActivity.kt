package com.zheng.remotedoor

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zheng.remotedoor.databinding.ActivityMainBinding
import com.zheng.remotedoor.ui.HomeFragment
import com.zheng.remotedoor.ui.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var autoCloseTimer: CountDownTimer? = null
    private var menuExpanded = false
    private var currentTabId: Int = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentTabId = savedInstanceState?.getInt(KEY_TAB, TAB_HOME) ?: TAB_HOME
        setupSideMenu()

        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), TAB_HOME)
        } else {
            when (currentTabId) {
                TAB_SETTINGS -> switchFragment(SettingsFragment(), TAB_SETTINGS)
                else -> switchFragment(HomeFragment(), TAB_HOME)
            }
        }
    }

    private fun setupSideMenu() {
        binding.fabMenuToggle.setOnClickListener { toggleSideMenu() }
        binding.fabNavHome.setOnClickListener {
            collapseSideMenu()
            switchFragment(HomeFragment(), TAB_HOME)
        }
        binding.fabNavSettings.setOnClickListener {
            collapseSideMenu()
            switchFragment(SettingsFragment(), TAB_SETTINGS)
        }
        updateNavHighlight()
    }

    private fun toggleSideMenu() {
        if (menuExpanded) {
            collapseSideMenu()
        } else {
            expandSideMenu()
        }
    }

    private fun expandSideMenu() {
        menuExpanded = true
        binding.sideMenuItems.visibility = View.VISIBLE
        binding.sideMenuItems.alpha = 0f
        binding.sideMenuItems.translationX = 80f
        binding.sideMenuItems.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(200)
            .start()
        binding.fabMenuToggle.setImageResource(R.drawable.ic_nav_arrow_right)
    }

    private fun collapseSideMenu() {
        menuExpanded = false
        binding.sideMenuItems.animate()
            .alpha(0f)
            .translationX(80f)
            .setDuration(180)
            .withEndAction {
                binding.sideMenuItems.visibility = View.GONE
            }
            .start()
        binding.fabMenuToggle.setImageResource(R.drawable.ic_nav_arrow_left)
    }

    private fun updateNavHighlight() {
        binding.fabNavHome.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (currentTabId == TAB_HOME) R.color.primary else R.color.nav_bg
        )
        binding.fabNavSettings.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (currentTabId == TAB_SETTINGS) R.color.primary else R.color.nav_bg
        )
        binding.fabNavHome.imageTintList = ContextCompat.getColorStateList(
            this,
            if (currentTabId == TAB_HOME) android.R.color.white else R.color.primary
        )
        binding.fabNavSettings.imageTintList = ContextCompat.getColorStateList(
            this,
            if (currentTabId == TAB_SETTINGS) android.R.color.white else R.color.primary
        )
    }

    fun logout() {
        RemoteDoorApp.instance.prefsManager.markManualLogout()
        RemoteDoorApp.instance.mqttManager.disconnect()
        cancelAutoCloseTimer()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun switchFragment(fragment: Fragment, tabId: Int) {
        currentTabId = tabId
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        updateNavHighlight()
    }

    fun startAutoCloseTimer(seconds: Int, onTick: (Int) -> Unit, onFinish: () -> Unit) {
        cancelAutoCloseTimer()
        autoCloseTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                onTick((millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                onFinish()
            }
        }.start()
    }

    fun cancelAutoCloseTimer() {
        autoCloseTimer?.cancel()
        autoCloseTimer = null
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            RemoteDoorApp.instance.mqttManager.ensureConnected()
        }
    }

    override fun onDestroy() {
        cancelAutoCloseTimer()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, currentTabId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val KEY_TAB = "current_tab"
        private const val TAB_HOME = 0
        private const val TAB_SETTINGS = 1
    }
}
