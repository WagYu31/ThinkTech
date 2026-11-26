package com.thinktech.smarthome.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.api.IResetPasswordCallback
import com.thingclips.smart.android.user.api.IUidLoginCallback
import com.thingclips.smart.android.user.api.IValidateCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thinktech.smarthome.ThinkTechApp

class TuyaAuthRepository {

    companion object {
        private const val TAG = "TuyaAuthRepository"

        const val PREFS_NAME = "thinktech_auth_prefs"
        const val KEY_IS_LOGGED_IN = "pref_is_logged_in"
        const val KEY_REMEMBER_ME = "pref_remember_me"
        const val KEY_SAVED_ACCOUNT = "pref_saved_account"
        const val KEY_SAVED_PASSWORD = "pref_saved_password"
        const val KEY_SAVED_COUNTRY_CODE = "pref_saved_country_code"
        const val KEY_SAVED_USER_NICKNAME = "pref_saved_user_nickname"
        const val KEY_SAVED_USER_EMAIL = "pref_saved_user_email"
        const val KEY_SAVED_USER_UID = "pref_saved_user_uid"
        const val KEY_SAVED_USER_PHONE = "pref_saved_user_phone"

        var loggedInUser: User? = null

        fun toTuyaUid(account: String): String {
            return account.trim().lowercase()
                .replace("@", "_at_")
                .replace(".", "_")
                .replace(Regex("[^a-z0-9_-]"), "_")
                .take(50)
        }
    }

    private fun getPrefs(): SharedPreferences? {
        return try {
            ThinkTechApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Throwable) {
            null
        }
    }

    fun isTuyaSdkLoggedIn(): Boolean {
        if (!ThinkTechApp.isSdkInitialized) return false
        return try {
            ThingHomeSdk.getUserInstance()?.isLogin == true
        } catch (e: Throwable) {
            false
        }
    }

    fun isUserLoggedIn(): Boolean {
        if (loggedInUser != null) return true
        if (isTuyaSdkLoggedIn()) return true
        val prefs = getPrefs() ?: return false
        val isMarkedLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val hasSavedAcc = !prefs.getString(KEY_SAVED_ACCOUNT, null).isNullOrEmpty()
        val hasSavedPwd = !prefs.getString(KEY_SAVED_PASSWORD, null).isNullOrEmpty()
        return isMarkedLoggedIn && (hasSavedAcc || hasSavedPwd)
    }

    fun isMarkedLoggedIn(): Boolean {
        return getPrefs()?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
    }

    fun hasSavedCredentials(): Boolean {
        val prefs = getPrefs() ?: return false
        val account = prefs.getString(KEY_SAVED_ACCOUNT, null)
        val password = prefs.getString(KEY_SAVED_PASSWORD, null)
        return !account.isNullOrBlank() && !password.isNullOrBlank()
    }

    fun getSavedCredentials(): Triple<String, String, String>? {
        val prefs = getPrefs() ?: return null
        val account = prefs.getString(KEY_SAVED_ACCOUNT, null) ?: return null
        val password = prefs.getString(KEY_SAVED_PASSWORD, null) ?: return null
        val country = prefs.getString(KEY_SAVED_COUNTRY_CODE, "62") ?: "62"
        return Triple(account, password, country)
    }

