package com.welovecampus.app.ui.events

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.EventEntity
import com.welovecampus.app.databinding.ActivityEventsBinding
import com.welovecampus.app.databinding.ItemCalendarDayBinding
import com.welovecampus.app.databinding.ItemEventBinding
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A cell in the month grid; dayKey is null for the blank cells before the 1st. */
data class CalendarCell(val dayOfMonth: Int?, val dayKey: String?, val hasEvent: Boolean, val isToday: Boolean, val isSelected: Boolean)

/**
 * Monthly calendar with event dots + list underneath (Requirement 2.8).
 * Tap a date to see that day's events; tap it again to go back to "Upcoming".
 * Past events are never shown in the Upcoming list.
 */
class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private val month = MutableStateFlow(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
    private val selectedDay = MutableStateFlow<String?>(null)
    private val dayAdapter = DayAdapter { cell -> cell.dayKey?.let { selectedDay.value = if (selectedDay.value == it) null else it } }
    private val eventAdapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.title_events)

        binding.calendarGrid.layoutManager = GridLayoutManager(this, 7)
        binding.calendarGrid.adapter = dayAdapter
        binding.eventList.layoutManager = LinearLayoutManager(this)
        binding.eventList.adapter = eventAdapter

        binding.btnPrev.setOnClickListener { shiftMonth(-1) }
        binding.btnNext.setOnClickListener { shiftMonth(1) }
        binding.chipShowUpcoming.setOnClickListener { selectedDay.value = null }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(container.campusRepo.observeEvents(), month, selectedDay) { events, m, sel ->
                    Triple(events, m, sel)
                }.collect { (events, m, sel) -> render(events, m, sel) }
            }
        }
    }

    private fun shiftMonth(by: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = month.value; add(Calendar.MONTH, by) }
        month.value = c.timeInMillis
    }

    private fun render(events: List<EventEntity>, monthStart: Long, selected: String?) {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        binding.monthLabel.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)

        // Build the grid: blank cells so the 1st lands under the right weekday (Monday first)
        val eventDays = events.map { DateUtils.dayKey(it.eventDate) }.toSet()
        val today = DateUtils.dayKey()
        val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = MutableList(offset) { CalendarCell(null, null, false, false, false) }
        for (d in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val key = DateUtils.dayKey(cal.timeInMillis)
            cells += CalendarCell(d, key, key in eventDays, key == today, key == selected)
        }
        dayAdapter.submitList(cells)

        // List under the grid
        val now = System.currentTimeMillis()
        val list = if (selected != null) {
            binding.listTitle.text = getString(R.string.events_on, DateUtils.formatDate(events.firstOrNull { DateUtils.dayKey(it.eventDate) == selected }?.eventDate ?: dayKeyToMillis(selected)))
            binding.chipShowUpcoming.visibility = View.VISIBLE
            events.filter { DateUtils.dayKey(it.eventDate) == selected }
        } else {
            binding.listTitle.setText(R.string.upcoming_events)
            binding.chipShowUpcoming.visibility = View.GONE
            events.filter { it.eventDate >= now }   // past events hidden automatically
        }
        eventAdapter.submitList(list)
        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun dayKeyToMillis(key: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)?.time ?: System.currentTimeMillis()
}

class DayAdapter(private val onClick: (CalendarCell) -> Unit) : ListAdapter<CalendarCell, DayAdapter.VH>(DIFF) {

    class VH(val b: ItemCalendarDayBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            dayNumber.text = c.dayOfMonth?.toString() ?: ""
            eventDot.visibility = if (c.hasEvent) View.VISIBLE else View.INVISIBLE
            dayNumber.background = when {
                c.isSelected -> ContextCompat.getDrawable(ctx, R.drawable.bg_day_selected)
                c.isToday -> ContextCompat.getDrawable(ctx, R.drawable.bg_day_today)
                else -> null
            }
            dayNumber.setTextColor(ContextCompat.getColor(ctx, if (c.isSelected) R.color.white else R.color.ink))
            root.isClickable = c.dayKey != null
            root.setOnClickListener { if (c.dayKey != null) onClick(c) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CalendarCell>() {
            override fun areItemsTheSame(a: CalendarCell, b: CalendarCell) = a.dayKey == b.dayKey && a.dayOfMonth == b.dayOfMonth
            override fun areContentsTheSame(a: CalendarCell, b: CalendarCell) = a == b
        }
    }
}

class EventAdapter : ListAdapter<EventEntity, EventAdapter.VH>(DIFF) {

    class VH(val b: ItemEventBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        val cal = Calendar.getInstance().apply { timeInMillis = e.eventDate }
        with(holder.b) {
            day.text = cal.get(Calendar.DAY_OF_MONTH).toString()
            month.text = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
            title.text = e.title
            details.text = "${DateUtils.formatTime(e.eventDate)} • ${e.location}"
            description.text = e.description
            description.visibility = if (e.description.isBlank()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<EventEntity>() {
            override fun areItemsTheSame(a: EventEntity, b: EventEntity) = a.eventId == b.eventId
            override fun areContentsTheSame(a: EventEntity, b: EventEntity) = a == b
        }
    }
}
