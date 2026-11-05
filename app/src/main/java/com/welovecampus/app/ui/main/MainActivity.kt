package com.welovecampus.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.databinding.ActivityMainBinding
import com.welovecampus.app.ui.assignments.AssignmentsFragment
import com.welovecampus.app.ui.auth.LoginActivity
import com.welovecampus.app.ui.home.HomeFragment
import com.welovecampus.app.ui.more.MoreFragment
import com.welovecampus.app.ui.notes.NotesFragment
import com.welovecampus.app.ui.profile.ProfileActivity
import com.welovecampus.app.ui.timetable.TimetableFragment

/**
 * Hosts the bottom navigation: Home / Timetable / Tasks / Notes / More.
 * Each tab is a Fragment swapped into the container.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!container.authRepo.isSignedIn) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId); true
        }
        if (savedInstanceState == null) {
            selectTab(intent.getIntExtra(EXTRA_OPEN_TAB, 0))
        } else {
            updateTitle(binding.bottomNav.selectedItemId)
        }
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_OPEN_TAB)) {
            selectTab(intent.getIntExtra(EXTRA_OPEN_TAB, 0))
        }
    }

    /** Public so the Home screen cards can jump to another tab. */
    fun selectTab(tab: Int) {
        val id = tabToMenuId(tab)
        // setting the already-selected item does not fire the listener, so show() explicitly
        binding.bottomNav.selectedItemId = id
        show(id)
    }

    private fun tabToMenuId(tab: Int) = when (tab) {
        1 -> R.id.nav_timetable
        2 -> R.id.nav_tasks
        3 -> R.id.nav_notes
        4 -> R.id.nav_more
        else -> R.id.nav_home
    }

    private fun show(menuId: Int) {
        val tag = "tab_$menuId"
        if (supportFragmentManager.findFragmentByTag(tag) != null) { updateTitle(menuId); return }
        val fragment: Fragment = when (menuId) {
            R.id.nav_timetable -> TimetableFragment()
            R.id.nav_tasks -> AssignmentsFragment()
            R.id.nav_notes -> NotesFragment()
            R.id.nav_more -> MoreFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
        updateTitle(menuId)
    }

    private fun updateTitle(menuId: Int) {
        supportActionBar?.title = getString(
            when (menuId) {
                R.id.nav_timetable -> R.string.title_timetable
                R.id.nav_tasks -> R.string.title_assignments
                R.id.nav_notes -> R.string.title_notes
                R.id.nav_more -> R.string.title_more
                else -> R.string.title_dashboard
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
