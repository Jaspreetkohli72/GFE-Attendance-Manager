package com.jaspreet.gfeattendancemanager.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.databinding.ActivityMainBinding
import com.jaspreet.gfeattendancemanager.ui.adapter.StaffButtonAdapter
import com.jaspreet.gfeattendancemanager.ui.dialog.AdminPinDialog
import com.jaspreet.gfeattendancemanager.ui.dialog.StaffActionDialog
import com.jaspreet.gfeattendancemanager.ui.dialog.SyncLogDialog
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: KioskViewModel by viewModels()
    private lateinit var staffAdapter: StaffButtonAdapter
    private lateinit var soundManager: SoundManager

    private var clockJob: Job? = null
    private var bannerHideJob: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KioskLogger.sys("WORKSHOP KIOSK APPLICATION STARTED SUCCESSFULLY")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Ignore back button press for kiosk lockdown
            }
        })

        soundManager = SoundManager()

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        startClock()
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(batteryReceiver, filter)
            updateBatteryStatus()
        } catch (e: Exception) {
            KioskLogger.error("Failed to register battery receiver", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    private fun updateBatteryStatus() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = registerReceiver(null, filter) ?: return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                            plugged > 0

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL ||
                             isPlugged

            val text = if (isCharging) "⚡ $pct%" else "$pct%"
            binding.tvBattery.text = text

            val iconRes = when {
                isCharging -> R.drawable.ic_battery_charging
                pct >= 90 -> R.drawable.ic_battery_full
                pct >= 45 -> R.drawable.ic_battery_medium
                pct >= 15 -> R.drawable.ic_battery_low
                else -> R.drawable.ic_battery_critical
            }
            binding.ivBattery.setImageResource(iconRes)
        } catch (e: Exception) {
            KioskLogger.error("Failed to update battery status", e)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupRecyclerView() {
        staffAdapter = StaffButtonAdapter { staffMember ->
            KioskLogger.user("[USER ACTION] Worker tile tapped: ${staffMember.name} (ID: ${staffMember.id})")
            val dialog = StaffActionDialog.newInstance(staffMember)
            dialog.show(supportFragmentManager, "StaffActionDialog")
        }
        val screenWidthDp = resources.configuration.screenWidthDp
        val spanCount = when {
            screenWidthDp >= 1200 -> 5
            screenWidthDp >= 840 -> 4
            else -> 3
        }
        binding.rvStaffGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvStaffGrid.adapter = staffAdapter
    }

    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            KioskLogger.user("[USER ACTION] Pull-to-refresh triggered")
            viewModel.fetchStaff()
        }

        binding.ibRefresh.setOnClickListener {
            KioskLogger.user("[USER ACTION] Refresh button clicked")
            binding.swipeRefresh.isRefreshing = true
            viewModel.fetchStaff()
        }

        binding.ibAdminShield.setOnClickListener {
            KioskLogger.user("[USER ACTION] Admin Shield clicked -> Opening AdminPinDialog")
            val dialog = AdminPinDialog.newInstance()
            dialog.show(supportFragmentManager, "AdminPinDialog")
        }

        binding.tvSyncStatus.setOnClickListener {
            KioskLogger.user("[USER ACTION] Sync Status clicked -> Opening SyncLogDialog")
            val dialog = SyncLogDialog.newInstance()
            dialog.show(supportFragmentManager, "SyncLogDialog")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.staffList.collect { list ->
                        staffAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.syncTrigger.collect {
                        @Suppress("NotifyDataSetChanged")
                        staffAdapter.notifyDataSetChanged()
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        if (!isLoading) {
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                }
                launch {
                    viewModel.isOffline.collect { isOffline ->
                        KioskLogger.sys("Sync status updated: isOffline = $isOffline")
                        if (isOffline) {
                            binding.tvSyncStatus.text = getString(R.string.status_offline_cached)
                            binding.tvSyncStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.color_advance))
                            binding.tvSyncStatus.setBackgroundResource(R.drawable.bg_pill_offline)
                        } else {
                            binding.tvSyncStatus.text = getString(R.string.status_synced)
                            binding.tvSyncStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.color_clock_in_text))
                            binding.tvSyncStatus.setBackgroundResource(R.drawable.bg_pill_synced)
                        }
                    }
                }
                launch {
                    viewModel.bannerEvent.collect { bannerEvent ->
                        showConfirmationBanner(bannerEvent.message, bannerEvent.isSuccess)
                    }
                }
            }
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        KioskLogger.sys("Digital clock loop started")
        clockJob = lifecycleScope.launch {
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
            val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
            while (isActive) {
                val now = Date()
                binding.tvDate.text = dateFormat.format(now)
                binding.tvClock.text = timeFormat.format(now)
                updateBatteryStatus()
                delay(1.seconds)
            }
        }
    }

    fun showConfirmationBanner(message: String, isSuccess: Boolean) {
        bannerHideJob?.cancel()
        KioskLogger.sys("Confirmation banner displayed: $message (Success: $isSuccess)")

        binding.tvConfirmationBanner.text = message
        if (isSuccess) {
            binding.cvConfirmationOverlay.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_clock_in_bg))
            binding.cvConfirmationOverlay.strokeColor = ContextCompat.getColor(this, R.color.color_clock_in_stroke)
            binding.tvConfirmationBanner.setTextColor(ContextCompat.getColor(this, R.color.color_clock_in_text))
            soundManager.playSuccessTone()
        } else {
            binding.cvConfirmationOverlay.setCardBackgroundColor("#7F1D1D".toColorInt())
            binding.cvConfirmationOverlay.strokeColor = ContextCompat.getColor(this, R.color.color_clock_out_btn)
            binding.tvConfirmationBanner.setTextColor("#FCA5A5".toColorInt())
            soundManager.playErrorTone()
        }

        binding.cvConfirmationOverlay.visibility = View.VISIBLE

        bannerHideJob = lifecycleScope.launch {
            delay(2500.milliseconds)
            binding.cvConfirmationOverlay.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockJob?.cancel()
        clockJob = null
        bannerHideJob?.cancel()
        bannerHideJob = null
        soundManager.release()
    }
}
