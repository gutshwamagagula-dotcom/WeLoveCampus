package com.welovecampus.app.ui.discussions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.repo.DiscussionReply
import com.welovecampus.app.databinding.ActivityThreadDetailBinding
import com.welovecampus.app.databinding.ItemReplyBinding
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.util.DateUtils
import kotlinx.coroutines.launch

/** One thread with its live replies and a reply box. */
class ThreadDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThreadDetailBinding
    private val adapter = ReplyAdapter(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
    private var moduleCode = ""
    private var authorName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThreadDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.thread)

        val threadId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnSend.setOnClickListener { send(threadId) }

        lifecycleScope.launch {
            authorName = container.profileRepo.cachedProfile()?.fullName ?: getString(R.string.student)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.discussionRepo.observeThread(threadId).collect { t ->
                        if (t == null) return@collect
                        moduleCode = t.moduleCode
                        supportActionBar?.subtitle = t.moduleCode
                        binding.threadTitle.text = t.title
                        binding.threadBody.text = t.body
                        binding.threadMeta.text = getString(R.string.thread_meta, t.authorName, DateUtils.formatDateTime(t.createdAt))
                    }
                }
                launch {
                    container.discussionRepo.observeReplies(threadId).collect { replies ->
                        adapter.submitList(replies) {
                            if (replies.isNotEmpty()) binding.recycler.scrollToPosition(replies.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun send(threadId: String) {
        val text = binding.inputReply.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        container.discussionRepo.reply(threadId, text, authorName)
        binding.inputReply.setText("")
        if (moduleCode.isNotEmpty()) lifecycleScope.launch {
            showNewBadges(binding.root, container.discussionRepo.rewardPost(moduleCode))
        }
    }

    companion object {
        private const val EXTRA_ID = "thread_id"
        fun intent(context: Context, id: String) = Intent(context, ThreadDetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

class ReplyAdapter(private val myUid: String) : ListAdapter<DiscussionReply, ReplyAdapter.VH>(DIFF) {

    class VH(val b: ItemReplyBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemReplyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            author.text = if (r.authorUid == myUid) ctx.getString(R.string.you) else r.authorName
            body.text = r.body
            time.text = DateUtils.formatDateTime(r.createdAt)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DiscussionReply>() {
            override fun areItemsTheSame(a: DiscussionReply, b: DiscussionReply) = a.id == b.id
            override fun areContentsTheSame(a: DiscussionReply, b: DiscussionReply) = a == b
        }
    }
}
