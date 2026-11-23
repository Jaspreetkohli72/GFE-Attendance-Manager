package com.jaspreet.gfeattendancemanager.ui.admin

import android.os.Bundle
import android.view.MotionEvent
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
import com.jaspreet.gfeattendancemanager.databinding.ActivityAdminBinding
import com.jaspreet.gfeattendancemanager.ui.dialog.CreateStaffDialog
import com.jaspreet.gfeattendancemanager.ui.dialog.EditStaffDialog
import com.jaspreet.gfeattendancemanager.ui.dialog.ServerUrlDialog
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: KioskViewModel by viewModels()
    private lateinit var adminAdapter: AdminAdapter
    private lateinit var soundManager: SoundManager

    private var bannerHideJob: Job? = null
    private var adminInactivityJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KioskLogger.sys("AdminActivity started")

        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        soundManager = SoundManager()

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        viewModel.fetchAllStaff()
        resetAdminInactivityTimer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetAdminInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun resetAdminInactivityTimer() {
        adminInactivityJob?.cancel()
        adminInactivityJob = lifecycleScope.launch {
            delay(60.seconds)
            KioskLogger.sys("AdminActivity: 60s inactivity timeout -> Auto-locking Admin screen")
            finish()
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
        adminAdapter = AdminAdapter { staffMember ->
            if (supportFragmentManager.findFragmentByTag("EditStaffDialog") != null) return@AdminAdapter
            KioskLogger.user("[USER ACTION] AdminActivity: Edit clicked for ${staffMember.name} (ID: ${staffMember.id})")
            val dialog = EditStaffDialog.newInstance(staffMember)
            dialog.show(supportFragmentManager, "EditStaffDialog")
        }
        val screenWidthDp = resources.configuration.screenWidthDp
        val spanCount = when {
            screenWidthDp >= 1200 -> 5
            screenWidthDp >= 840 -> 4
            else -> 3
        }
        binding.rvAdminStaff.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvAdminStaff.adapter = adminAdapter
    }

    private fun setupClickListeners() {
        binding.adminSwipeRefresh.setOnRefreshListener {
            KioskLogger.user("[USER ACTION] AdminActivity: Pull-to-refresh triggered")
            viewModel.fetchAllStaff()
        }

        binding.btnServerConfig.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag("ServerUrlDialog") != null) return@setOnClickListener
            KioskLogger.user("[USER ACTION] AdminActivity: Server config clicked")
            val dialog = ServerUrlDialog.newInstance()
            dialog.show(supportFragmentManager, "ServerUrlDialog")
        }

        binding.btnAddStaff.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag("CreateStaffDialog") != null) return@setOnClickListener
            KioskLogger.user("[USER ACTION] AdminActivity: Add staff clicked")
            val dialog = CreateStaffDialog.newInstance()
            dialog.show(supportFragmentManager, "CreateStaffDialog")
        }

        binding.ibCloseAdmin.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.allStaffList.collect { list ->
                        adminAdapter.submitList(list)
                        binding.adminSwipeRefresh.isRefreshing = false
                        binding.pbAdminLoading.visibility = View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.adminSwipeRefresh.isRefreshing = isLoading
                        binding.pbAdminLoading.visibility = if (isLoading && adminAdapter.itemCount == 0) View.VISIBLE else View.GONE
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

    fun showConfirmationBanner(message: String, isSuccess: Boolean) {
        bannerHideJob?.cancel()
        KioskLogger.sys("Admin Confirmation banner displayed: $message (Success: $isSuccess)")

        binding.tvAdminBanner.text = message
        if (isSuccess) {
            binding.cvAdminBannerOverlay.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_clock_in_bg))
            binding.cvAdminBannerOverlay.strokeColor = ContextCompat.getColor(this, R.color.color_clock_in_stroke)
            binding.tvAdminBanner.setTextColor(ContextCompat.getColor(this, R.color.color_clock_in_text))
            soundManager.playSuccessTone()
        } else {
            binding.cvAdminBannerOverlay.setCardBackgroundColor("#7F1D1D".toColorInt())
            binding.cvAdminBannerOverlay.strokeColor = ContextCompat.getColor(this, R.color.color_clock_out_btn)
            binding.tvAdminBanner.setTextColor("#FCA5A5".toColorInt())
            soundManager.playErrorTone()
        }

        binding.cvAdminBannerOverlay.visibility = View.VISIBLE

        bannerHideJob = lifecycleScope.launch {
            delay(2500.milliseconds)
            binding.cvAdminBannerOverlay.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adminInactivityJob?.cancel()
        adminInactivityJob = null
        bannerHideJob?.cancel()
        bannerHideJob = null
        soundManager.release()
    }
}
