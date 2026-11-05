package com.welovecampus.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.welovecampus.app.R
import com.welovecampus.app.container
import com.welovecampus.app.databinding.ActivityLoginBinding
import com.welovecampus.app.ui.common.LocaleHelper
import com.welovecampus.app.ui.common.toast
import com.welovecampus.app.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Entry screen: "Continue with Google", institution (email/password) sign-in,
 * register, forgot password and the language picker.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already signed in? Skip straight to the Dashboard.
        if (container.authRepo.isSignedIn) {
            openMain(); return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoogle.setOnClickListener { signInWithGoogle() }
        binding.btnSignIn.setOnClickListener { signInWithEmail() }
        binding.btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnForgot.setOnClickListener { forgotPassword() }
        binding.inputEmail.doAfterTextChanged { binding.layoutEmail.error = null }
        binding.inputPassword.doAfterTextChanged { binding.layoutPassword.error = null }

        // Language picker - switches immediately without restarting the app
        binding.langEnglish.setOnClickListener { LocaleHelper.apply("en") }
        binding.langZulu.setOnClickListener { LocaleHelper.apply("zu") }
        binding.langAfrikaans.setOnClickListener { LocaleHelper.apply("af") }
        highlightLanguage()
    }

    private fun highlightLanguage() {
        val current = LocaleHelper.current()
        listOf(binding.langEnglish to "en", binding.langZulu to "zu", binding.langAfrikaans to "af").forEach { (v, tag) ->
            v.alpha = if (tag == current) 1f else 0.55f
        }
    }

    // ---------------- Google (Credential Manager) ----------------

    private fun signInWithGoogle() {
        // default_web_client_id is generated from google-services.json once Google sign-in
        // is enabled in the Firebase console. Looked up at runtime so the project still
        // builds before that step is done.
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resId == 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.google_not_configured_title)
                .setMessage(R.string.google_not_configured_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)   // show every Google account on the phone
            .setServerClientId(getString(resId))
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    container.authRepo.signInWithGoogleIdToken(idToken)
                    finishLogin()
                } else {
                    setLoading(false)
                    toast(R.string.error_google_sign_in)
                }
            } catch (e: GetCredentialCancellationException) {
                setLoading(false)            // user closed the sheet - not an error
            } catch (e: NoCredentialException) {
                setLoading(false)
                toast(R.string.error_no_google_account)
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Credential Manager", e)
                setLoading(false)
                toast(getString(R.string.error_with_reason, e.message ?: ""))
            } catch (e: Exception) {
                Log.w(TAG, "Google sign-in", e)
                setLoading(false)
                toast(getString(R.string.error_with_reason, e.message ?: ""))
            }
        }
    }

    // ---------------- Institution (email + password) ----------------

    private fun signInWithEmail() {
        val email = binding.inputEmail.textValue()
        val password = binding.inputPassword.textValue()
        var ok = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_invalid_email); ok = false
        }
        if (password.length < 6) {
            binding.layoutPassword.error = getString(R.string.error_password_short); ok = false
        }
        if (!ok) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                container.authRepo.signInWithEmail(email, password)
                finishLogin()
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.error_sign_in_failed, e.localizedMessage ?: ""))
            }
        }
    }

    private fun forgotPassword() {
        val email = binding.inputEmail.textValue()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_enter_email_first)
            return
        }
        lifecycleScope.launch {
            try {
                container.authRepo.sendPasswordReset(email)
                toast(R.string.reset_email_sent)
            } catch (e: Exception) {
                toast(getString(R.string.error_with_reason, e.localizedMessage ?: ""))
            }
        }
    }

    /** Creates the profile, seeds demo data on an empty project and does the first sync. */
    private suspend fun finishLogin() {
        binding.loadingText.setText(R.string.setting_up)
        try {
            container.bootstrapAfterLogin(LocaleHelper.current())
        } catch (e: Exception) {
            Log.w(TAG, "bootstrap", e)
            toast(getString(R.string.error_setup, e.localizedMessage ?: ""))
        }
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loadingText.setText(R.string.signing_in)
        binding.btnGoogle.isEnabled = !loading
        binding.btnSignIn.isEnabled = !loading
    }

    companion object { private const val TAG = "LoginActivity" }
}

fun TextInputEditText.textValue(): String = text?.toString()?.trim().orEmpty()
