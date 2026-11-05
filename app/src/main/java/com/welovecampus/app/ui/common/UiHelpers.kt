package com.welovecampus.app.ui.common

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.welovecampus.app.AppContainer
import com.welovecampus.app.R
import com.welovecampus.app.data.repo.Badge
import com.welovecampus.app.ui.assignments.AssignmentsViewModel
import com.welovecampus.app.ui.home.HomeViewModel
import com.welovecampus.app.ui.notes.NotesViewModel
import com.welovecampus.app.ui.timetable.TimetableViewModel

fun Context.toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/** Every secondary screen has the red toolbar with a back arrow. */
fun AppCompatActivity.setupToolbar(toolbar: MaterialToolbar, @StringRes title: Int, showBack: Boolean = true) {
    setSupportActionBar(toolbar)
    supportActionBar?.title = getString(title)
    supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
    toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
}

/** Celebrates newly unlocked badges. */
fun Activity.showNewBadges(anchor: View, badges: List<Badge>) {
    if (badges.isEmpty()) return
    val names = badges.joinToString(", ") { getString(it.title) }
    Snackbar.make(anchor, getString(R.string.badge_unlocked, names), Snackbar.LENGTH_LONG).show()
}

/** Language switching without restarting the app (Requirement 2.10). */
object LocaleHelper {
    val SUPPORTED = listOf("en", "zu", "af")

    fun current(): String {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val first = tags.split(",").firstOrNull().orEmpty().take(2)
        return if (first in SUPPORTED) first else "en"
    }

    fun apply(tag: String) {
        if (tag == current() && !AppCompatDelegate.getApplicationLocales().isEmpty) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}

/** Builds the ViewModels with the shared repositories. */
class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(c)
        modelClass.isAssignableFrom(TimetableViewModel::class.java) -> TimetableViewModel(c)
        modelClass.isAssignableFrom(AssignmentsViewModel::class.java) -> AssignmentsViewModel(c)
        modelClass.isAssignableFrom(NotesViewModel::class.java) -> NotesViewModel(c)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}
