package com.welovecampus.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.databinding.ActivityRegisterBinding
import com.welovecampus.app.ui.common.LocaleHelper
import com.welovecampus.app.ui.common.setupToolbar
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.ui.main.MainActivity
import kotlinx.coroutines.launch

/** Creates an institution (email + password) account. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.create_account)

        binding.btnRegister.setOnClickListener { register() }
    }

    private fun register() {
        val name = binding.inputName.textValue()
        val email = binding.inputEmail.textValue()
        val password = binding.inputPassword.textValue()
        val confirm = binding.inputConfirm.textValue()

        // Validation with a message under each field
        binding.layoutName.error = if (name.length < 2) getString(R.string.error_name_required) else null
        binding.layoutEmail.error = if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.error_invalid_email) else null
        binding.layoutPassword.error = if (password.length < 6) getString(R.string.error_password_short) else null
        binding.layoutConfirm.error = if (confirm != password) getString(R.string.error_password_mismatch) else null
        if (listOf(binding.layoutName, binding.layoutEmail, binding.layoutPassword, binding.layoutConfirm).any { it.error != null }) return

        binding.progress.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false
        lifecycleScope.launch {
            try {
                container.authRepo.register(name, email, password)
                runCatching { container.bootstrapAfterLogin(LocaleHelper.current()) }
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                binding.btnRegister.isEnabled = true
                toast(getString(R.string.error_register_failed, e.localizedMessage ?: ""))
            }
        }
    }
}
