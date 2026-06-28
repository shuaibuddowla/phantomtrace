package com.example.phantomtracing.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.phantomtracing.R
import com.example.phantomtracing.data.PreferencesManager
import com.example.phantomtracing.databinding.FragmentSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var prefs: PreferencesManager? = null
    private val tagPrefix = "PhantomTrace_Settings"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val context = context ?: return
        prefs = PreferencesManager(context)

        setupListeners()
        // Switches are setup after data is loaded to prevent initial snackbars
        loadDataAsync()
    }

    private fun loadDataAsync() {
        val currentPrefs = prefs ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    mapOf(
                        "keyword" to (currentPrefs.getKeyword() ?: ""),
                        "pin" to (currentPrefs.getPin() ?: ""),
                        "owner" to currentPrefs.getOwnerName(),
                        "trusted" to (currentPrefs.getTrustedNumber() ?: ""),
                        "alarm" to currentPrefs.isAlarmEnabled(),
                        "duration" to currentPrefs.getAlarmDuration(),
                        "boot" to currentPrefs.isBootEnabled(),
                        "live" to currentPrefs.isLiveTrackingEnabled(),
                        "dualSim" to currentPrefs.isDualSimEnabled(),
                        "trackingDuration" to currentPrefs.getTrackingDurationMinutes()
                    )
                }
                updateUIWithData(data)
            } catch (e: Exception) {
                Log.e(tagPrefix, "Error loading data", e)
            }
        }
    }

    private fun updateUIWithData(data: Map<String, Any>) {
        if (_binding == null) return
        
        // Remove listeners temporarily to avoid programmatic triggers showing snackbars
        binding.switchAlarm.setOnCheckedChangeListener(null)
        binding.switchLiveTracking.setOnCheckedChangeListener(null)
        binding.switchDualSim.setOnCheckedChangeListener(null)
        binding.switchBoot.setOnCheckedChangeListener(null)

        binding.tvKeywordValue.text = if ((data["keyword"] as String).isEmpty()) "Not Set" else "••••••••"
        binding.tvPinValue.text = if ((data["pin"] as String).isEmpty()) "Not Set" else "••••"

        binding.tvOwnerNameValue.text = (data["owner"] as String).ifEmpty { "Tap to set" }
        binding.tvTrustedNumberValue.text = (data["trusted"] as String).ifEmpty { "Any" }

        binding.switchAlarm.isChecked = data["alarm"] as Boolean
        binding.tvAlarmDurationValue.text = "${data["duration"]} sec"

        binding.switchLiveTracking.isChecked = data["live"] as Boolean
        binding.tvTrackingDuration.text = "${data["trackingDuration"]} min"

        binding.switchDualSim.isChecked = data["dualSim"] as Boolean
        binding.switchBoot.isChecked = data["boot"] as Boolean
        
        // Now that initial state is set, attach the listeners
        setupSwitches()
        updateBatteryStatusUI()
    }

    private fun updateBatteryStatusUI() {
        val context = context ?: return
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            _binding?.tvBatteryStatus?.text = if (isIgnoring) "Disabled" else "Enabled"
            _binding?.tvBatteryStatus?.setTextColor(if (isIgnoring) Color.parseColor("#00E5A0") else Color.parseColor("#9999BB"))
        } catch (ignored: Exception) {}
    }

    private fun setupListeners() {
        binding.rowKeyword.setOnClickListener {
            showSecretDialog("Secret Keyword", true) { newValue ->
                prefs?.saveKeyword(newValue)
                showSuccess("Keyword updated")
                loadDataAsync()
                true
            }
        }

        binding.rowPin.setOnClickListener {
            showSecretDialog("Secret PIN", false) { newValue ->
                prefs?.savePin(newValue)
                showSuccess("PIN updated")
                loadDataAsync()
                true
            }
        }

        binding.rowTrustedNumber.setOnClickListener {
            showStandardEditDialog("Trusted Number", "Enter phone number", InputType.TYPE_CLASS_PHONE) { newValue ->
                prefs?.saveTrustedNumber(newValue)
                showSuccess("Trusted number saved")
                loadDataAsync()
                true
            }
        }

        binding.rowOwnerName.setOnClickListener {
            showStandardEditDialog("Device Owner", "Enter owner name", InputType.TYPE_CLASS_TEXT) { newValue ->
                prefs?.saveOwnerName(newValue)
                showSuccess("Owner name updated")
                loadDataAsync()
                true
            }
        }

        binding.rowAlarmDuration.setOnClickListener { showDurationDialog() }
        
        binding.rowTrackingDuration.setOnClickListener { showTrackingDurationBottomSheet() }

        binding.rowBattery.setOnClickListener {
            val context = context ?: return@setOnClickListener
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (ignored: Exception) {
                    showError("Could not open settings")
                }
            }
        }

        binding.btnFacebook.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/shuaibdowla"))
            startActivity(intent)
        }

        binding.btnTelegram.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/shuaibdowla"))
            startActivity(intent)
        }

        // How to Use Listeners
        binding.rowStep1.setOnClickListener { showStepDetails(1) }
        binding.rowStep2.setOnClickListener { showStepDetails(2) }
        binding.rowStep3.setOnClickListener { showStepDetails(3) }
        binding.rowStep4.setOnClickListener { showStepDetails(4) }
        binding.rowStep5.setOnClickListener { showStepDetails(5) }
        binding.rowStep6.setOnClickListener { showStepDetails(6) }
        binding.rowStep7.setOnClickListener { showStepDetails(7) }

        // Developer Photo Popup
        binding.ivDeveloperPhoto.setOnClickListener { showDeveloperPhotoPopup() }
    }

    private fun showDeveloperPhotoPopup() {
        val context = context ?: return
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val photoView = ShapeableImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(280), dp(280))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.img)
            setStrokeColorResource(android.R.color.white)
            strokeWidth = dp(4f)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(dp(40f))
                .build()
            elevation = dp(12f)
        }

        container.addView(photoView)
        dialog.setContentView(container)
        
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
            attributes?.windowAnimations = android.R.style.Animation_Dialog
        }

        photoView.alpha = 0f
        photoView.scaleX = 0.8f
        photoView.scaleY = 0.8f
        
        dialog.show()

        photoView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        container.setOnClickListener { dialog.dismiss() }
    }

    private fun showStepDetails(step: Int) {
        val context = context ?: return
        val dialog = BottomSheetDialog(context, R.style.GlassBottomSheetTheme)
        
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.glass_bottom_sheet)
            paddingHorizontal(24)
            paddingBottom(32)
        }

        // Drag Handle
        val handle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
                bottomMargin = dp(24)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#80FFFFFF"))
                cornerRadius = dp(2f)
            }
        }
        content.addView(handle)

        val details = when(step) {
            1 -> listOf("Set Your Secret Trigger", "Go to Settings → Trigger Configuration.\nSet a secret keyword (minimum 6 characters) and a 4-digit PIN that only you know. This is the secret command that activates PhantomTrace.", "Choose a keyword that looks like a normal message to avoid suspicion.", R.drawable.ic_key)
            2 -> listOf("Add Trusted Number", "Go to Settings → Security & Ownership.\nAdd the phone number that is allowed to trigger PhantomTrace. Leave empty to allow triggers from any number.", "Add your family member's number as trusted for extra security.", R.drawable.ic_phone_shield)
            3 -> listOf("Activate Protection", "Tap Activate on the Home screen.\nMake sure all permissions show Granted and battery optimization is disabled. The status should show ARMED.", "Disable battery optimization to prevent Android from killing the app in background.", R.drawable.ic_shield)
            4 -> listOf("When Your Phone Is Lost", "From any phone, send an SMS to your lost phone in this exact format:\n\n[keyword] [PIN]\n\nExample: phantom1 2222\n\nThe SMS works even with 1 bar of signal. Both SIMs will be tried automatically.", null, R.drawable.ic_bell)
            5 -> listOf("Receive Live Location", "You will receive an SMS reply with a live tracking link. Open it in any browser to see your phone's exact location on a satellite map updating every 10 seconds.", "The link stays active for your selected tracking duration (up to 4 hours).", R.drawable.ic_map)
            6 -> listOf("Navigate to Device", "On the live map page, tap the Navigate button at the bottom. Google Maps will open automatically with turn-by-turn navigation directly to your phone's location.", null, R.drawable.ic_logs)
            7 -> listOf("Stop Live Tracking", "To stop live tracking early, send this SMS to your phone:\n\nstop [PIN]\n\nExample: stop 2222\n\nTracking also stops automatically after your selected duration ends.", null, R.drawable.ic_power)
            else -> listOf("", "", null, R.drawable.ic_help)
        }

        val title = details[0] as String
        val textContent = details[1] as String
        val tip = details[2] as? String
        val iconRes = details[3] as Int

        // Icon Header with enhanced glow
        val iconHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            background = ContextCompat.getDrawable(context, R.drawable.hero_glow)
        }
        val iconView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        iconContainer.addView(iconView)
        
        val badge = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00B4D8"))
                setStroke(dp(1.5f).toInt(), Color.WHITE)
            }
            gravity = Gravity.CENTER
            text = step.toString()
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
        }
        iconContainer.addView(badge)
        iconHeader.addView(iconContainer)

        // Title with shadow for readability
        val tvTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) }
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 4f, Color.BLACK)
        }
        iconHeader.addView(tvTitle)
        content.addView(iconHeader)

        // Description - pure white for maximum contrast on high-opacity glass
        content.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
            text = textContent
            setTextColor(Color.WHITE)
            textSize = 15f
            setLineSpacing(0f, 1.6f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
        })

        // Tip box - more liquid look
        if (tip != null) {
            val tipBox = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
                orientation = LinearLayout.HORIZONTAL
                paddingAll(14)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3300B4D8"))
                    setStroke(dp(1.5f).toInt(), Color.parseColor("#6000B4D8"))
                    cornerRadius = dp(16f)
                }
            }
            tipBox.addView(TextView(context).apply {
                text = "💡 "
                textSize = 16f
            })
            tipBox.addView(TextView(context).apply {
                text = tip
                setTextColor(Color.parseColor("#00B4D8"))
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setLineSpacing(0f, 1.4f)
            })
            content.addView(tipBox)
        }

        // Close Button - Extra Premium Glass
        val btnClose = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(32) }
            background = ContextCompat.getDrawable(context, R.drawable.btn_glass_premium)
            backgroundTintList = null // Allow gradient to show
            text = "Got it"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 16f
            letterSpacing = 0.02f
            typeface = Typeface.DEFAULT_BOLD
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(27)
            elevation = dp(8f)
            setOnClickListener { dialog.dismiss() }
        }
        content.addView(btnClose)

        dialog.setContentView(content)
        setupGlassBottomSheetBehavior(dialog)
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun View.paddingHorizontal(px: Int) = setPadding(dp(px), paddingTop, dp(px), paddingBottom)
    private fun View.paddingBottom(px: Int) = setPadding(paddingLeft, paddingTop, paddingRight, dp(px))
    private fun View.paddingAll(px: Int) = setPadding(dp(px), dp(px), dp(px), dp(px))

    private fun setupSwitches() {
        binding.switchAlarm.setOnCheckedChangeListener { _, isChecked ->
            prefs?.setAlarmEnabled(isChecked)
            showSuccess(if (isChecked) "Alarm enabled" else "Alarm disabled")
        }
        binding.switchLiveTracking.setOnCheckedChangeListener { _, isChecked ->
            prefs?.setLiveTrackingEnabled(isChecked)
            showSuccess(if (isChecked) "Live Tracking enabled" else "Live Tracking disabled")
        }
        binding.switchDualSim.setOnCheckedChangeListener { _, isChecked ->
            prefs?.setDualSimEnabled(isChecked)
            showSuccess(if (isChecked) "Dual SIM Fallback enabled" else "Dual SIM Fallback disabled")
        }
        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs?.setBootEnabled(isChecked)
            showSuccess(if (isChecked) "Start on Boot enabled" else "Start on Boot disabled")
        }
    }

    private fun showTrackingDurationBottomSheet() {
        val context = context ?: return
        val dialog = BottomSheetDialog(context, R.style.GlassBottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_tracking_duration, null)
        
        val options = listOf(
            Triple(15, "⏱️ 15 Minutes", "Quick search"),
            Triple(30, "⏱️ 30 Minutes", "Standard search"),
            Triple(60, "⏱️ 1 Hour", "Extended search"),
            Triple(120, "⏱️ 2 Hours", "Long search"),
            Triple(240, "⏱️ 4 Hours", "Maximum (Battery intensive)")
        )

        val container = view.findViewById<ViewGroup>(R.id.optionsContainer)
        val currentDuration = prefs?.getTrackingDurationMinutes() ?: 30

        options.forEach { (minutes, title, desc) ->
            val itemView = layoutInflater.inflate(R.layout.item_duration_option, container, false)
            itemView.findViewById<TextView>(R.id.tvTitle).text = title
            itemView.findViewById<TextView>(R.id.tvDescription).text = desc
            val radio = itemView.findViewById<RadioButton>(R.id.radioButton)
            
            val isSelected = currentDuration == minutes
            radio.isChecked = isSelected
            
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.glass_card_selected)
            }

            itemView.setOnClickListener {
                prefs?.setTrackingDurationMinutes(minutes)
                showSuccess("✓ Tracking set to $minutes minutes")
                loadDataAsync()
                dialog.dismiss()
            }
            container.addView(itemView)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showSecretDialog(title: String, isKeyword: Boolean, onSave: (String) -> Boolean) {
        val context = context ?: return
        val dialog = BottomSheetDialog(context, R.style.GlassBottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.dialog_edit_setting, null)
        
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val etInput = view.findViewById<EditText>(R.id.etDialogInput)
        val tvError = view.findViewById<TextView>(R.id.tvError)
        val ivToggle = view.findViewById<ImageView>(R.id.ivDialogToggle)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnDialogSave)
        val btnCancel = view.findViewById<View>(R.id.btnDialogCancel)

        tvTitle.text = title
        etInput.hint = if (isKeyword) "Enter keyword" else "Enter 4-digit PIN"
        etInput.inputType = if (isKeyword) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD 
                          else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        etInput.transformationMethod = PasswordTransformationMethod.getInstance()
        
        ivToggle.visibility = View.VISIBLE
        ivToggle.setOnClickListener {
            if (etInput.transformationMethod is PasswordTransformationMethod) {
                etInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                etInput.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            etInput.setSelection(etInput.text.length)
        }

        btnSave.setOnClickListener {
            val input = etInput.text.toString().trim()
            if (isKeyword) {
                if (input.length >= 6) {
                    if (onSave(input)) dialog.dismiss()
                } else {
                    tvError.text = "Minimum 6 characters required"
                    tvError.visibility = View.VISIBLE
                }
            } else {
                if (input.length == 4 && input.all { it.isDigit() }) {
                    if (onSave(input)) dialog.dismiss()
                } else {
                    tvError.text = "4 digits required"
                    tvError.visibility = View.VISIBLE
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        setupGlassBottomSheetBehavior(dialog)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showStandardEditDialog(title: String, hint: String, inputType: Int, onSave: (String) -> Boolean) {
        val context = context ?: return
        val dialog = BottomSheetDialog(context, R.style.GlassBottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.dialog_edit_setting, null)
        
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val etInput = view.findViewById<EditText>(R.id.etDialogInput)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnDialogSave)
        val btnCancel = view.findViewById<View>(R.id.btnDialogCancel)

        tvTitle.text = title
        etInput.hint = hint
        etInput.inputType = inputType
        
        btnSave.setOnClickListener { if (onSave(etInput.text.toString().trim())) dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        
        setupGlassBottomSheetBehavior(dialog)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showDurationDialog() {
        val context = context ?: return
        val dialog = BottomSheetDialog(context, R.style.GlassBottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.dialog_duration_picker, null)
        
        val tvValue = view.findViewById<TextView>(R.id.tvDurationValue)
        val seekBar = view.findViewById<SeekBar>(R.id.seekBarDuration)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnDialogSave)
        val btnCancel = view.findViewById<View>(R.id.btnDialogCancel)

        val currentDuration = prefs?.getAlarmDuration() ?: 60
        // Mapping: 0->30, 1->60, 2->90, 3->120, 4->150, 5->180, 6->210, 7->240, 8->270, 9->300
        val initialProgress = ((currentDuration - 30) / 30).coerceIn(0, 9)
        seekBar.progress = initialProgress
        tvValue.text = "$currentDuration seconds"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = (progress * 30) + 30
                tvValue.text = "$seconds seconds"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val seconds = (seekBar.progress * 30) + 30
            prefs?.setAlarmDuration(seconds)
            showSuccess("Alarm duration updated")
            loadDataAsync()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        setupGlassBottomSheetBehavior(dialog)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun setupGlassBottomSheetBehavior(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                it.alpha = 0f
                it.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun showSuccess(message: String) {
        _binding?.let {
            Snackbar.make(it.root, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.parseColor("#00E5A0"))
                .setTextColor(Color.BLACK)
                .show()
        }
    }

    private fun showError(message: String) {
        _binding?.let {
            Snackbar.make(it.root, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.parseColor("#FF4757"))
                .setTextColor(Color.WHITE)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatusUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
