package com.zheng.remotedoor

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.zheng.remotedoor.databinding.ActivityMainBinding
import com.zheng.remotedoor.ui.DoorControlFragment
import com.zheng.remotedoor.ui.SettingsFragment
import com.zheng.remotedoor.ui.VideoMonitorFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var autoCloseTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            switchFragment(VideoMonitorFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_video -> switchFragment(VideoMonitorFragment())
                R.id.nav_door -> switchFragment(DoorControlFragment())
                R.id.nav_settings -> switchFragment(SettingsFragment())
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                RemoteDoorApp.instance.mqttManager.disconnect()
                cancelAutoCloseTimer()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        cancelAutoCloseTimer()
        super.onDestroy()
    }
}
