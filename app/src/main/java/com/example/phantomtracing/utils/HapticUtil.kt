package com.example.phantomtracing.utils

import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {

    /**
     * Light haptic feedback for subtle interactions like switching tabs or selecting small items.
     */
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Medium haptic feedback for more significant actions like voting or clicking a main button.
     */
    fun medium(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /**
     * Heavy haptic feedback for long presses or critical actions.
     */
    fun heavy(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
