package com.thinktech.smarthome.ui.home

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.thinktech.smarthome.data.AppLanguageManager
import com.thinktech.smarthome.databinding.DialogHarmonyVoiceAiBinding
import java.util.Locale

/**
 * Manages the Full-Screen Minimalist White Voice Assistant UI (edge-to-edge rainbow wave +
 * luminous 3D aurora orb), real Android SpeechRecognizer (id-ID), TextToSpeech spoken feedback,
 * fallback RecognizerIntent, and direct Tuya command dispatch.
 */
class HarmonyVoiceAssistantController(
    private val activity: AppCompatActivity,
    private val micPermissionLauncher: ActivityResultLauncher<String>,
    private val fallbackSpeechLauncher: ActivityResultLauncher<Intent>,
    private val onExecuteCommand: (spokenText: String) -> Pair<Boolean, String>
) {

    private val handler = Handler(Looper.getMainLooper())
    private var voiceDialog: Dialog? = null
    private var screenBinding: DialogHarmonyVoiceAiBinding? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isListening = false
    private var pendingAutoListenAfterPermission = false

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        runCatching {
            textToSpeech = TextToSpeech(activity.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val targetLocale = AppLanguageManager.getTtsLocale(activity)
                    val res = textToSpeech?.setLanguage(targetLocale)
                    isTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!isTtsReady) {
                        textToSpeech?.language = Locale.getDefault()
                        isTtsReady = true
                    }
                }
            }
        }
    }

    fun openAssistant(autoListen: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (autoListen && !hasMicPermission()) {
            pendingAutoListenAfterPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        showFullScreenVoiceUi(startListeningImmediately = autoListen)
    }

    fun onMicPermissionResult(granted: Boolean) {
        val shouldStart = pendingAutoListenAfterPermission
        pendingAutoListenAfterPermission = false
        if (granted) {
            if (voiceDialog?.isShowing == true) {
                startVoiceRecognition()
            } else {
                showFullScreenVoiceUi(startListeningImmediately = shouldStart)
            }
        } else {
            Toast.makeText(
                activity,
                "Izin mikrofon ditolak — Anda tetap dapat mengetik perintah di bawah.",
                Toast.LENGTH_LONG
            ).show()
            if (voiceDialog?.isShowing != true) {
                showFullScreenVoiceUi(startListeningImmediately = false)
            }
            screenBinding?.layoutVoiceFallbackInput?.visibility = View.VISIBLE
        }
    }

    fun onFallbackSpeechResult(spokenText: String?) {
        if (spokenText.isNullOrBlank()) return
        if (voiceDialog?.isShowing != true) {
            showFullScreenVoiceUi(startListeningImmediately = false)
        }
        handleRecognizedText(spokenText)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showFullScreenVoiceUi(startListeningImmediately: Boolean) {
        stopVoiceRecognition()
        voiceDialog?.dismiss()

        val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogHarmonyVoiceAiBinding.inflate(activity.layoutInflater)
        dialog.setContentView(binding.root)

        dialog.window?.let { win ->
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            win.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            win.statusBarColor = Color.WHITE
            @Suppress("DEPRECATION")
            win.navigationBarColor = Color.WHITE
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowInsetsControllerCompat(win, win.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }

        // Preserve clean system bar padding across all notched & gesture-nav Android devices
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootVoiceScreen) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        voiceDialog = dialog
        screenBinding = binding

        binding.btnCloseVoiceSheet.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
        }

        // Single tap on the luminous aurora orb toggles listening
        binding.btnVoiceMicOrb.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(85L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            }.start()

            if (isListening) {
                stopVoiceRecognition()
                setIdleVisual("Ketuk bola suara untuk mulai berbicara")
            } else {
                if (!hasMicPermission()) {
                    pendingAutoListenAfterPermission = true
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    startVoiceRecognition()
                }
            }
        }

        // Long-press on the orb reveals optional minimalist text input (for silent/noisy rooms)
        binding.btnVoiceMicOrb.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val isShown = binding.layoutVoiceFallbackInput.visibility == View.VISIBLE
            binding.layoutVoiceFallbackInput.visibility = if (isShown) View.GONE else View.VISIBLE
            true
        }

        // Optional fallback text command handler
        val submitTyped = {
            val typed = binding.etVoiceTextCommand.text?.toString()?.trim().orEmpty()
            if (typed.isNotEmpty()) {
                binding.etVoiceTextCommand.setText("")
                stopVoiceRecognition()
                handleRecognizedText(typed)
            }
        }
        binding.btnSendVoiceText.setOnClickListener { submitTyped() }
        binding.etVoiceTextCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitTyped()
                true
            } else false
        }

        dialog.setOnDismissListener {
            stopVoiceRecognition()
            binding.voiceWaveView.stopAnim()
            binding.btnVoiceMicOrb.stopAnim()
            screenBinding = null
            voiceDialog = null
        }

        // Subtle smooth entrance fade
        binding.rootVoiceScreen.alpha = 0f
        dialog.show()
        binding.rootVoiceScreen.animate().alpha(1f).setDuration(180L).start()

        if (startListeningImmediately) {
            setListeningVisual()
            handler.postDelayed({
                if (voiceDialog?.isShowing == true) {
                    startVoiceRecognition()
                }
            }, 180L)
        } else {
            setIdleVisual("Ketuk bola suara untuk mulai berbicara")
        }
    }

    private fun startVoiceRecognition() {
        val b = screenBinding ?: return
        stopVoiceRecognition()

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            launchFallbackSpeechIntent()
            return
        }

        try {
            runCatching { textToSpeech?.stop() }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (speechRecognizer !== recognizer) return
                    isListening = true
                    setListeningVisual()
                }

                override fun onBeginningOfSpeech() {
                    if (speechRecognizer !== recognizer) return
                    b.tvVoiceStatusLine1.text = "Kami mendengarkan..."
                    b.tvVoiceStatusLine2.text = "Silakan lanjutkan berbicara"
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (speechRecognizer !== recognizer) return
                    b.voiceWaveView.updateRmsDb(rmsdB)
                    b.btnVoiceMicOrb.updateRmsDb(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (speechRecognizer !== recognizer) return
                    isListening = false
                    b.voiceWaveView.setListening(false)
                    b.btnVoiceMicOrb.setListening(false)
                    b.tvVoiceStatusLine1.text = "Memproses perintah..."
                    b.tvVoiceStatusLine2.text = "Mohon tunggu sebentar"
                }

                override fun onError(error: Int) {
                    if (speechRecognizer !== recognizer) return
                    isListening = false
                    b.voiceWaveView.setListening(false)
                    b.btnVoiceMicOrb.setListening(false)
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Suara belum dikenali — ketuk bola suara untuk ulangi"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdeteksi — ketuk bola suara lagi"
                        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon sedang digunakan aplikasi lain"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Perlu koneksi internet untuk pengenalan suara"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon diperlukan"
                        else -> "Layanan suara sibuk — ketuk bola suara atau tahan untuk mengetik"
                    }
                    setIdleVisual(msg)
                }

                override fun onResults(results: Bundle?) {
                    if (speechRecognizer !== recognizer) return
                    isListening = false
                    b.voiceWaveView.setListening(false)
                    b.btnVoiceMicOrb.setListening(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        handleRecognizedText(best)
                    } else {
                        setIdleVisual("Suara belum terbaca — ketuk bola suara untuk ulangi")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (speechRecognizer !== recognizer) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        b.tvVoiceTranscript.text = "“$partial”"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val speechLang = AppLanguageManager.getSpeechLocaleTag(activity)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            isListening = true
            setListeningVisual()
            recognizer.startListening(intent)
        } catch (_: Throwable) {
            isListening = false
            launchFallbackSpeechIntent()
        }
    }

    private fun launchFallbackSpeechIntent() {
        try {
            val speechLang = AppLanguageManager.getSpeechLocaleTag(activity)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLang)
                putExtra(RecognizerIntent.EXTRA_PROMPT, AppLanguageManager.tr(activity, "Apa yang ingin Anda lakukan?"))
            }
            fallbackSpeechLauncher.launch(intent)
        } catch (_: Throwable) {
            screenBinding?.layoutVoiceFallbackInput?.visibility = View.VISIBLE
            setIdleVisual("Layanan suara Google tidak tersedia — ketik perintah di bawah")
        }
    }

    private fun setListeningVisual() {
        val b = screenBinding ?: return
        b.tvVoiceStatusLine1.text = AppLanguageManager.tr(activity, "Kami mendengarkan...")
        b.tvVoiceStatusLine1.setTextColor(Color.parseColor("#6B7280"))
        b.tvVoiceStatusLine2.text = AppLanguageManager.tr(activity, "Apa yang ingin Anda lakukan?")
        b.tvVoiceStatusLine2.visibility = View.VISIBLE
        b.tvVoiceTranscript.text = AppLanguageManager.getCurrentLanguage(activity).voiceExamplePreview
        b.tvVoiceTranscript.setTextColor(Color.parseColor("#181C24"))
        b.layoutVoiceResultCard.visibility = View.GONE
        b.tvVoiceMicHint.visibility = View.GONE
        b.voiceWaveView.setListening(true)
        b.btnVoiceMicOrb.setListening(true)
    }

    private fun setIdleVisual(hint: String) {
        val b = screenBinding ?: return
        b.voiceWaveView.setListening(false)
        b.btnVoiceMicOrb.setListening(false)
        b.tvVoiceStatusLine1.text = AppLanguageManager.tr(activity, "Asisten Suara Siap")
        b.tvVoiceStatusLine1.setTextColor(Color.parseColor("#6B7280"))
        b.tvVoiceStatusLine2.text = AppLanguageManager.tr(activity, "Apa yang ingin Anda lakukan?")
        b.tvVoiceStatusLine2.visibility = View.VISIBLE
        b.tvVoiceMicHint.text = hint
        b.tvVoiceMicHint.visibility = View.VISIBLE
    }

    private fun handleRecognizedText(spokenText: String) {
        val b = screenBinding ?: return
        b.tvVoiceTranscript.text = "“$spokenText”"
        val (handled, replyMessage) = onExecuteCommand(spokenText)

        b.layoutVoiceResultCard.visibility = View.VISIBLE
        b.tvVoiceResultText.text = replyMessage
        if (handled) {
            b.tvVoiceStatusLine1.text = "Perintah berhasil dijalankan"
            b.tvVoiceStatusLine1.setTextColor(Color.parseColor("#00996B"))
            b.tvVoiceStatusLine2.visibility = View.GONE
            b.tvVoiceResultText.setTextColor(Color.parseColor("#0A59F7"))
            b.ivVoiceResultIcon.visibility = View.VISIBLE
            b.tvVoiceMicHint.text = "Ketuk bola suara untuk perintah berikutnya"
            b.tvVoiceMicHint.visibility = View.VISIBLE
        } else {
            b.tvVoiceStatusLine1.text = "Perintah belum dikenali"
            b.tvVoiceStatusLine1.setTextColor(Color.parseColor("#D97706"))
            b.tvVoiceStatusLine2.visibility = View.GONE
            b.tvVoiceResultText.setTextColor(Color.parseColor("#666B75"))
            b.ivVoiceResultIcon.visibility = View.GONE
            b.tvVoiceMicHint.text = "Coba: \"Geser kamera ke kiri\", \"Deteksi gerakan\", atau \"Bisa apa saja\""
            b.tvVoiceMicHint.visibility = View.VISIBLE
        }

        speakResponse(replyMessage)
    }

    private fun speakResponse(text: String) {
        if (!isTtsReady) return
        runCatching {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "thinktech_voice_reply")
        }
    }

    fun dismissIfShowing() {
        stopVoiceRecognition()
        voiceDialog?.dismiss()
    }

    private fun stopVoiceRecognition() {
        isListening = false
        val active = speechRecognizer
        speechRecognizer = null
        try {
            active?.cancel()
            active?.destroy()
        } catch (_: Throwable) {}
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        stopVoiceRecognition()
        voiceDialog?.dismiss()
        voiceDialog = null
        screenBinding = null
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
    }
}
