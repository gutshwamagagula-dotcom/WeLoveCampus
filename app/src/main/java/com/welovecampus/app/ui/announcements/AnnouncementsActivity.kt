package com.welovecampus.app.ui.announcements

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.AnnouncementEntity
import com.welovecampus.app.databinding.ActivityAnnouncementsBinding
import com.welovecampus.app.databinding.ItemAnnouncementBinding
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Newest-first feed (Requirement 2.7). EMERGENCY items get a red card and warning icon.
 */
class AnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnnouncementsBinding
    private val adapter = AnnouncementAdapter()
    private val filter = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.title_announcements)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.chipAll.setOnClickListener { filter.value = null }
        binding.chipAcademic.setOnClickListener { filter.value = "ACADEMIC" }
        binding.chipAdmin.setOnClickListener { filter.value = "ADMINISTRATIVE" }
        binding.chipEmergency.setOnClickListener { filter.value = "EMERGENCY" }

        binding.swipeRefresh.setColorSchemeResources(R.color.crimson)
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try { container.campusRepo.refreshAll() } catch (e: Exception) { toast(R.string.offline_showing_cache) }
                binding.swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(container.campusRepo.observeAnnouncements(), filter) { list, f ->
                    if (f == null) list else list.filter { it.category == f }
                }.collect {
                    adapter.submitList(it)
                    binding.emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}

class AnnouncementAdapter : ListAdapter<AnnouncementEntity, AnnouncementAdapter.VH>(DIFF) {

    class VH(val b: ItemAnnouncementBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        val ctx = holder.itemView.context
        val emergency = a.category == "EMERGENCY"
        with(holder.b) {
            sender.text = a.sender
            title.text = a.title
            message.text = a.message
            time.text = DateUtils.formatDateTime(a.timestamp)
            category.text = ctx.getString(
                when (a.category) {
                    "EMERGENCY" -> R.string.category_emergency
                    "ADMINISTRATIVE" -> R.string.category_administrative
                    else -> R.string.category_academic
                }
            )
            icon.setImageResource(if (emergency) R.drawable.ic_warning else R.drawable.ic_campaign)
            icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, if (emergency) R.color.emergency else R.color.crimson))
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, if (emergency) R.color.emergency_bg else R.color.white))
            card.strokeColor = ContextCompat.getColor(ctx, if (emergency) R.color.emergency else R.color.card_stroke)
            card.strokeWidth = ctx.resources.getDimensionPixelSize(if (emergency) R.dimen.stroke_bold else R.dimen.stroke_thin)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AnnouncementEntity>() {
            override fun areItemsTheSame(a: AnnouncementEntity, b: AnnouncementEntity) = a.announcementId == b.announcementId
            override fun areContentsTheSame(a: AnnouncementEntity, b: AnnouncementEntity) = a == b
        }
    }
}
