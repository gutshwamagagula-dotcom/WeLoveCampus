package com.welovecampus.app.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
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
import com.google.android.material.chip.Chip
import com.welovecampus.app.AppContainer
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.NoteEntity
import com.welovecampus.app.data.local.SyncStatus
import com.welovecampus.app.databinding.FragmentNotesBinding
import com.welovecampus.app.databinding.ItemNoteBinding
import com.welovecampus.app.ui.common.VmFactory
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesState(
    val notes: List<NoteEntity> = emptyList(),
    val modules: List<String> = emptyList(),
    val pending: Int = 0
)

class NotesViewModel(private val c: AppContainer) : ViewModel() {
    val moduleFilter = MutableStateFlow<String?>(null)   // null = all modules
    val search = MutableStateFlow("")

    val state: StateFlow<NotesState> = combine(
        c.notesRepo.observeNotes(), c.notesRepo.observePendingCount(), moduleFilter, search
    ) { notes, pending, module, query ->
        val filtered = notes
            .filter { module == null || it.moduleCode == module }
            .filter {
                query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) || it.tag.contains(query, true)
            }
        NotesState(filtered, notes.map { it.moduleCode }.filter { it.isNotBlank() }.distinct().sorted(), pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    suspend fun syncNow() = c.syncEverything()
}

/** Offline notes list with per-note sync status (Requirement 2.4). */
class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private val vm: NotesViewModel by viewModels { VmFactory(requireContext().container) }
    private val adapter = NoteAdapter { startActivity(NoteEditorActivity.intent(requireContext(), it.noteId)) }
    private var shownModules: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.fabAdd.setOnClickListener { startActivity(NoteEditorActivity.intent(requireContext(), null)) }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.inputSearch.doAfterTextChanged { vm.search.value = it?.toString().orEmpty() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun render(s: NotesState) {
        adapter.submitList(s.notes)
        binding.emptyText.visibility = if (s.notes.isEmpty()) View.VISIBLE else View.GONE
        binding.syncBanner.visibility = if (s.pending > 0) View.VISIBLE else View.GONE
        binding.syncBannerText.text = resources.getQuantityString(R.plurals.notes_pending, s.pending, s.pending)
        if (s.modules != shownModules) buildModuleChips(s.modules)
    }

    /** "All" + one chip per module that has notes. */
    private fun buildModuleChips(modules: List<String>) {
        shownModules = modules
        val group = binding.moduleChips
        group.removeAllViews()
        fun chip(label: String, module: String?) = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = vm.moduleFilter.value == module
            setOnClickListener { vm.moduleFilter.value = module; isChecked = true }
        }
        group.addView(chip(getString(R.string.all), null))
        modules.forEach { group.addView(chip(it, it)) }
    }

    private fun syncNow() {
        if (!Network.isOnline(requireContext())) { requireContext().toast(R.string.offline_notes_safe); return }
        binding.btnSyncNow.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val badges = vm.syncNow()
                requireContext().toast(R.string.sync_complete)
                requireActivity().showNewBadges(binding.root, badges)
            } catch (e: Exception) {
                requireContext().toast(getString(R.string.error_with_reason, e.localizedMessage ?: ""))
            } finally {
                _binding?.btnSyncNow?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        shownModules = emptyList()
    }
}

class NoteAdapter(private val onClick: (NoteEntity) -> Unit) : ListAdapter<NoteEntity, NoteAdapter.VH>(DIFF) {

    class VH(val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            title.text = n.title.ifBlank { ctx.getString(R.string.untitled_note) }
            preview.text = n.content
            meta.text = listOf(n.moduleCode, n.tag.takeIf { it.isNotBlank() }?.let { "#$it" }, DateUtils.formatDateTime(n.lastModified))
                .filter { !it.isNullOrBlank() }.joinToString(" • ")
            val synced = n.syncStatus == SyncStatus.SYNCED
            syncStatus.text = ctx.getString(if (synced) R.string.status_synced else R.string.status_pending)
            syncStatus.setTextColor(ContextCompat.getColor(ctx, if (synced) R.color.synced else R.color.pending))
            syncStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_upload, 0, 0, 0
            )
            root.setOnClickListener { onClick(n) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(a: NoteEntity, b: NoteEntity) = a.noteId == b.noteId
            override fun areContentsTheSame(a: NoteEntity, b: NoteEntity) = a == b
        }
    }
}
