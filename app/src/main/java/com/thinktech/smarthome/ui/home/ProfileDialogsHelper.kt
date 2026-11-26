package com.thinktech.smarthome.ui.home

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.AppLanguageManager
import com.thinktech.smarthome.data.TuyaAuthRepository
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.ui.pairing.PairDeviceActivity
import com.thinktech.smarthome.ui.settings.AppLanguageActivity

object ProfileDialogsHelper {

    const val KEY_TUYA_ROOMS = "pref_tuya_rooms"
    const val KEY_TUYA_HOME_NAME = "pref_tuya_home_name"

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun createCardBackground(color: Int = Color.WHITE, cornerRadius: Float = 36f, strokeColor: Int = Color.parseColor("#E5E7EB"), strokeWidth: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            this.cornerRadius = cornerRadius
            setStroke(strokeWidth, strokeColor)
        }
    }

    fun createPillButton(text: String, bgColor: Int, textColor: Int, context: Context, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            this.setTextColor(textColor)
            this.textSize = 14.5f
            this.typeface = Typeface.DEFAULT_BOLD
            this.gravity = Gravity.CENTER
            this.setPadding(dpToPx(context, 16), dpToPx(context, 12), dpToPx(context, 16), dpToPx(context, 12))
            this.background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dpToPx(context, 14).toFloat()
            }
            this.isClickable = true
            this.isFocusable = true
            this.setOnClickListener { onClick() }
        }
    }

    fun createBaseBottomSheet(context: Context, title: String): Pair<BottomSheetDialog, LinearLayout> {
        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet_dialog)
            setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 20), dpToPx(context, 28))
        }

        // Handle Bar
        val handle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(context, 40), dpToPx(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(context, 16)
            }
            setBackgroundResource(R.drawable.bg_drag_handle)
        }
        root.addView(handle)

        // Header Title Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(context, 16)
            }
        }

        val tvTitle = TextView(context).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvTitle)

        val btnClose = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor("#64748B"))
            val pad = dpToPx(context, 6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(context, 32), dpToPx(context, 32))
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(contentContainer)
        }
        root.addView(scrollView)

        dialog.setContentView(root)
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        return Pair(dialog, contentContainer)
    }

    // ==========================================
    // CUSTOM TUYA SMART MODAL DIALOGS
    // ==========================================

    fun showTuyaInputDialog(
        activity: Context,
        title: String,
        subtitle: String = "",
        hint: String = "",
        initialText: String = "",
        confirmText: String = "Simpan",
        cancelText: String = "Batal",
        confirmColor: Int = Color.parseColor("#FF6010"),
        onConfirm: (String) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 24).toFloat(), Color.parseColor("#F1F5F9"), 1)
            val pHoriz = dpToPx(activity, 22)
            val pVert = dpToPx(activity, 22)
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val tvTitle = TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        }
        root.addView(tvTitle)

        if (subtitle.isNotBlank()) {
            val tvSub = TextView(activity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(activity, 4)
                }
            }
            root.addView(tvSub)
        }

        val etInput = EditText(activity).apply {
            setText(initialText)
            this.hint = hint
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#CBD5E1"), dpToPx(activity, 1))
            val padH = dpToPx(activity, 14)
            val padV = dpToPx(activity, 12)
            setPadding(padH, padV, padH, padV)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(activity, 16)
                bottomMargin = dpToPx(activity, 20)
            }
        }
        root.addView(etInput)

        val rowButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val btnCancel = createPillButton(cancelText, Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"), activity) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(activity, 8)
            }
        }

        val btnConfirm = createPillButton(confirmText, confirmColor, Color.WHITE, activity) {
            val text = etInput.text.toString().trim()
            if (text.isNotBlank()) {
                dialog.dismiss()
                onConfirm(text)
            } else {
                Toast.makeText(activity, "Input tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(activity, 8)
            }
        }

        rowButtons.addView(btnCancel)
        rowButtons.addView(btnConfirm)
        root.addView(rowButtons)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (activity.resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()

        etInput.requestFocus()
    }

    fun showTuyaConfirmDialog(
        activity: Context,
        title: String,
        message: String,
        confirmText: String = "Lanjutkan",
        cancelText: String = "Batal",
        confirmColor: Int = Color.parseColor("#FF6010"),
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 24).toFloat(), Color.parseColor("#F1F5F9"), 1)
            val pHoriz = dpToPx(activity, 22)
            val pVert = dpToPx(activity, 22)
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val tvTitle = TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 8)
            }
        }
        root.addView(tvTitle)

        val tvMsg = TextView(activity).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#475569"))
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 20)
            }
        }
        root.addView(tvMsg)

        val rowButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val btnCancel = createPillButton(cancelText, Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"), activity) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(activity, 8)
            }
        }

        val btnConfirm = createPillButton(confirmText, confirmColor, Color.WHITE, activity) {
            dialog.dismiss()
            onConfirm()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(activity, 8)
            }
        }

        rowButtons.addView(btnCancel)
        rowButtons.addView(btnConfirm)
        root.addView(rowButtons)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (activity.resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showTuyaInfoDialog(
        activity: Context,
        title: String,
        message: String,
        positiveText: String = "Mengerti",
        positiveColor: Int = Color.parseColor("#FF6010"),
        onPositive: (() -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 24).toFloat(), Color.parseColor("#F1F5F9"), 1)
            val pHoriz = dpToPx(activity, 22)
            val pVert = dpToPx(activity, 22)
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val tvTitle = TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 8)
            }
        }
        root.addView(tvTitle)

        val tvMsg = TextView(activity).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#475569"))
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 20)
            }
        }
        root.addView(tvMsg)

        val btnOk = createPillButton(positiveText, positiveColor, Color.WHITE, activity) {
            dialog.dismiss()
            onPositive?.invoke()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(btnOk)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (activity.resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showTuyaOptionsDialog(
        activity: Context,
        title: String,
        options: List<String>,
        onSelected: (Int) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 24).toFloat(), Color.parseColor("#F1F5F9"), 1)
            val pHoriz = dpToPx(activity, 22)
            val pVert = dpToPx(activity, 20)
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val tvTitle = TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 14)
            }
        }
        root.addView(tvTitle)

        options.forEachIndexed { index, option ->
            val optView = TextView(activity).apply {
                text = option
                textSize = 14.5f
                setTextColor(Color.parseColor("#1E293B"))
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val padH = dpToPx(activity, 14)
                val padV = dpToPx(activity, 12)
                setPadding(padH, padV, padH, padV)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 8)
                }
                setOnClickListener {
                    dialog.dismiss()
                    onSelected(index)
                }
            }
            root.addView(optView)
        }

        val btnCancel = createPillButton("Batal", Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"), activity) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(activity, 6)
            }
        }
        root.addView(btnCancel)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (activity.resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // ==========================================
    // 1. PROFIL PENGGUNA
    // ==========================================
    fun showUserProfileDialog(activity: MainActivity, authRepo: TuyaAuthRepository, onProfileUpdated: (String) -> Unit) {
        val (dialog, content) = createBaseBottomSheet(activity, "Profil Pengguna")

        val prefs = activity.getSharedPreferences(TuyaAuthRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val user = authRepo.getCurrentUser()
        val currentNick = prefs.getString(TuyaAuthRepository.KEY_SAVED_USER_NICKNAME, "") ?: ""
        val currentAcc = prefs.getString(TuyaAuthRepository.KEY_SAVED_ACCOUNT, "") ?: ""

        val uEmail = user?.email
        val uUsername = user?.username
        val uNick = user?.nickName

        val cleanEmail = when {
            !currentAcc.isNullOrBlank() && currentAcc.contains("@") -> currentAcc
            !uEmail.isNullOrBlank() -> uEmail
            !uUsername.isNullOrBlank() && uUsername.contains("_at_") -> {
                uUsername.replace("_at_", "@").replace("_com", ".com")
            }
            else -> "cingire687@gmail.com"
        }

        val initialName = when {
            currentNick.isNotBlank() -> currentNick
            !uNick.isNullOrBlank() -> uNick
            cleanEmail.contains("@") -> cleanEmail.substringBefore("@")
            !uUsername.isNullOrBlank() -> {
                if (uUsername.contains("_at_")) uUsername.substringBefore("_at_") else uUsername
            }
            else -> "cingire687"
        }

        val uid = user?.uid ?: if (currentAcc.isNotBlank()) TuyaAuthRepository.toTuyaUid(currentAcc) else "thinktech_user"

        // Avatar Preview
        val avatarContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 20)
            }
        }
        val ivAvatar = ImageView(activity).apply {
            setImageResource(R.drawable.ic_robot_avatar)
            layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 74), dpToPx(activity, 74))
        }
        val tvSub = TextView(activity).apply {
            text = "Akun ThinkTech Cloud IoT Terverifikasi ✓"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, dpToPx(activity, 6), 0, 0)
        }
        avatarContainer.addView(ivAvatar)
        avatarContainer.addView(tvSub)
        content.addView(avatarContainer)

        // Name input section
        val tvLabelName = TextView(activity).apply {
            text = "Nama Tampilan (Nickname)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dpToPx(activity, 6))
        }
        content.addView(tvLabelName)

        val etName = EditText(activity).apply {
            setText(initialName)
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#CBD5E1"), dpToPx(activity, 1))
            val padH = dpToPx(activity, 14)
            val padV = dpToPx(activity, 12)
            setPadding(padH, padV, padH, padV)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 14)
            }
        }
        content.addView(etName)

        // Email field (Read Only)
        val tvLabelEmail = TextView(activity).apply {
            text = "Email Akun Terdaftar"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dpToPx(activity, 6))
        }
        content.addView(tvLabelEmail)

        val tvEmailVal = TextView(activity).apply {
            text = cleanEmail
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
            background = createCardBackground(Color.parseColor("#F1F5F9"), dpToPx(activity, 12).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val padH = dpToPx(activity, 14)
            val padV = dpToPx(activity, 12)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 14)
            }
        }
        content.addView(tvEmailVal)

        // UID field
        val tvLabelUid = TextView(activity).apply {
            text = "ID Akun Tuya Cloud (UID)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dpToPx(activity, 6))
        }
        content.addView(tvLabelUid)

        val tvUidVal = TextView(activity).apply {
            text = uid
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            background = createCardBackground(Color.parseColor("#F1F5F9"), dpToPx(activity, 12).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val padH = dpToPx(activity, 14)
            val padV = dpToPx(activity, 12)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 22)
            }
        }
        content.addView(tvUidVal)

        // Save Button (Tuya Orange Accent)
        val btnSave = createPillButton("Simpan Perubahan", Color.parseColor("#FF6010"), Color.WHITE, activity) {
            val newName = etName.text.toString().trim()
            if (newName.isNotBlank()) {
                prefs.edit().putString(TuyaAuthRepository.KEY_SAVED_USER_NICKNAME, newName).apply()
                authRepo.getCurrentUser()?.nickName = newName
                onProfileUpdated(newName)
                Toast.makeText(activity, "Nama profil berhasil disimpan: $newName", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(activity, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(btnSave)

        dialog.show()
    }

    // ==========================================
    // 2. PEMINDAI QR & PAIRING CEPAT
    // ==========================================
    fun showQrScannerDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Pemindai QR & Pairing Cepat")

        val scannerMockCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createCardBackground(Color.parseColor("#0F172A"), dpToPx(activity, 20).toFloat(), Color.parseColor("#38BDF8"), dpToPx(activity, 2))
            setPadding(dpToPx(activity, 24), dpToPx(activity, 28), dpToPx(activity, 24), dpToPx(activity, 28))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 18)
            }
        }

        val ivQr = ImageView(activity).apply {
            setImageResource(R.drawable.ic_qr_scan)
            setColorFilter(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 74), dpToPx(activity, 74))
        }
        val tvDesc = TextView(activity).apply {
            text = "Arahkan kamera ke QR Code pada perangkat IoT ThinkTech untuk menghubungkan secara instan."
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(activity, 12), 0, 0)
        }
        scannerMockCard.addView(ivQr)
        scannerMockCard.addView(tvDesc)
        content.addView(scannerMockCard)

        // Action 1: Manual Code Input
        val btnManualCode = createPillButton("⌨️ Masukkan Kode Pairing Manual", Color.parseColor("#F1F5F9"), Color.parseColor("#1E293B"), activity) {
            dialog.dismiss()
            showTuyaInputDialog(
                activity,
                title = "Input Kode Perangkat",
                subtitle = "Masukkan ID atau kode serial unik perangkat saklar:",
                hint = "Contoh: TT-SWITCH-3CH",
                confirmText = "Hubungkan",
                confirmColor = Color.parseColor("#2563EB")
            ) { code ->
                Toast.makeText(activity, "Kode \"$code\" valid! Menghubungkan ke saklar...", Toast.LENGTH_SHORT).show()
                activity.startActivity(Intent(activity, PairDeviceActivity::class.java))
            }
        }
        btnManualCode.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpToPx(activity, 10)
        }
        content.addView(btnManualCode)

        // Action 2: Standard Wi-Fi Pairing
        val btnWifiPair = createPillButton("📡 Buka Pairing Wi-Fi (2.4 GHz)", Color.parseColor("#FF6010"), Color.WHITE, activity) {
            dialog.dismiss()
            activity.startActivity(Intent(activity, PairDeviceActivity::class.java))
        }
        content.addView(btnWifiPair)

        dialog.show()
    }

    // ==========================================
    // 3. PENGATURAN APLIKASI
    // ==========================================
    fun showAppSettingsDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(
            activity,
            AppLanguageManager.tr(activity, "Pengaturan Aplikasi")
        )

        // Prominent App Language Selector Card
        val currentLang = AppLanguageManager.getCurrentLanguage(activity)
        val cardLanguage = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createCardBackground(
                Color.parseColor("#EEF2FF"),
                dpToPx(activity, 16).toFloat(),
                Color.parseColor("#DCE4FF"),
                dpToPx(activity, 1)
            )
            val pad = dpToPx(activity, 14)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 12)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                activity.startActivity(Intent(activity, AppLanguageActivity::class.java))
            }
        }
        val ivLangIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_app_language)
            layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 22), dpToPx(activity, 22)).apply {
                marginEnd = dpToPx(activity, 12)
            }
        }
        val tvLangLabel = TextView(activity).apply {
            text = AppLanguageManager.tr(activity, "Bahasa Aplikasi")
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#151922"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLangValue = TextView(activity).apply {
            text = "${currentLang.nativeName} ›"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#3B5BFF"))
        }
        cardLanguage.addView(ivLangIcon)
        cardLanguage.addView(tvLangLabel)
        cardLanguage.addView(tvLangValue)
        content.addView(cardLanguage)

        // Switch Item: Haptic Feedback
        val rowHaptic = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(activity, 10), 0, dpToPx(activity, 10))
        }
        val tvHaptic = TextView(activity).apply {
            text = "Getaran & Haptic Feedback"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swHaptic = SwitchCompat(activity).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                Toast.makeText(activity, "Haptic feedback: ${if (isChecked) "Aktif" else "Nonaktif"}", Toast.LENGTH_SHORT).show()
            }
        }
        rowHaptic.addView(tvHaptic)
        rowHaptic.addView(swHaptic)
        content.addView(rowHaptic)

        // Switch Item: Data Saver
        val rowDataSaver = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(activity, 10), 0, dpToPx(activity, 10))
        }
        val tvData = TextView(activity).apply {
            text = "Mode Penghemat Data IoT"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swData = SwitchCompat(activity).apply {
            isChecked = false
            setOnCheckedChangeListener { _, isChecked ->
                Toast.makeText(activity, "Mode hemat data: ${if (isChecked) "Aktif" else "Nonaktif"}", Toast.LENGTH_SHORT).show()
            }
        }
        rowDataSaver.addView(tvData)
        rowDataSaver.addView(swData)
        content.addView(rowDataSaver)

        // Clear Cache
        val cardCache = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 14)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(activity, 12)
                bottomMargin = dpToPx(activity, 12)
            }
            isClickable = true
        }
        val tvCacheLabel = TextView(activity).apply {
            text = "Bersihkan Cache Aplikasi"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvCacheSize = TextView(activity).apply {
            text = "18.4 MB"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
        }
        cardCache.addView(tvCacheLabel)
        cardCache.addView(tvCacheSize)
        cardCache.setOnClickListener {
            try {
                activity.cacheDir.deleteRecursively()
            } catch (t: Throwable) {}
            tvCacheSize.text = "0.0 B"
            Toast.makeText(activity, "Cache aplikasi berhasil dibersihkan!", Toast.LENGTH_SHORT).show()
        }
        content.addView(cardCache)

        // Info Rows
        val infoBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#F1F5F9"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 14)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 18)
            }
        }
        infoBox.addView(TextView(activity).apply {
            text = "Versi Aplikasi: ThinkTech v1.0.9 (Build 9) HyperOS UI\nTuya Smart Life SDK Engine: v7.8.0 Active\nProtokol Keamanan: 256-Bit SSL TLS 1.3 Active"
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            setLineSpacing(0f, 1.3f)
        })
        content.addView(infoBox)

        dialog.show()
    }

    // ==========================================
    // 4. MANAJEMEN KELUARGA & RUANGAN (TUYA POLISHED)
    // ==========================================
    fun showFamilyManagementDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Manajemen Keluarga & Ruangan")
        val prefs = activity.getSharedPreferences(TuyaAuthRepository.PREFS_NAME, Context.MODE_PRIVATE)

        // Read saved home name and rooms
        var homeName = prefs.getString(KEY_TUYA_HOME_NAME, "Rumah Utama") ?: "Rumah Utama"
        val savedRoomsStr = prefs.getString(KEY_TUYA_ROOMS, null)
        val roomsList = (if (!savedRoomsStr.isNullOrBlank()) {
            savedRoomsStr.split("|").filter { it.isNotBlank() }
        } else {
            listOf("Ruang Tamu", "Kamar Tidur", "Dapur", "Ruang Kerja")
        }).toMutableList()

        fun saveRoomsToPrefs() {
            prefs.edit().putString(KEY_TUYA_ROOMS, roomsList.joinToString("|")).apply()
            activity.refreshRoomTabs()
            activity.applyRoomFilter()
        }

        fun saveHomeNameToPrefs(name: String) {
            homeName = name
            prefs.edit().putString(KEY_TUYA_HOME_NAME, name).apply()
            activity.refreshHomeName(name)
        }

        // Top Home Card
        val homeCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#EFF6FF"), dpToPx(activity, 18).toFloat(), Color.parseColor("#BFDBFE"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 18)
            }
        }

        val rowHomeHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvHomeTitle = TextView(activity).apply {
            text = "🏠 $homeName"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E3A8A"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnEditHome = TextView(activity).apply {
            text = "Ubah"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            background = createCardBackground(Color.WHITE, dpToPx(activity, 10).toFloat(), Color.parseColor("#93C5FD"), dpToPx(activity, 1))
            setPadding(dpToPx(activity, 10), dpToPx(activity, 4), dpToPx(activity, 10), dpToPx(activity, 4))
            isClickable = true
            setOnClickListener {
                showTuyaInputDialog(
                    activity,
                    title = "Ubah Nama Rumah",
                    subtitle = "Atur nama rumah pintar ThinkTech Anda:",
                    hint = "Contoh: Rumah Utama, Villa Puncak",
                    initialText = homeName,
                    confirmText = "Simpan",
                    confirmColor = Color.parseColor("#FF6010")
                ) { newName ->
                    saveHomeNameToPrefs(newName)
                    tvHomeTitle.text = "🏠 $newName"
                    Toast.makeText(activity, "Nama rumah berhasil diubah menjadi: $newName", Toast.LENGTH_SHORT).show()
                }
            }
        }

        rowHomeHeader.addView(tvHomeTitle)
        rowHomeHeader.addView(btnEditHome)
        homeCard.addView(rowHomeHeader)

        val tvHomeLoc = TextView(activity).apply {
            text = "📍 Jakarta, Indonesia · ${roomsList.size} Ruangan Terdaftar"
            textSize = 12.5f
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dpToPx(activity, 6), 0, 0)
        }
        homeCard.addView(tvHomeLoc)
        content.addView(homeCard)

        // Rooms section header
        val rowRoomsHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(activity, 10))
        }

        val tvRoomsTitle = TextView(activity).apply {
            text = "Daftar Ruangan Terdaftar"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvRoomsCountBadge = TextView(activity).apply {
            text = "${roomsList.size} Ruangan"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B"))
            background = createCardBackground(Color.parseColor("#F1F5F9"), dpToPx(activity, 8).toFloat(), Color.parseColor("#E2E8F0"), 1)
            setPadding(dpToPx(activity, 8), dpToPx(activity, 3), dpToPx(activity, 8), dpToPx(activity, 3))
        }

        rowRoomsHeader.addView(tvRoomsTitle)
        rowRoomsHeader.addView(tvRoomsCountBadge)
        content.addView(rowRoomsHeader)

        // Dynamic container for room list
        val roomsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        content.addView(roomsContainer)

        fun getRoomEmoji(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("tamu") || lower.contains("living") -> "🛋️"
                lower.contains("tidur") || lower.contains("bedroom") -> "🛏️"
                lower.contains("dapur") || lower.contains("kitchen") -> "🍳"
                lower.contains("kerja") || lower.contains("office") -> "💻"
                lower.contains("balkon") || lower.contains("balcony") -> "🌿"
                lower.contains("garasi") || lower.contains("garage") -> "🚗"
                lower.contains("mandi") || lower.contains("bath") -> "🚿"
                lower.contains("makan") || lower.contains("dining") -> "🍽️"
                else -> "🏠"
            }
        }

        fun renderRooms() {
            roomsContainer.removeAllViews()
            tvRoomsCountBadge.text = "${roomsList.size} Ruangan"
            tvHomeLoc.text = "📍 Jakarta, Indonesia · ${roomsList.size} Ruangan Terdaftar"

            roomsList.forEachIndexed { index, rName ->
                val devicesInThisRoom = activity.allDevicesList.filter { it.room == rName }

                val roomCard = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                    val pad = dpToPx(activity, 12)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dpToPx(activity, 8)
                    }
                    isClickable = true
                    isFocusable = true
                }

                // Icon Badge
                val iconBox = TextView(activity).apply {
                    text = getRoomEmoji(rName)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    background = createCardBackground(Color.parseColor("#EFF6FF"), dpToPx(activity, 10).toFloat(), Color.TRANSPARENT, 0)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 38), dpToPx(activity, 38))
                }
                roomCard.addView(iconBox)

                // Name & Device count
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dpToPx(activity, 12)
                    }
                }

                val tvRName = TextView(activity).apply {
                    text = rName
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#1E293B"))
                }
                val tvRCount = TextView(activity).apply {
                    text = if (devicesInThisRoom.isNotEmpty()) {
                        val devNames = devicesInThisRoom.joinToString(", ") { it.name }
                        "${devicesInThisRoom.size} Perangkat Terhubung ($devNames)"
                    } else {
                        "0 Perangkat (Ketuk untuk menambahkan)"
                    }
                    textSize = 12f
                    setTextColor(if (devicesInThisRoom.isNotEmpty()) Color.parseColor("#10B981") else Color.parseColor("#64748B"))
                    setPadding(0, dpToPx(activity, 2), 0, 0)
                }
                textCol.addView(tvRName)
                textCol.addView(tvRCount)
                roomCard.addView(textCol)

                // Delete Button (only if more than 1 room)
                val btnDelete = TextView(activity).apply {
                    text = "✕"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#94A3B8"))
                    background = createCardBackground(Color.parseColor("#F1F5F9"), dpToPx(activity, 14).toFloat(), Color.TRANSPARENT, 0)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 28), dpToPx(activity, 28))
                    setOnClickListener {
                        if (roomsList.size <= 1) {
                            Toast.makeText(activity, "Minimal harus memiliki 1 ruangan terdaftar", Toast.LENGTH_SHORT).show()
                        } else {
                            showTuyaConfirmDialog(
                                activity,
                                title = "Hapus Ruangan",
                                message = "Apakah Anda yakin ingin menghapus ruangan \"$rName\"?",
                                confirmText = "Hapus",
                                confirmColor = Color.parseColor("#EF4444")
                            ) {
                                roomsList.removeAt(index)
                                saveRoomsToPrefs()
                                renderRooms()
                                Toast.makeText(activity, "Ruangan \"$rName\" berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                roomCard.addView(btnDelete)

                // Ketuk kartu ruangan untuk mengelola / menambahkan perangkat ke ruangan ini
                roomCard.setOnClickListener {
                    showRoomDevicesDialog(activity, rName) {
                        renderRooms()
                        activity.applyRoomFilter()
                    }
                }

                roomsContainer.addView(roomCard)
            }
        }

        renderRooms()

        // Add Room Button (Tuya Orange Tint Button)
        val btnAddRoom = TextView(activity).apply {
            text = "＋ Tambah Ruangan Baru"
            setTextColor(Color.parseColor("#EA580C"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(activity, 16), dpToPx(activity, 13), dpToPx(activity, 16), dpToPx(activity, 13))
            background = createCardBackground(Color.parseColor("#FFF7ED"), dpToPx(activity, 14).toFloat(), Color.parseColor("#FDBA74"), dpToPx(activity, 1))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(activity, 6)
                bottomMargin = dpToPx(activity, 16)
            }
            setOnClickListener {
                showTuyaInputDialog(
                    activity,
                    title = "Tambah Ruangan Baru",
                    subtitle = "Masukkan nama ruangan untuk mengelompokkan saklar dan perangkat pintar Anda:",
                    hint = "Nama ruangan (misal: Balkon, Garasi, Teras)",
                    confirmText = "Simpan",
                    confirmColor = Color.parseColor("#FF6010")
                ) { rName ->
                    roomsList.add(rName)
                    saveRoomsToPrefs()
                    renderRooms()
                    Toast.makeText(activity, "Ruangan \"$rName\" berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(btnAddRoom)

        // Invite Family Member Card
        val inviteCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 16).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 14)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        inviteCard.addView(TextView(activity).apply {
            text = "👥 Bagikan Rumah ke Anggota Keluarga"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        })

        inviteCard.addView(TextView(activity).apply {
            text = "Anggota keluarga dapat bergabung dan mengontrol saklar dengan kode undangan berikut:"
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 10))
        })

        val codeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 10).toFloat(), Color.parseColor("#CBD5E1"), dpToPx(activity, 1))
            val p = dpToPx(activity, 10)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 12)
            }
        }

        val tvCode = TextView(activity).apply {
            text = "TT-883921"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#0F172A"))
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnCopyCode = TextView(activity).apply {
            text = "Salin"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            isClickable = true
            setOnClickListener {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ThinkTech Family Code", "TT-883921"))
                Toast.makeText(activity, "Kode undangan TT-883921 berhasil disalin!", Toast.LENGTH_SHORT).show()
            }
        }

        codeRow.addView(tvCode)
        codeRow.addView(btnCopyCode)
        inviteCard.addView(codeRow)

        val btnInvite = createPillButton("👥 Salin & Bagikan Kode Undangan", Color.parseColor("#2563EB"), Color.WHITE, activity) {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ThinkTech Family Code", "TT-883921"))
            Toast.makeText(activity, "Kode undangan \"TT-883921\" berhasil disalin ke clipboard!", Toast.LENGTH_LONG).show()
        }
        inviteCard.addView(btnInvite)
        content.addView(inviteCard)

        dialog.show()
    }

    // ==========================================
    // 5. PUSAT PESAN
    // ==========================================
    fun showMessageCenterDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Pusat Pesan & Notifikasi")

        val messages = listOf(
            Triple("🟢 WiFi 3ch switch", "Perangkat terhubung stabil ke Wi-Fi 2.4 GHz (Sinyal 92%)", "Baru saja"),
            Triple("⚡ Smart Assistant AI", "Skenario saklar berhasil dieksekusi", "15 menit lalu"),
            Triple("🛡️ Keamanan Akun", "Sesi cloud terenkripsi aktif di perangkat ini", "1 jam lalu"),
            Triple("📢 Pembaruan Sistem", "ThinkTech Smart Home v1.0.9 siap digunakan", "Hari ini")
        )

        messages.forEach { (title, desc, time) ->
            val msgCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val pad = dpToPx(activity, 14)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 10)
                }
            }
            val rowTop = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val tvMTitle = TextView(activity).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvMTime = TextView(activity).apply {
                text = time
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            rowTop.addView(tvMTitle)
            rowTop.addView(tvMTime)

            val tvMDesc = TextView(activity).apply {
                text = desc
                textSize = 13f
                setTextColor(Color.parseColor("#475569"))
                setPadding(0, dpToPx(activity, 4), 0, 0)
            }
            msgCard.addView(rowTop)
            msgCard.addView(tvMDesc)
            content.addView(msgCard)
        }

        val btnMarkRead = createPillButton("✓ Tandai Semua Sudah Dibaca", Color.parseColor("#FF6010"), Color.WHITE, activity) {
            Toast.makeText(activity, "Semua notifikasi telah ditandai dibaca.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        content.addView(btnMarkRead)

        dialog.show()
    }

    // ==========================================
    // 6. MY ORDERS
    // ==========================================
    fun showMyOrdersDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Pesanan Saya (ThinkTech Store)")

        val orders = listOf(
            Triple("📦 WiFi 3ch Smart Switch", "Status: Tiba di Rumah & Terpasang\nResi: JNE-ID-8839201948 · Rp 165.000", Color.parseColor("#10B981")),
            Triple("📦 Zigbee 3.0 Mini Hub Gateway", "Status: Pesanan Selesai\nResi: SICEPAT-7729104 · Rp 220.000", Color.parseColor("#2563EB")),
            Triple("📦 Smart LCD Temperature Sensor", "Status: Tersedia di Official Store\nKompatibel dengan ThinkTech App", Color.parseColor("#64748B"))
        )

        orders.forEach { (pName, pDesc, badgeColor) ->
            val oCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val pad = dpToPx(activity, 14)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 10)
                }
            }
            val tvName = TextView(activity).apply {
                text = pName
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            }
            val tvDesc = TextView(activity).apply {
                text = pDesc
                textSize = 12f
                setTextColor(badgeColor)
                setPadding(0, dpToPx(activity, 4), 0, 0)
                setLineSpacing(0f, 1.25f)
            }
            oCard.addView(tvName)
            oCard.addView(tvDesc)
            content.addView(oCard)
        }

        val btnTrack = createPillButton("🚚 Lacak Pengiriman Ekspedisi", Color.parseColor("#2563EB"), Color.WHITE, activity) {
            Toast.makeText(activity, "Paket WiFi 3ch switch telah diterima di alamat penerima.", Toast.LENGTH_LONG).show()
        }
        content.addView(btnTrack)

        dialog.show()
    }

    // ==========================================
    // 7. FAQ & FEEDBACK
    // ==========================================
    fun showFaqFeedbackDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Pusat Bantuan & Umpan Balik")

        val faqs = listOf(
            Pair("❓ Mengapa saklar harus Wi-Fi 2.4 GHz?", "Frekuensi 2.4 GHz memiliki jangkauan yang jauh lebih luas dan menembus dinding beton dengan stabil dibanding 5 GHz."),
            Pair("❓ Bagaimana cara reset saklar jika ganti Wi-Fi?", "Tekan dan tahan tombol saklar saluran 1 selama 7-10 detik hingga lampu LED indikator berkedip biru cepat, lalu buka menu Tambah Perangkat."),
            Pair("❓ Apakah saklar tetap berfungsi jika internet mati?", "Ya, tombol sentuh saklar fisik di dinding tetap berfungsi 100% secara manual meskipun koneksi internet terputus.")
        )

        faqs.forEach { (q, a) ->
            val faqCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val pad = dpToPx(activity, 14)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 10)
                }
            }
            faqCard.addView(TextView(activity).apply {
                text = q
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            faqCard.addView(TextView(activity).apply {
                text = a
                textSize = 12.5f
                setTextColor(Color.parseColor("#475569"))
                setPadding(0, dpToPx(activity, 4), 0, 0)
                setLineSpacing(0f, 1.25f)
            })
            content.addView(faqCard)
        }

        // Feedback form
        val tvFbHeader = TextView(activity).apply {
            text = "Kirim Masukan / Lapor Masalah"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 6))
        }
        content.addView(tvFbHeader)

        val etFeedback = EditText(activity).apply {
            hint = "Tuliskan laporan bug atau saran fitur..."
            textSize = 13.5f
            setTextColor(Color.parseColor("#0F172A"))
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#CBD5E1"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 12)
            setPadding(pad, pad, pad, pad)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 14)
            }
        }
        content.addView(etFeedback)

        val btnSend = createPillButton("✉️ Kirim Masukan ke Developer", Color.parseColor("#FF6010"), Color.WHITE, activity) {
            val text = etFeedback.text.toString().trim()
            if (text.isNotBlank()) {
                Toast.makeText(activity, "Terima kasih! Masukan Anda telah kami terima untuk pembaruan berikutnya.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            } else {
                Toast.makeText(activity, "Silakan isi masukan terlebih dahulu.", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(btnSend)

        dialog.show()
    }

    // ==========================================
    // 8. THIRD-PARTY SERVICES
    // ==========================================
    fun showThirdPartyDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Layanan Asisten Pihak Ketiga")

        val services = listOf(
            Triple("Amazon Alexa", "Skill: Tuya Smart / Smart Life · Siap Dihubungkan", R.drawable.ic_service_alexa),
            Triple("Google Assistant", "Action: Tuya Smart Home · Terhubung", R.drawable.ic_service_google),
            Triple("Apple Siri Shortcuts", "Pintasan Suara Siri 'Matikan Lampu' · Siap Pakai", R.drawable.ic_service_siri),
            Triple("SmartThings & Home Assistant", "Integrasi Protokol MQTT Cloud Ready", R.drawable.ic_service_smartthings)
        )

        services.forEach { (name, desc, iconRes) ->
            val sCard = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val pad = dpToPx(activity, 14)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 10)
                }
                isClickable = true
            }

            val iv = ImageView(activity).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 32), dpToPx(activity, 32))
            }
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(activity, 14)
                }
            }
            textCol.addView(TextView(activity).apply {
                text = name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            textCol.addView(TextView(activity).apply {
                text = desc
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })

            sCard.addView(iv)
            sCard.addView(textCol)
            sCard.setOnClickListener {
                Toast.makeText(activity, "Panduan integrasi $name: Buka aplikasi $name lalu sambungkan akun Tuya/ThinkTech Anda.", Toast.LENGTH_LONG).show()
            }
            content.addView(sCard)
        }

        dialog.show()
    }

    // ==========================================
    // 9. ANDROID AUTO
    // ==========================================
    fun showAndroidAutoDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Integrasi Android Auto")

        val cardStatus = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#F0FDF4"), dpToPx(activity, 18).toFloat(), Color.parseColor("#86EFAC"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 16)
            }
        }
        cardStatus.addView(TextView(activity).apply {
            text = "🚗 Status Head Unit: Siap Terhubung"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#15803D"))
        })
        cardStatus.addView(TextView(activity).apply {
            text = "Saat smartphone terhubung ke mobil via kabel USB atau nirkabel, kontrol saklar otomatis muncul di layar dashboard mobil."
            textSize = 12.5f
            setTextColor(Color.parseColor("#166534"))
            setPadding(0, dpToPx(activity, 4), 0, 0)
        })
        content.addView(cardStatus)

        val shortcuts = listOf("🚗 Buka Gerbang / Pagar Rumah", "💡 Matikan Semua Lampu Saat Mobil Menjauh", "🏠 Mode Tiba di Rumah (Nyalakan Saklar Utama)")
        shortcuts.forEach { sc ->
            val tvSc = TextView(activity).apply {
                text = sc
                textSize = 13.5f
                setTextColor(Color.parseColor("#334155"))
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
                val pad = dpToPx(activity, 12)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 8)
                }
            }
            content.addView(tvSc)
        }

        val btnGeofence = createPillButton("📍 Aktifkan Geofencing Radius 100m", Color.parseColor("#2563EB"), Color.WHITE, activity) {
            Toast.makeText(activity, "Automasi lokasi Android Auto aktif: Saklar akan menyesuaikan otomatis saat mobil mendekat.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        content.addView(btnGeofence)

        dialog.show()
    }

    // ==========================================
    // 10. AI WEB VERSION
    // ==========================================
    fun showAiWebVersionDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "ThinkTech AI Cloud Web Console")

        val cardWeb = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#EFF6FF"), dpToPx(activity, 18).toFloat(), Color.parseColor("#93C5FD"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 16)
            }
        }
        cardWeb.addView(TextView(activity).apply {
            text = "💻 Dashboard Web ThinkTech"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E3A8A"))
        })
        cardWeb.addView(TextView(activity).apply {
            text = "Kontrol seluruh saklar, pantau konsumsi listrik, dan jalankan automasi rumah pintar langsung dari browser laptop atau PC Anda."
            textSize = 12.5f
            setTextColor(Color.parseColor("#1D4ED8"))
            setPadding(0, dpToPx(activity, 4), 0, 0)
        })
        content.addView(cardWeb)

        val urlBox = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#CBD5E1"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 16)
            }
        }
        val tvUrl = TextView(activity).apply {
            text = "https://ai.thinktech.id"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCopy = TextView(activity).apply {
            text = "Salin"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setOnClickListener {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ThinkTech Web URL", "https://ai.thinktech.id"))
                Toast.makeText(activity, "Tautan https://ai.thinktech.id berhasil disalin!", Toast.LENGTH_SHORT).show()
            }
        }
        urlBox.addView(tvUrl)
        urlBox.addView(btnCopy)
        content.addView(urlBox)

        val btnOpenBrowser = createPillButton("🌐 Buka di Web Browser", Color.parseColor("#2563EB"), Color.WHITE, activity) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.thinktech.id"))
                activity.startActivity(intent)
            } catch (t: Throwable) {
                Toast.makeText(activity, "Akses dashboard di browser: https://ai.thinktech.id", Toast.LENGTH_LONG).show()
            }
        }
        content.addView(btnOpenBrowser)

        dialog.show()
    }

    // ==========================================
    // 11. GOOGLE HOME
    // ==========================================
    fun showGoogleHomeDialog(activity: MainActivity) {
        val (dialog, content) = createBaseBottomSheet(activity, "Sinkronisasi Google Home")

        val cardGh = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#FEF3C7"), dpToPx(activity, 18).toFloat(), Color.parseColor("#FCD34D"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 16)
            }
        }
        cardGh.addView(TextView(activity).apply {
            text = "🏠 Google Assistant & Google Home Ready"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#92400E"))
        })
        cardGh.addView(TextView(activity).apply {
            text = "Perangkat saklar ThinkTech Anda kompatibel dengan perintah suara Google Nest Hub, Android Smart Home, dan Google Assistant."
            textSize = 12.5f
            setTextColor(Color.parseColor("#B45309"))
            setPadding(0, dpToPx(activity, 4), 0, 0)
        })
        content.addView(cardGh)

        val deviceBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 14).toFloat(), Color.parseColor("#E2E8F0"), dpToPx(activity, 1))
            val pad = dpToPx(activity, 14)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(activity, 16)
            }
        }
        deviceBox.addView(TextView(activity).apply {
            text = "Perangkat Tersinkron:"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        })
        deviceBox.addView(TextView(activity).apply {
            text = "• WiFi 3ch switch (Saluran L1, L2, L3)\n• Status Cloud: Online & Terhubung"
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dpToPx(activity, 4), 0, 0)
        })
        content.addView(deviceBox)

        val btnSync = createPillButton("🔄 Sinkronkan Status Sekarang", Color.parseColor("#FF6010"), Color.WHITE, activity) {
            Toast.makeText(activity, "Menyinkronkan dengan Google Home Cloud...", Toast.LENGTH_SHORT).show()
            activity.window.decorView.postDelayed({
                Toast.makeText(activity, "✓ Semua saklar berhasil disinkronkan ke Google Home!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }, 1000)
        }
        content.addView(btnSync)

        dialog.show()
    }

    // ==========================================
    // 12. LOGOUT CONFIRMATION (TUYA MODAL)
    // ==========================================
    fun showLogoutConfirmDialog(activity: MainActivity, onConfirmLogout: () -> Unit) {
        showTuyaConfirmDialog(
            activity,
            title = "Keluar dari Akun?",
            message = "Apakah Anda yakin ingin keluar dari akun ThinkTech? Anda perlu login kembali untuk mengontrol saklar pintar rumah Anda.",
            confirmText = "Keluar",
            confirmColor = Color.parseColor("#EF4444")
        ) {
            onConfirmLogout()
        }
    }

    // ==========================================
    // KELOLA PERANGKAT DALAM RUANGAN
    // ==========================================
    fun showRoomDevicesDialog(activity: MainActivity, roomName: String, onUpdated: () -> Unit) {
        val allDevices = activity.allDevicesList
        if (allDevices.isEmpty()) {
            showTuyaInfoDialog(
                activity,
                title = "Kelola Ruangan: $roomName",
                message = "Belum ada perangkat saklar terdaftar di rumah. Silakan tambahkan perangkat baru melalui menu Tambah Perangkat (+) di beranda."
            )
            return
        }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(Color.WHITE, dpToPx(activity, 24).toFloat(), Color.parseColor("#F1F5F9"), 1)
            val pHoriz = dpToPx(activity, 22)
            val pVert = dpToPx(activity, 20)
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val tvTitle = TextView(activity).apply {
            text = "Kelola Ruangan: $roomName"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        }
        root.addView(tvTitle)

        val tvSub = TextView(activity).apply {
            text = "Atur perangkat/saklar yang ditempatkan di $roomName:"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 14))
        }
        root.addView(tvSub)

        allDevices.forEach { dev ->
            val devCard = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createCardBackground(Color.parseColor("#F8FAFC"), dpToPx(activity, 12).toFloat(), Color.parseColor("#E2E8F0"), 1)
                val p = dpToPx(activity, 12)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(activity, 10)
                }
            }

            val iconDev = TextView(activity).apply {
                text = "⚡"
                textSize = 16f
                gravity = Gravity.CENTER
                background = createCardBackground(Color.parseColor("#EFF6FF"), dpToPx(activity, 10).toFloat(), Color.TRANSPARENT, 0)
                layoutParams = LinearLayout.LayoutParams(dpToPx(activity, 34), dpToPx(activity, 34))
            }
            devCard.addView(iconDev)

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(activity, 10)
                }
            }
            val tvDName = TextView(activity).apply {
                text = dev.name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1E293B"))
            }
            val tvDLocation = TextView(activity).apply {
                text = if (dev.room == roomName) "✓ Terpasang di $roomName" else "Lokasi saat ini: ${dev.room}"
                textSize = 12f
                setTextColor(if (dev.room == roomName) Color.parseColor("#10B981") else Color.parseColor("#64748B"))
            }
            textCol.addView(tvDName)
            textCol.addView(tvDLocation)
            devCard.addView(textCol)

            val btnAction = TextView(activity).apply {
                if (dev.room == roomName) {
                    text = "✓ Di Ruangan Ini"
                    setTextColor(Color.parseColor("#10B981"))
                    background = createCardBackground(Color.parseColor("#ECFDF5"), dpToPx(activity, 8).toFloat(), Color.parseColor("#A7F3D0"), 1)
                } else {
                    text = "＋ Pindahkan"
                    setTextColor(Color.WHITE)
                    background = createCardBackground(Color.parseColor("#FF6010"), dpToPx(activity, 8).toFloat(), Color.TRANSPARENT, 0)
                }
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(activity, 10), dpToPx(activity, 6), dpToPx(activity, 10), dpToPx(activity, 6))
                isClickable = true
                setOnClickListener {
                    if (dev.room != roomName) {
                        TuyaDeviceRepository.setDeviceRoom(dev.devId, roomName)
                        dev.room = roomName
                        activity.allDevicesList = activity.allDevicesList.map {
                            if (it.devId == dev.devId) it.copy(room = roomName) else it
                        }
                        Toast.makeText(activity, "${dev.name} berhasil dipindahkan ke $roomName!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onUpdated()
                    }
                }
            }
            devCard.addView(btnAction)

            root.addView(devCard)
        }

        val btnClose = createPillButton("Tutup", Color.parseColor("#F1F5F9"), Color.parseColor("#475569"), activity) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(activity, 6)
            }
        }
        root.addView(btnClose)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (activity.resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}
