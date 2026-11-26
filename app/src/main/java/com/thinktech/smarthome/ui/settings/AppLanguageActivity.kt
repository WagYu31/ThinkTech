package com.thinktech.smarthome.ui.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.AppLanguage
import com.thinktech.smarthome.data.AppLanguageManager
import com.thinktech.smarthome.databinding.ActivityAppLanguageBinding
import com.thinktech.smarthome.ui.home.MainActivity

/**
 * Flagship Huawei HarmonyOS-style App Language Picker with live bilingual & Voice AI preview,
 * instant language search, sculpted monogram badges, and real-time app-wide switching.
 */
class AppLanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppLanguageBinding
    private var selectedCode: String = "id"
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowChrome()
        selectedCode = AppLanguageManager.getSavedCode(this)
        setupListeners()
        renderPreview(AppLanguageManager.getLanguageByCode(selectedCode))
        renderLanguageList()
    }

    private fun setupWindowChrome() {
        val bg = Color.parseColor("#F5F7FA")
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.WHITE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    private fun setupListeners() {
        binding.btnBackLanguage.setOnClickListener { finish() }

        binding.etSearchLanguage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                renderLanguageList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnApplyLanguage.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val chosen = AppLanguageManager.getLanguageByCode(selectedCode)
            AppLanguageManager.setLanguage(this, chosen.code)

            val toastMsg = when (chosen.code) {
                "en" -> "App language set to English"
                "ms" -> "Bahasa aplikasi ditukar kepada Bahasa Melayu"
                "zh" -> "应用语言已切换为简体中文"
                "ja" -> "アプリの言語を日本語に変更しました"
                else -> "Bahasa aplikasi diubah ke Bahasa Indonesia"
            }
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()

            // Relaunch MainActivity cleanly so every tab & component renders in the newly chosen language
            val restart = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(restart)
            finish()
        }
    }

    private fun renderPreview(lang: AppLanguage) {
        binding.tvPreviewVoiceBadge.text = lang.voiceBadgeLabel
        binding.tvPreviewGreeting.text = lang.greetingPreview
        binding.tvPreviewVoiceQuote.text = lang.voiceExamplePreview

        // Live preview of screen chrome labels in the selected language!
        binding.tvLanguageHeaderTitle.text = AppLanguageManager.translate("Bahasa Aplikasi", lang.code)
        binding.tvLanguageSectionLabel.text = AppLanguageManager.translate("Pilih Bahasa", lang.code)
        binding.etSearchLanguage.hint = AppLanguageManager.translate("Cari bahasa...", lang.code)
        binding.tvLanguageNote.text = AppLanguageManager.translate(
            "Perubahan bahasa langsung diterapkan ke seluruh halaman dan Asisten Suara (Voice AI).",
            lang.code
        )
        binding.btnApplyLanguage.text = AppLanguageManager.translate("Terapkan Bahasa", lang.code)
    }

    private fun renderLanguageList() {
        val container = binding.layoutLanguageList
        container.removeAllViews()

        val filtered = AppLanguageManager.supportedLanguages.filter { lang ->
            searchQuery.isBlank() ||
                lang.nativeName.contains(searchQuery, ignoreCase = true) ||
                lang.regionSubtitle.contains(searchQuery, ignoreCase = true) ||
                lang.code.contains(searchQuery, ignoreCase = true)
        }

        filtered.forEachIndexed { index, lang ->
            val row = layoutInflater.inflate(R.layout.item_app_language, container, false)
            val tvBadge = row.findViewById<TextView>(R.id.tvLangBadge)
            val tvName = row.findViewById<TextView>(R.id.tvLangNativeName)
            val tvSub = row.findViewById<TextView>(R.id.tvLangRegionSub)
            val flRadio = row.findViewById<FrameLayout>(R.id.flLangRadio)
            val ivCheck = row.findViewById<ImageView>(R.id.ivLangCheck)
            val divider = row.findViewById<View>(R.id.viewLangDivider)
            val rowRoot = row.findViewById<View>(R.id.rowLanguageRoot)

            val isSelected = lang.code == selectedCode

            tvBadge.text = lang.badge
            tvBadge.setBackgroundResource(if (isSelected) R.drawable.bg_lang_badge_on else R.drawable.bg_lang_badge_off)
            tvBadge.setTextColor(Color.parseColor(if (isSelected) "#3B5BFF" else "#4B5563"))

            tvName.text = lang.nativeName
            tvName.setTextColor(Color.parseColor(if (isSelected) "#3B5BFF" else "#151922"))
            tvSub.text = lang.regionSubtitle

            flRadio.setBackgroundResource(if (isSelected) R.drawable.bg_lang_radio_on else R.drawable.bg_lang_radio_off)
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            divider.visibility = if (index == filtered.lastIndex) View.GONE else View.VISIBLE

            rowRoot.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                selectedCode = lang.code
                renderPreview(lang)
                renderLanguageList()
            }

            container.addView(row)
        }
    }
}
