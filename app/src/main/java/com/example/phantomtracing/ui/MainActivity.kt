package com.example.phantomtracing.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.phantomtracing.R
import com.example.phantomtracing.databinding.ActivityMainBinding
import com.example.phantomtracing.utils.PermissionHelper
import com.example.phantomtracing.utils.HapticUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSplash()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != navController.currentDestination?.id) {
                val itemView = binding.bottomNav.findViewById<View>(item.itemId)
                itemView?.let {
                    HapticUtil.light(it)
                    BottomNavAnimator.animateTabSelect(it)
                    moveIndicator(it)
                }

                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopEnterAnim(R.anim.fade_in)
                    .setPopExitAnim(R.anim.fade_out)
                    .setPopUpTo(navController.graph.startDestinationId, inclusive = false, saveState = true)
                    .build()

                try {
                    navController.navigate(item.itemId, null, options)
                } catch (e: Exception) {
                    return@setOnItemSelectedListener false
                }
            }
            true
        }

        binding.bottomNav.setOnItemReselectedListener { item ->
            val itemView = binding.bottomNav.findViewById<View>(item.itemId)
            itemView?.let {
                HapticUtil.light(it)
                BottomNavAnimator.animateTabSelect(it)
            }
            navController.popBackStack(item.itemId, false)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val itemView = binding.bottomNav.findViewById<View>(destination.id)
            if (itemView != null) {
                binding.navIndicator.visibility = View.VISIBLE
                moveIndicator(itemView)
            } else {
                binding.navIndicator.visibility = View.GONE
            }
        }

        binding.bottomNav.post {
            val selectedItem = binding.bottomNav.findViewById<View>(binding.bottomNav.selectedItemId)
            if (selectedItem != null) {
                moveIndicator(selectedItem)
            }
        }
    }

    private fun moveIndicator(view: View) {
        val x = view.x + (view.width / 2) - (binding.navIndicator.width / 2)
        binding.navIndicator.animate()
            .x(x)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    // ── Navigate to Logs from any fragment ───────────────────────────
    fun navigateToLogs() {
        binding.bottomNav.selectedItemId = R.id.logsFragment
    }

    // ── Splash screen ────────────────────────────────────────────────
    private fun setupSplash() {
        val drawable = binding.ivSplashLogo.drawable
        if (drawable is AnimatedVectorDrawable) {
            drawable.start()
            val loopRunnable = object : Runnable {
                override fun run() {
                    if (binding.splashOverlay.visibility == View.VISIBLE) {
                        drawable.stop()
                        drawable.start()
                        handler.postDelayed(this, 1200)
                    }
                }
            }
            handler.post(loopRunnable)
        }

        handler.postDelayed({
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    binding.splashOverlay.visibility = View.GONE
                    checkAndRequestPermissions()
                }
                .start()
        }, 3000)
    }

    // ── Permissions ──────────────────────────────────────────────────
    private fun checkAndRequestPermissions() {
        val permissionHelper = PermissionHelper(this)
        val missing = permissionHelper.getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                PermissionHelper.REQ_CODE_PERMISSIONS
            )
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        val permissionHelper = PermissionHelper(this)
        if (!permissionHelper.isBatteryOptimizationIgnored()) {
            permissionHelper.requestIgnoreBatteryOptimizations()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQ_CODE_PERMISSIONS) {
            checkBatteryOptimization()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}