package com.example.phantomtracing.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.*
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.fragment.findNavController
import com.example.phantomtracing.R
import com.example.phantomtracing.data.PreferencesManager
import com.example.phantomtracing.service.LocationForegroundService
import com.example.phantomtracing.utils.PermissionHelper
import com.example.phantomtracing.databinding.FragmentHomeBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager
    private lateinit var permissionHelper: PermissionHelper

    private val TAG = "PhantomTrace_Home"
    private val handler = Handler(Looper.getMainLooper())
    private var logoLoopRunnable: Runnable? = null

    private val testResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(LocationForegroundService.EXTRA_TEST_MESSAGE) ?: return
            showTestResult(message)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            prefs = PreferencesManager(requireContext())
            permissionHelper = PermissionHelper(requireActivity())

            setupUI()
            updateStatus()
            startAnimations()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
        }
    }



    private fun startAnimations() {
        if (_binding == null) return

        startOrbAnimation(binding.orb1, 6000, -20f)
        startOrbAnimation(binding.orb2, 8000, 15f)
        startOrbAnimation(binding.orb3, 7000, -12f)

        setupSpringEffect(binding.btnLocate)
        setupSpringEffect(binding.btnAlarm)
        setupSpringEffect(binding.btnTestNow)

        val cards = listOf(binding.statsGrid, binding.statusCard, binding.developerCard)
        cards.forEachIndexed { i, card ->
            card.alpha = 0f
            card.translationY = 30f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(100L + (i * 80L))
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
        
        updateLogoState()
    }

    private fun updateLogoState() {
        if (_binding == null) return
        
        val hasPerms = permissionHelper.hasAllPermissions()
        val isArmed = PreferencesManager.isSetupComplete(requireContext())
        val isReady = hasPerms && isArmed

        logoLoopRunnable?.let { handler.removeCallbacks(it) }
        
        if (isReady) {
            binding.ivHomeLogo.setImageResource(R.drawable.ic_phantom_logo_animated)
            val drawable = binding.ivHomeLogo.drawable
            if (drawable is AnimatedVectorDrawable) {
                drawable.start()
                logoLoopRunnable = object : Runnable {
                    override fun run() {
                        if (_binding != null) {
                            drawable.stop()
                            drawable.start()
                            handler.postDelayed(this, 1200)
                        }
                    }
                }.also { handler.post(it) }
            }
        } else {
            binding.ivHomeLogo.setImageResource(R.drawable.ic_phantom_logo)
        }
    }

    private fun startOrbAnimation(orb: View, duration: Long, translationY: Float) {
        val animator = ObjectAnimator.ofFloat(orb, "translationY", 0f, translationY, 0f)
        animator.duration = duration
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.interpolator = FastOutSlowInInterpolator()
        animator.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSpringEffect(btn: View) {
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
            }
            false
        }
    }

    private fun setupUI() {
        binding.btnLocate.setOnClickListener {
            openLocationInMaps()
        }

        binding.btnAlarm.setOnClickListener {
            val bundle = Bundle().apply {
                putString("scroll_to", "alarm")
            }
            try {
                findNavController().navigate(R.id.settingsFragment, bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to Settings failed", e)
            }
        }

        binding.btnTestNow.setOnClickListener {
            handleTestMode()
        }

        binding.btnViewLogs.setOnClickListener {
            try {
                findNavController().navigate(R.id.logsFragment)
            } catch (e: Exception) {
                (activity as? MainActivity)?.navigateToLogs()
            }
        }

        binding.btnCloseTest.setOnClickListener {
            binding.testResultCard.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(300)
                .withEndAction { binding.testResultCard.visibility = View.GONE }
                .start()
        }

        binding.testResultCard.setOnClickListener {
            val message = binding.tvTestMessage.text.toString()
            val url = extractUrl(message)
            if (url != null) {
                openUrl(url)
            } else {
                openLocationInMaps()
            }
        }

        binding.developerCard.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_homeFragment_to_developerFragment)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to Developer page failed", e)
            }
        }



        updateQuickStats()
        updateLastActivity()
    }

    private fun extractUrl(message: String): String? {
        val urlRegex = "https?://[\\\\w\\\\d./?=#-]+".toRegex()
        return urlRegex.find(message)?.value
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
            Toast.makeText(context, "Could not open tracking link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTestResult(message: String) {
        if (_binding == null) return
        
        binding.tvTestMessage.text = message
        binding.testResultCard.visibility = View.VISIBLE
        binding.testResultCard.alpha = 0f
        binding.testResultCard.translationY = -40f
        
        binding.testResultCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
            
        Toast.makeText(requireContext(), "Test Result Received", Toast.LENGTH_SHORT).show()
    }

    private fun openLocationInMaps() {
        try {
            val lat = prefs.getLastLatitude()
            val lng = prefs.getLastLongitude()

            if (lat != 0.0 && lng != 0.0) {
                val uri = "https://maps.google.com/?q=$lat,$lng"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")
                
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
            } else {
                Toast.makeText(context, "No location data available yet.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in openLocationInMaps", e)
        }
    }

    private fun handleTestMode() {
        try {
            val context = context ?: return
            if (!permissionHelper.hasAllPermissions()) {
                Snackbar.make(binding.root, "⚠️ Missing required permissions", Snackbar.LENGTH_LONG)
                    .setAction("Grant") {
                        permissionHelper.requestPermissionsSequentially(PermissionHelper.REQ_CODE_PERMISSIONS)
                    }
                    .show()
                return
            }

            if (!PreferencesManager.isSetupComplete(context)) {
                Snackbar.make(binding.root, "⚠️ Complete setup in Settings first", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(Color.parseColor("#FF2E93")).show()
                return
            }

            binding.btnTestNow.isEnabled = false
            binding.testResultCard.visibility = View.GONE
            
            val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
                putExtra("sender_number", "TEST_MODE")
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Toast.makeText(context, "System test initiated...", Toast.LENGTH_SHORT).show()
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (_binding != null) {
                    binding.btnTestNow.isEnabled = true
                    updateLastActivity()
                }
            }, 8000)
            
        } catch (e: Exception) {
            binding.btnTestNow.isEnabled = true
        }
    }

    private fun updateLastActivity() {
        if (_binding == null) return
        try {
            val logs = prefs.getLogs()
            if (logs.isNotEmpty()) {
                val last = logs.first()
                binding.tvLastActivityNumber.text = if (last.sender == "TEST_MODE") "SYSTEM TEST" else last.sender
                binding.tvLogStatusLabel.text = last.statusText
            } else {
                binding.tvLastActivityNumber.text = "NO ACTIVITY"
                binding.tvLogStatusLabel.text = "SYSTEM IDLE"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last activity", e)
        }
    }

    private fun updateQuickStats() {
        if (_binding == null) return
        try {
            binding.tvTotalTriggers.text = prefs.getLogs().size.toString()
            
            val isSetup = PreferencesManager.isSetupComplete(requireContext())
            val hasPerms = permissionHelper.hasAllPermissions()
            
            binding.tvSystemStatus.text = if (isSetup && hasPerms) "ARMED" else "OFFLINE"
            binding.tvSystemStatus.setTextColor(if (isSetup && hasPerms) Color.parseColor("#00F5A0") else Color.parseColor("#FF2E93"))

            binding.tvActiveSims.text = getActiveSimCount().toString()
            
            val context = requireContext()
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bm.isCharging
            } else {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }

            binding.tvBatteryStatus.text = "$level%"
            binding.tvPowerLabel.visibility = View.VISIBLE
            binding.ivChargingIcon.visibility = if (isCharging) View.VISIBLE else View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error updating quick stats", e)
        }
    }

    private fun getActiveSimCount(): Int {
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return 0
            val sm = requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            return sm?.activeSubscriptionInfoCount ?: 0
        } catch (e: Exception) { return 0 }
    }

    private fun updateStatus() {
        if (_binding == null) return
        try {
            val isArmed = PreferencesManager.isSetupComplete(requireContext())
            val hasPerms = permissionHelper.hasAllPermissions()
            
            if (isArmed && hasPerms) {
                binding.tvSystemSecured.visibility = View.VISIBLE
            } else {
                binding.tvSystemSecured.visibility = View.GONE
            }
            
            updateLogoState()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status", e)
        }
    }

    override fun onResume() {
        super.onResume()
        updateQuickStats()
        updateStatus()
        updateLastActivity()

        
        val filter = IntentFilter(LocationForegroundService.ACTION_TEST_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(testResultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(testResultReceiver, filter)
        }
    }



    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(testResultReceiver)
        } catch (e: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        logoLoopRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}