package com.welovecampus.app.ui.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.databinding.ActivityNoteEditorBinding
import com.welovecampus.app.sync.SyncWorker
import com.welovecampus.app.ui.auth.textValue
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.toast
import kotlinx.coroutines.launch

/**
 * Create / edit / delete a note. Everything is saved to Room immediately
 * (works in airplane mode) and a sync is queued for when the phone is online.
 */
class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private var noteId: String? = null
    private var original = ""   // snapshot used to detect unsaved changes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        noteId = intent.getStringExtra(EXTRA_ID)
        setupToolbar(binding.toolbar, if (noteId == null) R.string.new_note else R.string.edit_note)

        lifecycleScope.launch {
            // Module dropdown: enrolled modules + "General"
            val enrolled = container.profileRepo.cachedProfile()?.moduleList().orEmpty()
            val options = listOf(getString(R.string.general)) + enrolled
            binding.inputModule.setAdapter(ArrayAdapter(this@NoteEditorActivity, android.R.layout.simple_list_item_1, options))

            noteId?.let { id ->
                container.notesRepo.getNote(id)?.let { n ->
                    binding.inputTitle.setText(n.title)
                    binding.inputContent.setText(n.content)
                    binding.inputTag.setText(n.tag)
                    binding.inputModule.setText(n.moduleCode.ifBlank { getString(R.string.general) }, false)
                }
            } ?: binding.inputModule.setText(options.first(), false)
            original = snapshot()
        }

        // Back = auto-save if something changed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (snapshot() != original && !isBlank()) save() else finish()
            }
        })
    }

    private fun snapshot() = listOf(
        binding.inputTitle.textValue(), binding.inputContent.text?.toString().orEmpty(),
        binding.inputModule.text?.toString().orEmpty(), binding.inputTag.textValue()
    ).joinToString("|")

    private fun isBlank() = binding.inputTitle.textValue().isEmpty() && binding.inputContent.text.isNullOrBlank()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_editor, menu)
        menu.findItem(R.id.action_delete)?.isVisible = noteId != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_save -> { save(); true }
        R.id.action_delete -> { confirmDelete(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun save() {
        if (isBlank()) {
            binding.layoutTitle.error = getString(R.string.error_note_empty)
            return
        }
        val module = binding.inputModule.text?.toString().orEmpty().let { if (it == getString(R.string.general)) "" else it }
        lifecycleScope.launch {
            container.notesRepo.saveNote(
                noteId,
                binding.inputTitle.textValue(),
                binding.inputContent.text?.toString().orEmpty(),
                module,
                binding.inputTag.textValue().removePrefix("#")
            )
            SyncWorker.syncNow(this@NoteEditorActivity)   // uploads as soon as there is a connection
            toast(R.string.note_saved)
            finish()
        }
    }

    private fun confirmDelete() {
        val id = noteId ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_note)
            .setMessage(R.string.delete_note_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    container.notesRepo.deleteNote(id)
                    SyncWorker.syncNow(this@NoteEditorActivity)
                    toast(R.string.note_deleted)
                    finish()
                }
            }
            .show()
    }

    companion object {
        private const val EXTRA_ID = "note_id"
        fun intent(context: Context, noteId: String?) =
            Intent(context, NoteEditorActivity::class.java).apply { noteId?.let { putExtra(EXTRA_ID, it) } }
    }
}
