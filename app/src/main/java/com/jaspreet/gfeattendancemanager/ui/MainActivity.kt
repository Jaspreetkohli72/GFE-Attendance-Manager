package com.jaspreet.gfeattendancemanager.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.databinding.ActivityMainBinding
import com.jaspreet.gfeattendancemanager.ui.adapter.StaffButtonAdapter
import com.jaspreet.gfeattendancemanager.ui.dialog.AdminSettingsDialog
import com.jaspreet.gfeattendancemanager.ui.dialog.StaffActionDialog
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: KioskViewModel by viewModels()
    private lateinit var staffAdapter: StaffButtonAdapter
    private lateinit var soundManager: SoundManager

    private var clockJob: Job? = null
    private var bannerHideJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KioskLogger.sys("WORKSHOP KIOSK APPLICATION STARTED SUCCESSFULLY")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Ignore for kiosk lockdown
            }
        })

        soundManager = SoundManager()

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        startClock()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }


    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
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

        binding.ibSettings.setOnClickListener {
            KioskLogger.user("[USER ACTION] Admin settings opened")
            val dialog = AdminSettingsDialog.newInstance()
            dialog.show(supportFragmentManager, "AdminSettingsDialog")
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
            val dateFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
            while (isActive) {
                binding.tvClock.text = dateFormat.format(Date())
                delay(1000L)
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
            delay(2500L)
            binding.cvConfirmationOverlay.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockJob?.cancel()
        bannerHideJob?.cancel()
        soundManager.release()
    }
}
