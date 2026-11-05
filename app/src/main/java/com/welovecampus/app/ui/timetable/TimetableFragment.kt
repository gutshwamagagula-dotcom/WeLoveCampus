package com.welovecampus.app.ui.timetable

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.welovecampus.app.AppContainer
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.TimetableSlotEntity
import com.welovecampus.app.databinding.FragmentTimetableBinding
import com.welovecampus.app.databinding.ItemClassSlotBinding
import com.welovecampus.app.ui.common.VmFactory
import com.welovecampus.app.util.TimetableLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the selected weekday and exposes that day's classes from Room. */
class TimetableViewModel(c: AppContainer) : ViewModel() {
    val selectedDay = MutableStateFlow(TimetableLogic.todayIndex().let { if (it > 5) 1 else it })

    val classes: StateFlow<List<TimetableSlotEntity>> =
        combine(c.campusRepo.observeTimetable(), selectedDay) { all, day -> all.filter { it.dayOfWeek == day } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Weekly timetable, Monday to Friday (Requirement 2.2). Works offline from the Room cache. */
class TimetableFragment : Fragment() {

    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    private val vm: TimetableViewModel by viewModels { VmFactory(requireContext().container) }
    private val adapter = SlotAdapter { showDetails(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        val days = resources.getStringArray(R.array.weekdays_short).take(5)
        days.forEach { binding.dayTabs.addTab(binding.dayTabs.newTab().setText(it)) }
        binding.dayTabs.getTabAt(vm.selectedDay.value - 1)?.select()
        binding.dayTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { vm.selectedDay.value = tab.position + 1 }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.classes.collect {
                    adapter.submitList(it)
                    binding.emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showDetails(slot: TimetableSlotEntity) {
        val isLink = slot.room.startsWith("http")
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${slot.moduleCode} – ${slot.moduleName}")
            .setMessage(getString(R.string.class_details, slot.startTime, slot.endTime, slot.lecturer, slot.room))
            .setPositiveButton(android.R.string.ok, null)
        if (isLink) {
            dialog.setNeutralButton(R.string.open_link) { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(slot.room))) }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SlotAdapter(private val onClick: (TimetableSlotEntity) -> Unit) :
    ListAdapter<TimetableSlotEntity, SlotAdapter.VH>(DIFF) {

    class VH(val b: ItemClassSlotBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemClassSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        with(holder.b) {
            startTime.text = s.startTime
            endTime.text = s.endTime
            moduleCode.text = s.moduleCode
            moduleName.text = s.moduleName
            lecturer.text = s.lecturer
            room.text = s.room
            root.setOnClickListener { onClick(s) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TimetableSlotEntity>() {
            override fun areItemsTheSame(a: TimetableSlotEntity, b: TimetableSlotEntity) = a.slotId == b.slotId
            override fun areContentsTheSame(a: TimetableSlotEntity, b: TimetableSlotEntity) = a == b
        }
    }
}
