package com.welovecampus.app.ui.profile

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils as AndroidDateUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.data.local.ProfileEntity
import com.welovecampus.app.data.repo.Badge
import com.welovecampus.app.databinding.ActivityProfileBinding
import com.welovecampus.app.sync.SyncWorker
import com.welovecampus.app.ui.auth.LoginActivity
import com.welovecampus.app.ui.common.LocaleHelper
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.showNewBadges
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.util.DateUtils
import com.welovecampus.app.util.Network
import com.welovecampus.app.util.NotificationCategory
import com.welovecampus.app.util.StreakCalculator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Profile / Settings: language, notification toggles, modules, sync, badges, sign out.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var profile: ProfileEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.title_profile)

        setupLanguage()
        setupNotificationSwitches()

        binding.btnEditName.setOnClickListener { editName() }
        binding.btnModules.setOnClickListener { chooseModules() }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.btnReloadDemo.setOnClickListener { reloadDemo() }
        binding.btnSignOut.setOnClickListener { confirmSignOut() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(container.profileRepo.observeProfile(), container.notesRepo.observePendingCount()) { p, pending -> p to pending }
                    .collect { (p, pending) -> render(p, pending) }
            }
        }
    }

    private fun render(p: ProfileEntity?, pendingNotes: Int) {
        profile = p
        if (p == null) return
        binding.name.text = p.fullName
        binding.email.text = p.email
        binding.avatar.text = p.fullName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        val streak = StreakCalculator.displayStreak(p.lastCheckInDay, p.streakDays, DateUtils.dayKey(), DateUtils.yesterdayKey())
        binding.stats.text = getString(R.string.profile_stats, p.points, streak)
        binding.modulesSummary.text = p.moduleList().takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: getString(R.string.no_modules_enrolled)

        val last = container.prefs.lastSyncedAt
        binding.syncStatus.text = getString(
            R.string.sync_status,
            if (last == 0L) getString(R.string.never) else AndroidDateUtils.getRelativeTimeSpanString(last).toString(),
            pendingNotes
        )

        // Badges: earned ones in gold, locked ones greyed out
        binding.badgeChips.removeAllViews()
        val earned = p.badgeList().toSet()
        Badge.entries.forEach { b ->
            val has = b.name in earned
            binding.badgeChips.addView(Chip(this).apply {
                text = getString(b.title)
                isClickable = true
                chipIcon = ContextCompat.getDrawable(context, if (has) R.drawable.ic_star else R.drawable.ic_lock)
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, if (has) R.color.gold else R.color.grey_light)
                )
                alpha = if (has) 1f else 0.6f
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@ProfileActivity)
                        .setTitle(b.title)
                        .setMessage(getString(b.description) + "\n\n" + getString(if (has) R.string.badge_earned else R.string.badge_locked))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            })
        }
    }

    // ---------------- Language ----------------
    private fun setupLanguage() {
        when (LocaleHelper.current()) {
            "zu" -> binding.radioZulu.isChecked = true
            "af" -> binding.radioAfrikaans.isChecked = true
            else -> binding.radioEnglish.isChecked = true
        }
        binding.languageGroup.setOnCheckedChangeListener { _, id ->
            val tag = when (id) {
                R.id.radioZulu -> "zu"
                R.id.radioAfrikaans -> "af"
                else -> "en"
            }
            if (tag == LocaleHelper.current()) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                container.profileRepo.setLanguage(tag)
                LocaleHelper.apply(tag)   // activity is recreated in the new language; session is kept
            }
        }
    }

    // ---------------- Notifications ----------------
    private fun setupNotificationSwitches() {
        val prefs = container.prefs
        val map: List<Pair<MaterialSwitch, String>> = listOf(
            binding.switchAssignments to NotificationCategory.ASSIGNMENTS,
            binding.switchTimetable to NotificationCategory.TIMETABLE,
            binding.switchAnnouncements to NotificationCategory.ANNOUNCEMENTS,
            binding.switchEvents to NotificationCategory.EVENTS,
            binding.switchDiscussions to NotificationCategory.DISCUSSIONS,
            binding.switchEmergency to NotificationCategory.EMERGENCY
        )
        map.forEach { (sw, category) ->
            sw.isChecked = prefs.isCategoryEnabled(category)
            if (category == NotificationCategory.EMERGENCY) {
                sw.isEnabled = false        // Requirement 2.9: emergency alerts cannot be disabled
            } else {
                sw.setOnCheckedChangeListener { _, checked ->
                    prefs.setCategoryEnabled(category, checked)
                    lifecycleScope.launch { container.profileRepo.saveNotificationPrefs() }
                }
            }
        }
    }

    // ---------------- Modules ----------------
    private fun chooseModules() {
        lifecycleScope.launch {
            val all = container.campusRepo.allModules()
            if (all.isEmpty()) { toast(R.string.no_modules_available); return@launch }
            val enrolled = profile?.moduleList().orEmpty().toMutableSet()
            val labels = all.map { "${it.code} – ${it.name}" }.toTypedArray()
            val checked = all.map { it.code in enrolled }.toBooleanArray()
            MaterialAlertDialogBuilder(this@ProfileActivity)
                .setTitle(R.string.my_modules)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    if (isChecked) enrolled += all[which].code else enrolled -= all[which].code
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    lifecycleScope.launch {
                        container.profileRepo.setEnrolledModules(all.map { it.code }.filter { it in enrolled })
                        syncNow()
                    }
                }
                .show()
        }
    }

    private fun editName() {
        val layout = TextInputLayout(this).apply { setPadding(48, 16, 48, 0) }
        val input = TextInputEditText(layout.context).apply { setText(profile?.fullName.orEmpty()) }
        layout.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_name)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.length >= 2) lifecycleScope.launch { container.profileRepo.setFullName(name) }
            }
            .show()
    }

    // ---------------- Sync ----------------
    private fun syncNow() {
        if (!Network.isOnline(this)) {
            SyncWorker.syncNow(this)
            toast(R.string.offline_sync_queued); return
        }
        binding.syncProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val badges = container.syncEverything()
                toast(R.string.sync_complete)
                showNewBadges(binding.root, badges)
            } catch (e: Exception) {
                toast(getString(R.string.error_with_reason, e.localizedMessage ?: ""))
            } finally {
                binding.syncProgress.visibility = View.GONE
                profile?.let { render(it, 0) }
            }
        }
    }

    private fun reloadDemo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reload_demo)
            .setMessage(R.string.reload_demo_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reload) { _, _ ->
                lifecycleScope.launch {
                    try {
                        container.seeder.seed()
                        container.syncEverything()
                        toast(R.string.demo_reloaded)
                    } catch (e: Exception) {
                        toast(getString(R.string.error_with_reason, e.localizedMessage ?: ""))
                    }
                }
            }
            .show()
    }

    // ---------------- Sign out ----------------
    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                lifecycleScope.launch {
                    container.authRepo.signOut(this@ProfileActivity)
                    startActivity(Intent(this@ProfileActivity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }
            }
            .show()
    }
}
