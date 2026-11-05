package com.welovecampus.app.ui.assignments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.google.android.material.tabs.TabLayout
import com.welovecampus.app.AppContainer
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.AssignmentEntity
import com.welovecampus.app.databinding.FragmentAssignmentsBinding
import com.welovecampus.app.databinding.ItemAssignmentBinding
import com.welovecampus.app.ui.common.VmFactory
import com.welovecampus.app.util.AssignmentStatus
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Priority
import com.welovecampus.app.util.PriorityCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssignmentsState(
    val items: List<AssignmentEntity> = emptyList(),
    val counts: Map<AssignmentStatus, Int> = emptyMap()
)

class AssignmentsViewModel(c: AppContainer) : ViewModel() {
    val tab = MutableStateFlow(AssignmentStatus.OPEN)

    val state: StateFlow<AssignmentsState> = combine(c.campusRepo.observeAssignments(), tab) { all, t ->
        val now = System.currentTimeMillis()
        val grouped = all.groupBy { PriorityCalculator.statusFor(it, now) }
        AssignmentsState(
            items = grouped[t].orEmpty().let { list ->
                if (t == AssignmentStatus.OPEN) list.sortedBy { it.dueDate } else list.sortedByDescending { it.dueDate }
            },
            counts = AssignmentStatus.entries.associateWith { grouped[it]?.size ?: 0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssignmentsState())
}

/** Open / Submitted / Missing tabs with automatic priority (Requirement 2.3). */
class AssignmentsFragment : Fragment() {

    private var _binding: FragmentAssignmentsBinding? = null
    private val binding get() = _binding!!
    private val vm: AssignmentsViewModel by viewModels { VmFactory(requireContext().container) }
    private val adapter = AssignmentAdapter { startActivity(AssignmentDetailActivity.intent(requireContext(), it.assignmentId)) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAssignmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        listOf(R.string.tab_open, R.string.tab_submitted, R.string.tab_missing).forEach {
            binding.statusTabs.addTab(binding.statusTabs.newTab().setText(it))
        }
        binding.statusTabs.getTabAt(vm.tab.value.ordinal)?.select()
        binding.statusTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { vm.tab.value = AssignmentStatus.entries[tab.position] }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    adapter.submitList(s.items)
                    binding.emptyText.visibility = if (s.items.isEmpty()) View.VISIBLE else View.GONE
                    val labels = listOf(R.string.tab_open, R.string.tab_submitted, R.string.tab_missing)
                    AssignmentStatus.entries.forEachIndexed { i, st ->
                        binding.statusTabs.getTabAt(i)?.text = "${getString(labels[i])} (${s.counts[st] ?: 0})"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AssignmentAdapter(private val onClick: (AssignmentEntity) -> Unit) :
    ListAdapter<AssignmentEntity, AssignmentAdapter.VH>(DIFF) {

    class VH(val b: ItemAssignmentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAssignmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            title.text = a.title
            moduleCode.text = a.moduleCode
            due.text = ctx.getString(R.string.due_on, DateUtils.formatDateTime(a.dueDate))
            val status = PriorityCalculator.statusFor(a)
            if (status == AssignmentStatus.OPEN) {
                val p = PriorityCalculator.priorityFor(a.dueDate)
                priorityChip.visibility = View.VISIBLE
                priorityChip.text = ctx.getString(priorityLabel(p))
                priorityChip.chipBackgroundColor =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, priorityColor(p)))
            } else {
                priorityChip.visibility = View.GONE
            }
            pendingSync.visibility = if (!a.statusSynced) View.VISIBLE else View.GONE
            root.setOnClickListener { onClick(a) }
        }
    }

    companion object {
        fun priorityLabel(p: Priority) = when (p) {
            Priority.HIGH -> R.string.priority_high
            Priority.MEDIUM -> R.string.priority_medium
            Priority.LOW -> R.string.priority_low
        }
        fun priorityColor(p: Priority) = when (p) {
            Priority.HIGH -> R.color.priority_high
            Priority.MEDIUM -> R.color.priority_medium
            Priority.LOW -> R.color.priority_low
        }
        val DIFF = object : DiffUtil.ItemCallback<AssignmentEntity>() {
            override fun areItemsTheSame(a: AssignmentEntity, b: AssignmentEntity) = a.assignmentId == b.assignmentId
            override fun areContentsTheSame(a: AssignmentEntity, b: AssignmentEntity) = a == b
        }
    }
}
