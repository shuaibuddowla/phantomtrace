package com.example.phantomtracing.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phantomtracing.R
import com.example.phantomtracing.data.LogEntry
import com.example.phantomtracing.data.PreferencesManager
import com.example.phantomtracing.databinding.FragmentLogsBinding
import com.example.phantomtracing.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsFragment : Fragment() {
    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: LogsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferencesManager(requireContext())
        
        setupRecyclerView()
        setupListeners()
        updateLogs()
        startAnimations()
    }

    private fun startAnimations() {
        binding.logHeader.alpha = 0f
        binding.logHeader.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setupRecyclerView() {
        adapter = LogsAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter
        binding.rvLogs.setHasFixedSize(true)
        binding.rvLogs.setItemViewCacheSize(20)
        binding.rvLogs.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 300
            removeDuration = 200
        }
    }

    private fun setupListeners() {
        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to delete all trigger history?")
                .setPositiveButton("Clear") { _, _ ->
                    prefs.clearLogs()
                    updateLogs()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateLogs() {
        val logs = prefs.getLogs()
        if (logs.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvLogs.visibility = View.GONE
            binding.tvLogsCount.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvLogs.visibility = View.VISIBLE
            binding.tvLogsCount.visibility = View.VISIBLE
            binding.tvLogsCount.text = "${logs.size} trigger records"
            adapter.submitList(logs)
        }
    }

    private inner class LogsAdapter : ListAdapter<LogEntry, LogsAdapter.LogViewHolder>(LogDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return LogViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(log: LogEntry) {
                // Show full number, handle SYSTEM TEST specifically
                binding.tvLogNumber.text = if (log.sender == "TEST_MODE") "SYSTEM TEST" else log.sender
                binding.tvLogTimestamp.text = formatTimestamp(log.timestamp)
                binding.tvLogStatusLabel.text = log.statusText
                binding.tvLogAction.text = log.actionText
                
                val successColor = Color.parseColor("#00E5A0")
                val failedColor = Color.parseColor("#FF2E93")
                
                val color = if (log.success) successColor else failedColor
                val bgColor = if (log.success) Color.parseColor("#1A00E5A0") else Color.parseColor("#1AFF2E93")
                val iconBgColor = if (log.success) Color.parseColor("#1A00E5A0") else Color.parseColor("#1AFF2E93")

                binding.tvLogStatusLabel.setTextColor(color)
                binding.tvLogStatusLabel.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
                
                binding.leftAccent.setBackgroundColor(color)
                
                binding.statusIconContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(iconBgColor)
                binding.ivLogStatusIcon.setImageResource(if (log.success) R.drawable.ic_check_circle else R.drawable.ic_cancel)
                binding.ivLogStatusIcon.setColorFilter(color)
            }

            private fun formatTimestamp(timestamp: Long): String {
                val now = System.currentTimeMillis()
                val diff = now - timestamp
                val dayMillis = 24 * 60 * 60 * 1000
                
                val sdf = if (diff < dayMillis) {
                    SimpleDateFormat("'Today' h:mm a", Locale.getDefault())
                } else if (diff < 2 * dayMillis) {
                    SimpleDateFormat("'Yesterday' h:mm a", Locale.getDefault())
                } else {
                    SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
                }
                return sdf.format(Date(timestamp))
            }
        }
    }

    private class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.sender == newItem.sender
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
