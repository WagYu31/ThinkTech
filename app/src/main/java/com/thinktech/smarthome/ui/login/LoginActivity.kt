package com.thinktech.smarthome.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.os.CountDownTimer
import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.TuyaAuthRepository
import com.thinktech.smarthome.databinding.ActivityLoginBinding
import com.thinktech.smarthome.databinding.DialogForgotPasswordBinding
import com.thinktech.smarthome.ui.home.MainActivity

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = TuyaAuthRepository.PREFS_NAME
        private const val KEY_REMEMBER_ME = TuyaAuthRepository.KEY_REMEMBER_ME
        private const val KEY_SAVED_ACCOUNT = TuyaAuthRepository.KEY_SAVED_ACCOUNT
        private const val KEY_SAVED_PASSWORD = TuyaAuthRepository.KEY_SAVED_PASSWORD
    }

    private lateinit var binding: ActivityLoginBinding
    private val authRepo = TuyaAuthRepository()

    data class Country(
        val name: String,
        val code: String,
        val flag: String
    )

    private var selectedCountryCode = "62"
    private var selectedCountryFlag = "🇮🇩"

    private val countryList = listOf(
        Country("Indonesia", "62", "🇮🇩"),
        Country("Malaysia", "60", "🇲🇾"),
        Country("Singapore", "65", "🇸🇬"),
        Country("United States", "1", "🇺🇸"),
        Country("United Kingdom", "44", "🇬🇧"),
        Country("Japan", "81", "🇯🇵"),
        Country("Australia", "61", "🇦🇺"),
        Country("China", "86", "🇨🇳"),
        Country("Germany", "49", "🇩🇪"),
        Country("France", "33", "🇫🇷"),
        Country("South Korea", "82", "🇰🇷"),
        Country("Saudi Arabia", "966", "🇸🇦"),
        Country("United Arab Emirates", "971", "🇦🇪"),
        Country("Thailand", "66", "🇹🇭"),
        Country("Philippines", "63", "🇵🇭"),
        Country("Vietnam", "84", "🇻🇳"),
        Country("India", "91", "🇮🇳"),
        Country("Netherlands", "31", "🇳🇱"),
        Country("Canada", "1", "🇨🇦"),
        Country("Hong Kong", "852", "🇭🇰"),
        Country("Taiwan", "886", "🇹🇼"),
        Country("Turkey", "90", "🇹🇷"),
        Country("Brazil", "55", "🇧🇷"),
        Country("Egypt", "20", "🇪🇬"),
        Country("New Zealand", "64", "🇳🇿")
    )

    private var isLoginPasswordVisible = false
    private var isRegisterPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle hardware / gesture Back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.layoutLoginForm.visibility == View.VISIBLE ||
                    binding.layoutRegisterForm.visibility == View.VISIBLE -> {
                        showWelcomeScreen()
                    }
                    else -> finish()
                }
            }
        })

        // Check if already logged in
        if (authRepo.isTuyaSdkLoggedIn() || authRepo.isMarkedLoggedIn()) {
            navigateToHome()
            return
        }

        setupWelcomeListeners()
        setupLoginFormListeners()
        setupRegisterFormListeners()
        setupAgreementSection()
    }

    private fun setupWelcomeListeners() {
        // Primary: Sign In
        binding.btnWelcomeLogin.setOnClickListener {
            showLoginForm()
        }

        // Secondary: Create Account
        binding.btnWelcomeRegister.setOnClickListener {
            showRegisterForm()
        }

        // Tertiary: Explore as Guest
        binding.tvTryAsGuest.setOnClickListener {
            Toast.makeText(this, "Entering as Guest / Demo Mode", Toast.LENGTH_SHORT).show()
            navigateToHome()
        }
    }

    private fun setupLoginFormListeners() {
        // Back to Welcome
        binding.btnBackFromLogin.setOnClickListener {
            showWelcomeScreen()
        }

        // Switch to Sign Up
        binding.tvSwitchToRegister.setOnClickListener {
            showRegisterForm()
        }


        // Toggle Password Show/Hide
        binding.ivLoginTogglePassword.setOnClickListener {
            isLoginPasswordVisible = togglePasswordVisibility(
                binding.etLoginPassword,
                binding.ivLoginTogglePassword,
                isLoginPasswordVisible
            )
        }

        // Remember Me & Credential Persistence Setup
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isRememberMe = prefs.getBoolean(KEY_REMEMBER_ME, true)
        binding.cbRememberMe.isChecked = isRememberMe

        val creds = authRepo.getSavedCredentials()
        if (isRememberMe && creds != null) {
            val (savedAccount, savedPassword, savedCountry) = creds
            binding.etLoginAccount.setText(savedAccount)
            binding.etLoginPassword.setText(savedPassword)
            selectedCountryCode = savedCountry

            // Returning user with credentials -> directly present the login form
            showLoginForm()
        }

        binding.layoutRememberMe.setOnClickListener {
            binding.cbRememberMe.isChecked = !binding.cbRememberMe.isChecked
        }

        binding.cbRememberMe.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_REMEMBER_ME, false)
                    .remove(KEY_SAVED_ACCOUNT)
                    .remove(KEY_SAVED_PASSWORD)
                    .apply()
            }
        }

        // Forgot Password Dialog
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordBottomSheet()
        }

        // Submit Sign In
        binding.btnLoginSubmit.setOnClickListener {
            hideLoginError()

            if (!binding.cbLoginAgreement.isChecked) {
                showLoginError("Please agree to the Terms of Service and Privacy Policy.")
                return@setOnClickListener
            }

            val account = binding.etLoginAccount.text.toString().trim()
            val password = binding.etLoginPassword.text.toString().trim()

            if (account.isEmpty()) {
                showLoginError("Please enter your email address or phone number.")
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                showLoginError("Please enter your password.")
                return@setOnClickListener
            }

            setLoginLoading(true)

            if (account.contains("@")) {
                authRepo.loginWithEmail(
                    countryCode = selectedCountryCode,
                    email = account,
                    password = password,
                    onSuccess = { user ->
                        onLoginSuccess(account, password, user.nickName ?: user.username)
                    },
                    onError = { code, error ->
                        setLoginLoading(false)
                        showLoginError("Sign in failed ($code): $error")
                    }
                )
            } else {
                authRepo.loginWithPhone(
                    countryCode = selectedCountryCode,
                    phone = account,
                    password = password,
                    onSuccess = { user ->
                        onLoginSuccess(account, password, user.nickName ?: user.username)
                    },
                    onError = { code, error ->
                        setLoginLoading(false)
                        showLoginError("Sign in failed ($code): $error")
                    }
                )
            }
        }
    }

    private fun onLoginSuccess(account: String, password: String, displayName: String) {
        // Trigger Android Autofill Framework to prompt Google Password Manager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                getSystemService(AutofillManager::class.java)?.commit()
            } catch (e: Throwable) {
                // Safe fallback
            }
        }

        // Persist session and credentials based on Remember Me checkbox
        val isRemember = binding.cbRememberMe.isChecked
        val user = authRepo.getCurrentUser() ?: com.thingclips.smart.android.user.bean.User().apply {
            username = account
            nickName = displayName
        }
        authRepo.saveUserSession(
            user = user,
            account = account,
            password = password,
            countryCode = selectedCountryCode,
            rememberMe = isRemember
        )

        setLoginLoading(false)
        Toast.makeText(this, "Welcome back, $displayName!", Toast.LENGTH_SHORT).show()
        navigateToHome()
    }

    private fun setupRegisterFormListeners() {
        // Back to Welcome
        binding.btnBackFromRegister.setOnClickListener {
            showWelcomeScreen()
        }

        // Switch to Sign In
        binding.tvSwitchToLogin.setOnClickListener {
            showLoginForm()
        }

        // Country Code & Flag Picker
        binding.btnRegisterCountryPicker.setOnClickListener {
            showCountryPickerBottomSheet()
        }


        // Toggle Password Show/Hide
        binding.ivRegisterTogglePassword.setOnClickListener {
            isRegisterPasswordVisible = togglePasswordVisibility(
                binding.etRegisterPassword,
                binding.ivRegisterTogglePassword,
                isRegisterPasswordVisible
            )
        }

        // --- REAL-TIME VALIDATION WATCHERS ---

        // 1. Live Validation: Full Name
        binding.etRegisterName.doAfterTextChanged { text ->
            val name = text?.toString()?.trim().orEmpty()
            if (name.isEmpty() && binding.etRegisterName.hasFocus()) {
                binding.tvRegisterNameError.text = "Full name is required."
                binding.tvRegisterNameError.visibility = View.VISIBLE
            } else {
                binding.tvRegisterNameError.visibility = View.GONE
            }
        }

        // 2. Real-Time Phone Number Auto-Formatter with Hyphens (e.g. 857-7159-3522 or 0857-7159-3522)
        var isFormattingPhone = false
        binding.etRegisterPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormattingPhone || s == null) return

                val rawText = s.toString()
                val digits = rawText.filter { it.isDigit() }

                // Format: if starts with 0 -> 4-4-4 (0857-7159-3522), otherwise 3-4-4 (857-7159-3522)
                val sb = StringBuilder()
                val isZeroLeading = digits.startsWith("0")

                for (i in digits.indices) {
                    if (isZeroLeading) {
                        if ((i == 4 || i == 8) && i < 12) {
                            sb.append('-')
                        }
                    } else {
                        if ((i == 3 || i == 7) && i < 11) {
                            sb.append('-')
                        }
                    }
                    sb.append(digits[i])
                }
                val formatted = sb.toString()

                if (rawText != formatted) {
                    isFormattingPhone = true
                    // Compute new cursor position based on digits typed so far
                    val currentCursor = binding.etRegisterPhone.selectionStart
                    val digitsBeforeCursor = rawText.take(currentCursor).count { it.isDigit() }

                    s.replace(0, s.length, formatted)

                    var newCursor = 0
                    var digitsSeen = 0
                    for (char in formatted) {
                        if (digitsSeen >= digitsBeforeCursor) break
                        if (char.isDigit()) digitsSeen++
                        newCursor++
                    }
                    newCursor = newCursor.coerceIn(0, formatted.length)
                    binding.etRegisterPhone.setSelection(newCursor)
                    isFormattingPhone = false
                }

                // Update Status Text
                if (digits.isEmpty()) {
                    if (binding.etRegisterPhone.hasFocus()) {
                        binding.tvRegisterPhoneStatus.text = "Phone number is required."
                        binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.status_red))
                        binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                    } else {
                        binding.tvRegisterPhoneStatus.visibility = View.GONE
                    }
                } else if (digits.length < 8) {
                    binding.tvRegisterPhoneStatus.text = "Phone number too short (min. 8 digits, currently: ${digits.length})"
                    binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.status_red))
                    binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                } else if (digits.length in 8..15) {
                    binding.tvRegisterPhoneStatus.text = "✓ Valid phone number for +$selectedCountryCode (${digits.length} digits)"
                    binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.status_green))
                    binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                } else {
                    binding.tvRegisterPhoneStatus.text = "Phone number exceeds limit (max. 15 digits)"
                    binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.status_red))
                    binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                }
            }
        })

        // 3. Live Validation: Email
        binding.etRegisterEmail.doAfterTextChanged { text ->
            val email = text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                binding.tvRegisterEmailStatus.visibility = View.GONE
            } else if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tvRegisterEmailStatus.text = "✓ Valid email format"
                binding.tvRegisterEmailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                binding.tvRegisterEmailStatus.visibility = View.VISIBLE
            } else {
                binding.tvRegisterEmailStatus.text = "Invalid email format (e.g. name@domain.com)"
                binding.tvRegisterEmailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                binding.tvRegisterEmailStatus.visibility = View.VISIBLE
            }
        }

        // 4. Live Validation: Password Checklist
        binding.etRegisterPassword.doAfterTextChanged { text ->
            val password = text?.toString().orEmpty()

            // Rule 1: 8+ characters
            val isLengthValid = password.length >= 8
            if (isLengthValid) {
                binding.ivRuleLength.setImageResource(R.drawable.ic_rule_check)
                binding.tvRuleLength.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                binding.tvRuleLength.text = "8+ characters ✓"
            } else {
                binding.ivRuleLength.setImageResource(R.drawable.ic_rule_uncheck)
                binding.tvRuleLength.setTextColor(ContextCompat.getColor(this, R.color.thinktech_slate))
                binding.tvRuleLength.text = "At least 8 characters"
            }

            // Rule 2: 1 symbol
            val hasSymbol = password.any { !it.isLetterOrDigit() }
            if (hasSymbol) {
                binding.ivRuleSymbol.setImageResource(R.drawable.ic_rule_check)
                binding.tvRuleSymbol.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                binding.tvRuleSymbol.text = "1 symbol ✓"
            } else {
                binding.ivRuleSymbol.setImageResource(R.drawable.ic_rule_uncheck)
                binding.tvRuleSymbol.setTextColor(ContextCompat.getColor(this, R.color.thinktech_slate))
                binding.tvRuleSymbol.text = "At least 1 symbol (@, #, $, %, !, etc.)"
            }

            if (isLengthValid && hasSymbol) {
                hideRegisterError()
            }
        }

        // Submit Sign Up
        binding.btnRegisterSubmit.setOnClickListener {
            hideRegisterError()

            val name = binding.etRegisterName.text.toString().trim()
            val phone = binding.etRegisterPhone.text.toString().trim()
            val email = binding.etRegisterEmail.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString().trim()

            // 1. Validate Name
            if (name.isEmpty()) {
                binding.tvRegisterNameError.text = "Please enter your full name."
                binding.tvRegisterNameError.visibility = View.VISIBLE
                binding.etRegisterName.requestFocus()
                return@setOnClickListener
            }

            // 2. Validate Phone
            val phoneDigits = phone.filter { it.isDigit() }
            if (phone.isEmpty()) {
                binding.tvRegisterPhoneStatus.text = "Please enter your phone number."
                binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                binding.etRegisterPhone.requestFocus()
                return@setOnClickListener
            }
            if (phoneDigits.length < 8 || phoneDigits.length > 15) {
                binding.tvRegisterPhoneStatus.text = "Please enter a valid phone number (8-15 digits)."
                binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                binding.etRegisterPhone.requestFocus()
                return@setOnClickListener
            }

            // 3. Validate Email
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tvRegisterEmailStatus.text = "Please enter a valid email address."
                binding.tvRegisterEmailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                binding.tvRegisterEmailStatus.visibility = View.VISIBLE
                binding.etRegisterEmail.requestFocus()
                return@setOnClickListener
            }

            // 4. Validate Password
            if (password.length < 8) {
                showRegisterError("Password must be at least 8 characters.")
                binding.etRegisterPassword.requestFocus()
                return@setOnClickListener
            }
            val hasSymbol = password.any { !it.isLetterOrDigit() }
            if (!hasSymbol) {
                showRegisterError("Password must contain at least 1 symbol (e.g. @, #, $, %, !).")
                binding.etRegisterPassword.requestFocus()
                return@setOnClickListener
            }

            // 5. Terms & Policy
            if (!binding.cbRegisterAgreement.isChecked) {
                showRegisterError("Please agree to the Terms of Service and Privacy Policy.")
                return@setOnClickListener
            }

            setRegisterLoading(true)

            authRepo.registerWithEmail(
                countryCode = selectedCountryCode,
                email = email,
                password = password,
                name = name,
                phone = phoneDigits,
                onSuccess = { user ->
                    setRegisterLoading(false)

                    // Trigger Android Autofill Framework to prompt Google Password Manager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            getSystemService(AutofillManager::class.java)?.commit()
                        } catch (e: Throwable) {
                            // Safe fallback
                        }
                    }

                    // Save to Remember Me if enabled
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (binding.cbRememberMe.isChecked) {
                        prefs.edit()
                            .putBoolean(KEY_REMEMBER_ME, true)
                            .putString(KEY_SAVED_ACCOUNT, email)
                            .putString(KEY_SAVED_PASSWORD, password)
                            .apply()
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Account Created Successfully")
                        .setMessage("Welcome, ${user.nickName ?: name}! Your account has been registered to the ThinkTech Smart Home ecosystem.")
                        .setPositiveButton("Continue") { _, _ ->
                            navigateToHome()
                        }
                        .setCancelable(false)
                        .show()
                },
                onError = { code, error ->
                    setRegisterLoading(false)
                    showRegisterError("Registration failed ($code): $error")
                }
            )
        }
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean): Boolean {
        val newVisible = !isVisible
        if (newVisible) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imageView.setImageResource(R.drawable.ic_visibility)
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imageView.setImageResource(R.drawable.ic_visibility_off)
        }
        editText.setSelection(editText.text.length)
        return newVisible
    }


    private fun showWelcomeScreen() {
        binding.layoutWelcome.visibility = View.VISIBLE
        binding.layoutLoginForm.visibility = View.GONE
        binding.layoutRegisterForm.visibility = View.GONE
        hideLoginError()
        hideRegisterError()
    }

    private fun showLoginForm() {
        binding.layoutWelcome.visibility = View.GONE
        binding.layoutLoginForm.visibility = View.VISIBLE
        binding.layoutRegisterForm.visibility = View.GONE
        hideLoginError()
    }

    private fun showRegisterForm() {
        binding.layoutWelcome.visibility = View.GONE
        binding.layoutLoginForm.visibility = View.GONE
        binding.layoutRegisterForm.visibility = View.VISIBLE
        hideRegisterError()
    }

    private fun setLoginLoading(isLoading: Boolean) {
        binding.progressLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLoginSubmit.isEnabled = !isLoading
        binding.btnLoginSubmit.alpha = if (isLoading) 0.6f else 1.0f
    }

    private fun showLoginError(msg: String) {
        binding.tvLoginError.text = msg
        binding.tvLoginError.visibility = View.VISIBLE
    }

    private fun hideLoginError() {
        binding.tvLoginError.visibility = View.GONE
    }

    private fun setRegisterLoading(isLoading: Boolean) {
        binding.progressRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegisterSubmit.isEnabled = !isLoading
        binding.btnRegisterSubmit.alpha = if (isLoading) 0.6f else 1.0f
    }

    private fun showRegisterError(msg: String) {
        binding.tvRegisterError.text = msg
        binding.tvRegisterError.visibility = View.VISIBLE
    }

    private fun hideRegisterError() {
        binding.tvRegisterError.visibility = View.GONE
    }

    private fun setupAgreementSection() {
        val agreementHtml = "I agree to the <font color='#165DFC'><b>Terms of Service</b></font> and <font color='#165DFC'><b>Privacy Policy</b></font>"
        val styledText = HtmlCompat.fromHtml(agreementHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvLoginAgreement.text = styledText
        binding.tvRegisterAgreement.text = styledText

        // Toggle checkbox when tapping anywhere on agreement row
        binding.layoutLoginAgreement.setOnClickListener {
            binding.cbLoginAgreement.isChecked = !binding.cbLoginAgreement.isChecked
        }

        binding.layoutRegisterAgreement.setOnClickListener {
            binding.cbRegisterAgreement.isChecked = !binding.cbRegisterAgreement.isChecked
        }
    }

    private fun showCountryPickerBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_country_picker, null)
        dialog.setContentView(sheetView)
        (sheetView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val rv = sheetView.findViewById<RecyclerView>(R.id.rvCountries)
        val etSearch = sheetView.findViewById<EditText>(R.id.etSearchCountry)
        val btnClose = sheetView.findViewById<ImageButton>(R.id.btnCloseCountryPicker)

        rv.layoutManager = LinearLayoutManager(this)
        var filteredList = countryList
        lateinit var adapter: CountryAdapter
        adapter = CountryAdapter(filteredList, selectedCountryCode) { country ->
            selectedCountryCode = country.code
            selectedCountryFlag = country.flag
            binding.tvRegisterCountryCode.text = "+${country.code}"
            binding.tvRegisterCountryFlag.text = country.flag

            // Re-validate phone with new country code if text present
            val currentPhone = binding.etRegisterPhone.text?.toString()?.trim().orEmpty()
            if (currentPhone.isNotEmpty()) {
                val digits = currentPhone.filter { it.isDigit() }
                if (digits.length in 8..15) {
                    binding.tvRegisterPhoneStatus.text = "✓ Valid phone number for +${country.code} (${digits.length} digits)"
                    binding.tvRegisterPhoneStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                    binding.tvRegisterPhoneStatus.visibility = View.VISIBLE
                }
            }

            dialog.dismiss()
        }
        rv.adapter = adapter

        etSearch.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty().lowercase()
            filteredList = if (query.isEmpty()) {
                countryList
            } else {
                countryList.filter {
                    it.name.lowercase().contains(query) || it.code.contains(query)
                }
            }
            adapter.updateList(filteredList)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private class CountryAdapter(
        private var items: List<Country>,
        private var selectedCode: String,
        private val onSelect: (Country) -> Unit
    ) : RecyclerView.Adapter<CountryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFlag: TextView = view.findViewById(R.id.tvItemFlag)
            val tvName: TextView = view.findViewById(R.id.tvItemCountryName)
            val tvCode: TextView = view.findViewById(R.id.tvItemCountryCode)
            val ivCheck: ImageView = view.findViewById(R.id.ivItemCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_country_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvFlag.text = item.flag
            holder.tvName.text = item.name
            holder.tvCode.text = "+${item.code}"
            val isSelected = item.code == selectedCode
            holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                onSelect(item)
            }
        }

        override fun getItemCount() = items.size

        fun updateList(newItems: List<Country>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private fun showForgotPasswordBottomSheet() {
        val sheetBinding = DialogForgotPasswordBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)
        (sheetBinding.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Pre-fill account from login field if available
        val currentAccount = binding.etLoginAccount.text?.toString()?.trim().orEmpty()
        if (currentAccount.isNotEmpty()) {
            sheetBinding.etForgotAccount.setText(currentAccount)
        }

        var isNewPasswordVisible = false
        sheetBinding.ivForgotTogglePassword.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            togglePasswordVisibility(
                sheetBinding.etForgotNewPassword,
                sheetBinding.ivForgotTogglePassword,
                isNewPasswordVisible
            )
        }

        var countDownTimer: CountDownTimer? = null

        fun showError(msg: String) {
            sheetBinding.tvForgotError.text = msg
            sheetBinding.llForgotError.visibility = View.VISIBLE
            sheetBinding.llForgotSuccess.visibility = View.GONE
        }

        fun hideError() {
            sheetBinding.llForgotError.visibility = View.GONE
        }

        sheetBinding.btnForgotSendCode.setOnClickListener {
            hideError()
            val account = sheetBinding.etForgotAccount.text?.toString()?.trim().orEmpty()
            if (account.isEmpty()) {
                showError("Please enter your email or phone number first.")
                return@setOnClickListener
            }

            sheetBinding.btnForgotSendCode.isEnabled = false
            sheetBinding.btnForgotSendCode.text = "Sending..."

            authRepo.sendResetPasswordCode(
                countryCode = selectedCountryCode,
                account = account,
                onSuccess = {
                    runOnUiThread {
                        Toast.makeText(this, "Verification code sent to $account", Toast.LENGTH_SHORT).show()
                        countDownTimer = object : CountDownTimer(60000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                sheetBinding.btnForgotSendCode.text = "${millisUntilFinished / 1000}s"
                            }

                            override fun onFinish() {
                                sheetBinding.btnForgotSendCode.isEnabled = true
                                sheetBinding.btnForgotSendCode.text = "Get Code"
                            }
                        }.start()
                    }
                },
                onError = { code, error ->
                    runOnUiThread {
                        sheetBinding.btnForgotSendCode.isEnabled = true
                        sheetBinding.btnForgotSendCode.text = "Get Code"
                        showError("Failed to send code: $error ($code)")
                    }
                }
            )
        }

        sheetBinding.btnForgotSubmit.setOnClickListener {
            hideError()
            val account = sheetBinding.etForgotAccount.text?.toString()?.trim().orEmpty()
            val code = sheetBinding.etForgotCode.text?.toString()?.trim().orEmpty()
            val newPassword = sheetBinding.etForgotNewPassword.text?.toString()?.trim().orEmpty()

            if (account.isEmpty()) {
                showError("Please enter your email address or phone number.")
                return@setOnClickListener
            }
            if (code.length < 4) {
                showError("Please enter the verification code.")
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                showError("New password must be at least 6 characters.")
                return@setOnClickListener
            }

            sheetBinding.btnForgotSubmit.isEnabled = false
            sheetBinding.btnForgotSubmit.text = ""
            sheetBinding.pbForgotLoading.visibility = View.VISIBLE

            authRepo.resetPassword(
                countryCode = selectedCountryCode,
                account = account,
                code = code,
                newPassword = newPassword,
                onSuccess = {
                    runOnUiThread {
                        sheetBinding.pbForgotLoading.visibility = View.GONE
                        sheetBinding.llForgotSuccess.visibility = View.VISIBLE
                        sheetBinding.btnForgotSubmit.visibility = View.GONE

                        // Auto-fill into login fields for quick convenience
                        binding.etLoginAccount.setText(account)
                        binding.etLoginPassword.setText(newPassword)

                        sheetBinding.root.postDelayed({
                            dialog.dismiss()
                            Toast.makeText(this@LoginActivity, "Password reset successfully! You can now sign in.", Toast.LENGTH_LONG).show()
                        }, 1200)
                    }
                },
                onError = { errCode, errMsg ->
                    runOnUiThread {
                        sheetBinding.pbForgotLoading.visibility = View.GONE
                        sheetBinding.btnForgotSubmit.text = "Reset Password"
                        sheetBinding.btnForgotSubmit.isEnabled = true
                        showError("Reset failed: $errMsg ($errCode)")
                    }
                }
            )
        }

        sheetBinding.btnCloseForgotPassword.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
        }

        dialog.show()
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

