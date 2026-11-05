package com.welovecampus.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.databinding.FragmentHomeBinding
import com.welovecampus.app.databinding.ItemInfoCardBinding
import com.welovecampus.app.ui.announcements.AnnouncementsActivity
import com.welovecampus.app.ui.assignments.AssignmentDetailActivity
import com.welovecampus.app.ui.common.VmFactory
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.ui.events.EventsActivity
import com.welovecampus.app.ui.main.MainActivity
import com.welovecampus.app.ui.profile.ProfileActivity
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Network
import com.welovecampus.app.util.Priority
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels { VmFactory(requireContext().container) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipeRefresh.setColorSchemeResources(R.color.crimson)
        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.btnCheckIn.setOnClickListener { checkIn() }
        binding.cardNextClass.setOnClickListener { (activity as? MainActivity)?.selectTab(1) }
        binding.btnAllAnnouncements.setOnClickListener { startActivity(Intent(requireContext(), AnnouncementsActivity::class.java)) }
        binding.btnAllEvents.setOnClickListener { startActivity(Intent(requireContext(), EventsActivity::class.java)) }
        binding.cardNoModules.setOnClickListener { startActivity(Intent(requireContext(), ProfileActivity::class.java)) }
        binding.cardClash.setOnClickListener { (activity as? MainActivity)?.selectTab(1) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun render(s: HomeState) {
        val ctx = requireContext()
        val name = s.profile?.fullName?.substringBefore(" ") ?: ""
        binding.greeting.text = getString(R.string.greeting, name)

        // Next class
        binding.nextClassText.text = s.nextClass?.let {
            getString(R.string.next_class_value, it.moduleCode, dayName(it.dayOfWeek), it.startTime, it.room)
        } ?: getString(R.string.no_classes)

        binding.cardNoModules.visibility =
            if (s.profile != null && s.profile.moduleList().isEmpty()) View.VISIBLE else View.GONE
        binding.cardClash.visibility = if (s.clashCount > 0) View.VISIBLE else View.GONE
        binding.clashText.text = getString(R.string.clash_warning, s.clashCount)

        // Due Soon (merged)
        fill(binding.dueSoonContainer, s.dueSoon.isEmpty(), R.string.nothing_due) {
            s.dueSoon.forEach { item ->
                addCard(
                    binding.dueSoonContainer,
                    item.title,
                    "${item.moduleOrPlace} • ${DateUtils.formatDateTime(item.time)}",
                    when (item.priority) {
                        Priority.HIGH -> R.color.priority_high
                        Priority.MEDIUM -> R.color.priority_medium
                        Priority.LOW -> R.color.priority_low
                        null -> R.color.gold
                    },
                    if (item.isAssignment) R.drawable.ic_tasks else R.drawable.ic_event
                ) {
                    if (item.isAssignment) startActivity(AssignmentDetailActivity.intent(ctx, item.id))
                    else startActivity(Intent(ctx, EventsActivity::class.java))
                }
            }
        }

        // Announcements
        fill(binding.announcementsContainer, s.announcements.isEmpty(), R.string.no_announcements) {
            s.announcements.forEach { a ->
                val emergency = a.category == "EMERGENCY"
                addCard(
                    binding.announcementsContainer, a.title, "${a.sender} • ${DateUtils.formatDateTime(a.timestamp)}",
                    if (emergency) R.color.emergency else R.color.crimson,
                    if (emergency) R.drawable.ic_warning else R.drawable.ic_campaign
                ) { startActivity(Intent(ctx, AnnouncementsActivity::class.java)) }
            }
        }

        // Upcoming events
        fill(binding.eventsContainer, s.upcomingEvents.isEmpty(), R.string.no_upcoming_events) {
            s.upcomingEvents.forEach { e ->
                addCard(binding.eventsContainer, e.title, "${DateUtils.formatDateTime(e.eventDate)} • ${e.location}", R.color.gold, R.drawable.ic_event) {
                    startActivity(Intent(ctx, EventsActivity::class.java))
                }
            }
        }

        // Streak / gamification
        binding.streakText.text = getString(R.string.streak_value, s.displayStreak, s.profile?.points ?: 0)
        binding.badgeCount.text = getString(R.string.badges_count, s.profile?.badgeList()?.size ?: 0)
        binding.btnCheckIn.isEnabled = !s.checkedInToday
        binding.btnCheckIn.text = getString(if (s.checkedInToday) R.string.checked_in_today else R.string.check_in)
    }

    private inline fun fill(container: LinearLayout, empty: Boolean, emptyText: Int, block: () -> Unit) {
        container.removeAllViews()
        if (empty) {
            addCard(container, getString(emptyText), null, R.color.grey_light, null, null)
        } else block()
    }

    private fun addCard(
        container: LinearLayout, title: String, subtitle: String?, accentColor: Int, icon: Int?, onClick: (() -> Unit)?
    ) {
        val card = ItemInfoCardBinding.inflate(layoutInflater, container, false)
        card.title.text = title
        card.subtitle.text = subtitle
        card.subtitle.visibility = if (subtitle == null) View.GONE else View.VISIBLE
        card.accent.setBackgroundColor(ContextCompat.getColor(requireContext(), accentColor))
        if (icon != null) card.icon.setImageResource(icon) else card.icon.visibility = View.GONE
        onClick?.let { click -> card.root.setOnClickListener { click() } }
        container.addView(card.root)
    }

    private fun dayName(day: Int): String = resources.getStringArray(R.array.weekdays_short)[(day - 1).coerceIn(0, 6)]

    private fun checkIn() {
        if (!Network.isOnline(requireContext())) { requireContext().toast(R.string.need_internet_check_in); return }
        binding.btnCheckIn.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = vm.checkIn()
                requireContext().toast(getString(R.string.checked_in_result, r.streak, r.points))
                requireActivity().showNewBadges(binding.root, r.newBadges)
            } catch (e: Exception) {
                binding.btnCheckIn.isEnabled = true
                requireContext().toast(getString(R.string.error_with_reason, e.localizedMessage ?: ""))
            }
        }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val badges = vm.refresh()
                requireActivity().showNewBadges(binding.root, badges)
            } catch (e: Exception) {
                requireContext().toast(R.string.offline_showing_cache)
            } finally {
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
