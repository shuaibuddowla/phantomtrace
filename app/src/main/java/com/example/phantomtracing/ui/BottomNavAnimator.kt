package com.example.phantomtracing.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object BottomNavAnimator {
    fun animateTabSelect(view: View) {
        if (isReduceMotionEnabled(view.context)) return

        // Reduced scale and adjusted stiffness for a smoother, more premium feel
        SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }.also {
            view.scaleX = 1.15f
            it.start()
        }
        SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }.also {
            view.scaleY = 1.15f
            it.start()
        }
    }

    private fun isReduceMotionEnabled(context: Context): Boolean {
        return Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
}