    fun saveUserSession(
        user: User,
        account: String,
        password: String?,
        countryCode: String = "62",
        rememberMe: Boolean = true
    ) {
        loggedInUser = user
        try {
            getPrefs()?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putBoolean(KEY_REMEMBER_ME, rememberMe)
                putString(KEY_SAVED_ACCOUNT, account)
                if (!password.isNullOrEmpty() && rememberMe) {
                    putString(KEY_SAVED_PASSWORD, password)
                }
                putString(KEY_SAVED_COUNTRY_CODE, countryCode.removePrefix("+").trim())
                val displayName = user.nickName ?: user.username ?: if (account.contains("@")) account.substringBefore("@") else account
                putString(KEY_SAVED_USER_NICKNAME, displayName)
                putString(KEY_SAVED_USER_EMAIL, user.email ?: if (account.contains("@")) account else "")
                putString(KEY_SAVED_USER_UID, user.uid ?: "")
                putString(KEY_SAVED_USER_PHONE, user.mobile ?: if (!account.contains("@")) account else "")
                apply()
            }
            Log.d(TAG, "User session successfully saved for $account (rememberMe=$rememberMe)")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save user session: ${e.message}")
        }
    }

    fun silentAutoLogin(
        onSuccess: (User) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        if (isTuyaSdkLoggedIn()) {
            val user = getCurrentUser() ?: User().apply {
                val prefs = getPrefs()
                nickName = prefs?.getString(KEY_SAVED_USER_NICKNAME, "ThinkTech User")
                email = prefs?.getString(KEY_SAVED_USER_EMAIL, "")
                mobile = prefs?.getString(KEY_SAVED_USER_PHONE, "")
            }
            loggedInUser = user
            onSuccess(user)
            return
        }

        val creds = getSavedCredentials()
        if (creds == null) {
            onError("NO_CREDENTIALS", "No saved credentials found")
            return
        }

        val (account, password, countryCode) = creds
        Log.d(TAG, "Performing silent auto-login for $account...")
        if (account.contains("@")) {
            loginWithEmail(countryCode, account, password, onSuccess, onError)
        } else {
            loginWithPhone(countryCode, account, password, onSuccess, onError)
        }
    }

    fun getCurrentUser(): User? {
        if (loggedInUser != null) return loggedInUser
        if (ThinkTechApp.isSdkInitialized) {
            try {
                val sdkUser = ThingHomeSdk.getUserInstance()?.user
                if (sdkUser != null) {
                    loggedInUser = sdkUser
                    return sdkUser
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
        val prefs = getPrefs()
        if (prefs != null && prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            val acc = prefs.getString(KEY_SAVED_ACCOUNT, "") ?: ""
            val nick = prefs.getString(KEY_SAVED_USER_NICKNAME, "")
            val cachedUser = User().apply {
                nickName = if (!nick.isNullOrBlank()) nick else if (acc.contains("@")) acc.substringBefore("@") else acc
                email = prefs.getString(KEY_SAVED_USER_EMAIL, if (acc.contains("@")) acc else "")
                mobile = prefs.getString(KEY_SAVED_USER_PHONE, if (!acc.contains("@")) acc else "")
                username = nickName
            }
            loggedInUser = cachedUser
            return cachedUser
        }
        return null
    }

    fun loginWithEmail(
        countryCode: String,
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val cleanCountryCode = countryCode.removePrefix("+").trim()

        if (!ThinkTechApp.isSdkInitialized) {
            val user = User().apply {
                this.email = email
                this.nickName = email.substringBefore("@")
                this.username = email
            }
            loggedInUser = user
            saveUserSession(user, email, password, cleanCountryCode, true)
            onSuccess(user)
            return
        }

        val uid = toTuyaUid(email)

        try {
            // First attempt UID login/register (creates home automatically and avoids OTP)
            ThingHomeSdk.getUserInstance().loginOrRegisterWithUid(
                cleanCountryCode,
                uid,
                password,
                true,
                object : IUidLoginCallback {
                    override fun onSuccess(user: User, homeId: Long) {
                        if (user.email.isNullOrEmpty()) {
                            user.email = email
                        }
                        loggedInUser = user
                        saveUserSession(user, email, password, cleanCountryCode, true)
                        onSuccess(user)
                    }

                    override fun onError(code: String, error: String) {
                        // If UID login fails, fallback to traditional Tuya email login
                        ThingHomeSdk.getUserInstance().loginWithEmail(
                            cleanCountryCode,
                            email,
                            password,
                            object : ILoginCallback {
                                override fun onSuccess(user: User) {
                                    loggedInUser = user
                                    saveUserSession(user, email, password, cleanCountryCode, true)
                                    onSuccess(user)
                                }

                                override fun onError(errCode: String, errMsg: String) {
                                    onError(code, error)
                                }
                            }
                        )
                    }
                }
            )
        } catch (e: Throwable) {
            onError("SDK_ERROR", e.message ?: "Unknown SDK error")
        }
    }

    fun loginWithPhone(
        countryCode: String,
        phone: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val cleanCountryCode = countryCode.removePrefix("+").trim()
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")

        if (!ThinkTechApp.isSdkInitialized) {
            val user = User().apply {
                this.mobile = phone
                this.nickName = "User $phone"
                this.username = phone
            }
            loggedInUser = user
            saveUserSession(user, phone, password, cleanCountryCode, true)
            onSuccess(user)
            return
        }

        val uid = "p_$cleanPhone"

        try {
            ThingHomeSdk.getUserInstance().loginOrRegisterWithUid(
                cleanCountryCode,
                uid,
                password,
                true,
                object : IUidLoginCallback {
                    override fun onSuccess(user: User, homeId: Long) {
                        if (user.mobile.isNullOrEmpty()) {
                            user.mobile = phone
                        }
                        loggedInUser = user
                        saveUserSession(user, phone, password, cleanCountryCode, true)
                        onSuccess(user)
                    }

                    override fun onError(code: String, error: String) {
                        ThingHomeSdk.getUserInstance().loginWithPhonePassword(
                            cleanCountryCode,
                            cleanPhone,
                            password,
                            object : ILoginCallback {
                                override fun onSuccess(user: User) {
                                    loggedInUser = user
                                    saveUserSession(user, phone, password, cleanCountryCode, true)
                                    onSuccess(user)
                                }

                                override fun onError(errCode: String, errMsg: String) {
                                    onError(code, error)
                                }
                            }
                        )
                    }
                }
            )
        } catch (e: Throwable) {
            onError("SDK_ERROR", e.message ?: "Unknown SDK error")
        }
    }

    fun registerWithEmail(
        countryCode: String,
        email: String,
        password: String,
        name: String = "",
        phone: String = "",
        onSuccess: (User) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val cleanCountryCode = countryCode.removePrefix("+").trim()

        if (!ThinkTechApp.isSdkInitialized) {
            val demoUser = User().apply {
                this.email = email
                this.nickName = if (name.isNotBlank()) name else email.substringBefore("@")
                this.mobile = phone
                this.username = if (name.isNotBlank()) name else email
            }
            loggedInUser = demoUser
            saveUserSession(demoUser, email, password, cleanCountryCode, true)
            onSuccess(demoUser)
            return
        }

        try {
            val uid = toTuyaUid(email)

            // Use Tuya's official loginOrRegisterWithUid API with isCreateHome = true.
            // This immediately provisions the account and its default family home in Tuya Cloud
            // without requiring email OTP verification codes.
            ThingHomeSdk.getUserInstance().loginOrRegisterWithUid(
                cleanCountryCode,
                uid,
                password,
                true,
                object : IUidLoginCallback {
                    override fun onSuccess(user: User, homeId: Long) {
                        if (user.email.isNullOrEmpty()) {
                            user.email = email
                        }
                        if (name.isNotBlank()) {
                            user.nickName = name
                        }
                        if (phone.isNotBlank()) {
                            user.mobile = phone
                        }
                        loggedInUser = user
                        saveUserSession(user, email, password, cleanCountryCode, true)
                        onSuccess(user)
                    }

                    override fun onError(code: String, error: String) {
                        onError(code, error)
                    }
                }
            )
        } catch (e: Throwable) {
            onError("REGISTER_ERROR", e.message ?: "Unknown registration error")
        }
    }

    fun logout(
        onSuccess: () -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        loggedInUser = null
        try {
            val prefs = getPrefs()
            val keepRemember = prefs?.getBoolean(KEY_REMEMBER_ME, true) ?: true
            prefs?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, false)
                remove(KEY_SAVED_USER_NICKNAME)
                remove(KEY_SAVED_USER_EMAIL)
                remove(KEY_SAVED_USER_UID)
                remove(KEY_SAVED_USER_PHONE)
                if (!keepRemember) {
                    remove(KEY_SAVED_ACCOUNT)
                    remove(KEY_SAVED_PASSWORD)
                    remove(KEY_SAVED_COUNTRY_CODE)
                }
                apply()
            }
        } catch (t: Throwable) {
            // Safe fallback
        }

        if (!ThinkTechApp.isSdkInitialized) {
            onSuccess()
            return
        }

        try {
            ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                override fun onSuccess() {
                    onSuccess()
                }

                override fun onError(code: String, error: String) {
                    // Even if network logout reports error, local state is logged out
                    onSuccess()
                }
            })
        } catch (e: Throwable) {
            onSuccess()
        }
    }

    fun sendResetPasswordCode(
        countryCode: String,
        account: String,
        onSuccess: () -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        if (!ThinkTechApp.isSdkInitialized) {
            onSuccess()
            return
        }

        try {
            val callback = object : IValidateCallback {
                override fun onSuccess() {
                    onSuccess()
                }

                override fun onError(code: String, error: String) {
                    onError(code, error)
                }
            }

            val cleanCountryCode = countryCode.removePrefix("+").trim()
            if (account.contains("@")) {
                ThingHomeSdk.getUserInstance().getEmailValidateCode(cleanCountryCode, account, callback)
            } else {
                val cleanPhone = account.replace(Regex("[^0-9]"), "")
                ThingHomeSdk.getUserInstance().getValidateCode(cleanCountryCode, cleanPhone, callback)
            }
        } catch (e: Throwable) {
            onError("SEND_CODE_ERROR", e.message ?: "Failed to send verification code")
        }
    }

    fun resetPassword(
        countryCode: String,
        account: String,
        code: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        if (!ThinkTechApp.isSdkInitialized) {
            onSuccess()
            return
        }

        try {
            val callback = object : IResetPasswordCallback {
                override fun onSuccess() {
                    onSuccess()
                }

                override fun onError(code: String, error: String) {
                    onError(code, error)
                }
            }

            val cleanCountryCode = countryCode.removePrefix("+").trim()
            val cleanCode = code.trim()
            if (account.contains("@")) {
                ThingHomeSdk.getUserInstance().resetEmailPassword(cleanCountryCode, account, cleanCode, newPassword, callback)
            } else {
                val cleanPhone = account.replace(Regex("[^0-9]"), "")
                ThingHomeSdk.getUserInstance().resetPhonePassword(cleanCountryCode, cleanPhone, cleanCode, newPassword, callback)
            }
        } catch (e: Throwable) {
            onError("RESET_PASSWORD_ERROR", e.message ?: "Failed to reset password")
        }
    }
}
