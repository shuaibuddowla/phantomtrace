package com.example.phantomtracing.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import com.example.phantomtracing.databinding.FragmentDeveloperBinding

class DeveloperFragment : Fragment() {
    private var _binding: FragmentDeveloperBinding? = null
    private val binding get() = _binding!!
    private var isExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeveloperBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        startEntranceAnimation()
    }

    private fun setupListeners() {
        binding.btnFb.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/shuaibdowla")))
        }
        binding.btnTg.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/shuaibdowla")))
        }

        binding.tvReadMore.setOnClickListener {
            toggleBio()
        }
    }

    private fun toggleBio() {
        isExpanded = !isExpanded
        if (isExpanded) {
            binding.tvDevBio.maxLines = Integer.MAX_VALUE
            binding.tvReadMore.text = "Read Less"
        } else {
            binding.tvDevBio.maxLines = 4
            binding.tvReadMore.text = "Read More"
        }
    }

    private fun startEntranceAnimation() {
        // Initial states
        binding.ivDevProfile.alpha = 0f
        binding.ivDevProfile.scaleX = 0.5f
        binding.ivDevProfile.scaleY = 0.5f
        
        binding.tvDevName.alpha = 0f
        binding.tvDevName.translationY = 50f
        
        binding.tvDevRole.alpha = 0f
        binding.tvDevRole.translationY = 30f
        
        binding.tvDevBio.alpha = 0f
        binding.tvDevBio.translationY = 30f
        binding.tvReadMore.alpha = 0f
        binding.tvReadMore.translationY = 30f

        // Animate profile image
        binding.ivDevProfile.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

        // Animate text with stagger
        binding.tvDevName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvDevRole.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(450)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvDevBio.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvReadMore.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(650)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
