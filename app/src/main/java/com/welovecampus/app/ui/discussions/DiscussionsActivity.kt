package com.welovecampus.app.ui.discussions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.repo.DiscussionThread
import com.welovecampus.app.databinding.ActivityDiscussionsBinding
import com.welovecampus.app.databinding.DialogNewThreadBinding
import com.welovecampus.app.databinding.ItemThreadBinding
import com.welovecampus.app.ui.auth.textValue
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Module-scoped boards (Requirement 2.6): the student only sees chips for the
 * modules they are enrolled in, and each board updates live.
 */
class DiscussionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscussionsBinding
    private val adapter = ThreadAdapter { startActivity(ThreadDetailActivity.intent(this, it.id)) }
    private var selectedModule: String? = null
    private var listenJob: Job? = null
    private var authorName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscussionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.title_discussions)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fabNewThread.setOnClickListener { newThreadDialog() }

        lifecycleScope.launch {
            val profile = container.profileRepo.cachedProfile()
            authorName = profile?.fullName ?: getString(R.string.student)
            val modules = profile?.moduleList().orEmpty()
            if (modules.isEmpty()) {
                binding.emptyText.setText(R.string.no_modules_enrolled)
                binding.emptyText.visibility = View.VISIBLE
                binding.fabNewThread.hide()
                return@launch
            }
            modules.forEachIndexed { i, code ->
                val chip = Chip(this@DiscussionsActivity).apply {
                    text = code
                    isCheckable = true
                    id = View.generateViewId()
                    setOnClickListener { selectModule(code) }
                }
                binding.moduleChips.addView(chip)
                if (i == 0) chip.isChecked = true
            }
            selectModule(savedInstanceState?.getString(KEY_MODULE) ?: modules.first())
        }
    }

    private fun selectModule(code: String) {
        selectedModule = code
        binding.boardTitle.text = getString(R.string.board_for, code)
        for (i in 0 until binding.moduleChips.childCount) {
            (binding.moduleChips.getChildAt(i) as? Chip)?.let { it.isChecked = it.text == code }
        }
        listenJob?.cancel()
        listenJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.discussionRepo.observeThreads(code).collect { threads ->
                    adapter.submitList(threads)
                    binding.emptyText.setText(R.string.no_threads)
                    binding.emptyText.visibility = if (threads.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun newThreadDialog() {
        val module = selectedModule ?: return
        val d = DialogNewThreadBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.new_thread_in, module))
            .setView(d.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.post) { _, _ ->
                val title = d.inputTitle.textValue()
                val body = d.inputBody.textValue()
                if (title.isEmpty()) { toast(R.string.error_title_required); return@setPositiveButton }
                container.discussionRepo.createThread(module, title, body, authorName)
                lifecycleScope.launch {
                    showNewBadges(binding.root, container.discussionRepo.rewardPost(module))
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedModule?.let { outState.putString(KEY_MODULE, it) }
    }

    companion object { private const val KEY_MODULE = "module" }
}

class ThreadAdapter(private val onClick: (DiscussionThread) -> Unit) :
    ListAdapter<DiscussionThread, ThreadAdapter.VH>(DIFF) {

    class VH(val b: ItemThreadBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemThreadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            title.text = t.title
            body.text = t.body
            body.visibility = if (t.body.isBlank()) View.GONE else View.VISIBLE
            meta.text = ctx.getString(R.string.thread_meta, t.authorName, DateUtils.formatDateTime(t.createdAt))
            replies.text = ctx.resources.getQuantityString(R.plurals.replies, t.replyCount, t.replyCount)
            root.setOnClickListener { onClick(t) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DiscussionThread>() {
            override fun areItemsTheSame(a: DiscussionThread, b: DiscussionThread) = a.id == b.id
            override fun areContentsTheSame(a: DiscussionThread, b: DiscussionThread) = a == b
        }
    }
}
