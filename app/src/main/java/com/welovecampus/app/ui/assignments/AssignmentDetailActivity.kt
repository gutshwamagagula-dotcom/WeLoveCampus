package com.welovecampus.app.ui.assignments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.AssignmentEntity
import com.welovecampus.app.databinding.ActivityAssignmentDetailBinding
import com.welovecampus.app.sync.SyncWorker
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.util.AssignmentStatus
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Network
import com.welovecampus.app.util.PriorityCalculator
import kotlinx.coroutines.launch

/** Full assignment instructions + "Mark as submitted" (works offline). */
class AssignmentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssignmentDetailBinding
    private var current: AssignmentEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssignmentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.assignment_details)

        val id = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        binding.btnSubmit.setOnClickListener { confirmSubmit() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.campusRepo.observeAssignment(id).collect { a ->
                    if (a == null) { finish(); return@collect }
                    current = a
                    render(a)
                }
            }
        }
    }

    private fun render(a: AssignmentEntity) {
        binding.title.text = a.title
        binding.moduleCode.text = a.moduleCode
        binding.dueDate.text = getString(R.string.due_on, DateUtils.formatDateTime(a.dueDate))
        binding.description.text = a.description.ifBlank { getString(R.string.no_description) }

        val status = PriorityCalculator.statusFor(a)
        binding.status.text = getString(
            when (status) {
                AssignmentStatus.OPEN -> R.string.tab_open
                AssignmentStatus.SUBMITTED -> R.string.tab_submitted
                AssignmentStatus.MISSING -> R.string.tab_missing
            }
        )
        if (status == AssignmentStatus.OPEN) {
            val p = PriorityCalculator.priorityFor(a.dueDate)
            binding.priorityChip.visibility = View.VISIBLE
            binding.priorityChip.text = getString(AssignmentAdapter.priorityLabel(p))
            binding.priorityChip.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, AssignmentAdapter.priorityColor(p)))
        } else binding.priorityChip.visibility = View.GONE

        binding.btnSubmit.visibility = if (a.submitted) View.GONE else View.VISIBLE
        binding.submittedInfo.visibility = if (a.submitted) View.VISIBLE else View.GONE
        a.submittedAt?.let {
            binding.submittedInfo.text = getString(
                if (a.statusSynced) R.string.submitted_on else R.string.submitted_on_pending,
                DateUtils.formatDateTime(it)
            )
        }
    }

    private fun confirmSubmit() {
        val a = current ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mark_submitted)
            .setMessage(getString(R.string.mark_submitted_confirm, a.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.mark_submitted) { _, _ -> submit(a) }
            .show()
    }

    private fun submit(a: AssignmentEntity) {
        lifecycleScope.launch {
            container.campusRepo.markSubmitted(a.assignmentId)
            if (Network.isOnline(this@AssignmentDetailActivity)) {
                try {
                    val badges = container.campusRepo.pushPendingSubmissions()
                    toast(R.string.submission_saved)
                    showNewBadges(binding.root, badges)
                } catch (e: Exception) {
                    SyncWorker.syncNow(this@AssignmentDetailActivity)
                    toast(R.string.saved_offline_will_sync)
                }
            } else {
                SyncWorker.syncNow(this@AssignmentDetailActivity)   // waits for a connection
                toast(R.string.saved_offline_will_sync)
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "assignment_id"
        fun intent(context: Context, id: String) =
            Intent(context, AssignmentDetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
