package com.thinktech.smarthome.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.TuyaAuthRepository
import com.thinktech.smarthome.ui.home.MainActivity
import com.thinktech.smarthome.ui.login.LoginActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var splashStage: LinearLayout
    private lateinit var splashLogoView: ThinkTechSplashLogoView
    private lateinit var tvWordThink: TextView
    private lateinit var tvWordTech: TextView
    private var splashAnimator: ValueAnimator? = null

    private val authRepository = TuyaAuthRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wordInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    private var isNavigating = false
    private var isAnimationFinished = false
    private var isAutoLoginComplete = false
    private var forceLoginScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seamless #F4F6FB immersion matching ThinkTech Splash HTML canvas
        window.statusBarColor = Color.parseColor("#F4F6FB")
        window.navigationBarColor = Color.parseColor("#F4F6FB")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        )

        setContentView(R.layout.activity_splash)

        splashStage = findViewById(R.id.splashStage)
        splashLogoView = findViewById(R.id.splashLogoView)
        tvWordThink = findViewById(R.id.tvWordThink)
        tvWordTech = findViewById(R.id.tvWordTech)

        // Set initial masked state before first frame draws
        splashStage.alpha = 0f
        val initialSlidePx = 64f * resources.displayMetrics.density
        tvWordThink.translationY = initialSlidePx
        tvWordTech.translationY = initialSlidePx

        startSplashAnimation()

        // Check login state and perform silent auto-login in parallel
        initiateAutoLogin()

        // Safety fallback timer (4000ms) in case network takes too long
        mainHandler.postDelayed({
            Log.d("SplashActivity", "Safety timer triggered navigation")
            isAnimationFinished = true
            isAutoLoginComplete = true
            checkAndNavigate()
        }, 4000)
    }

    private fun startSplashAnimation() {
        // Start immediately at pct=8 (skipping idle dead air after Android window splash)
        // through pct=82 (full logo draw + wordmark reveal + 2 crisp wireless pulses) in 2600ms.
        splashAnimator = ValueAnimator.ofFloat(8f, 82f).apply {
            duration = 2600L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val pct = anim.animatedValue as Float
                applyFrame(pct)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimationFinished = true
                    checkAndNavigate()
                }
            })
            start()
        }
    }

    private fun applyFrame(pct: Float) {
        // Quick crisp stage fade-in (8% -> 11%)
        splashStage.alpha = when {
            pct <= 8f -> 0f
            pct < 11f -> wordInterpolator.getInterpolation((pct - 8f) / 3f)
            else -> 1f
        }

        // Vector house + T cutout + cyan wireless signal arcs
        splashLogoView.timelinePercent = pct

        // Wordmark reveal inside clipped mask (w1: 42%->50%, w2: 45%->53%)
        val wordHeight = (tvWordThink.height.takeIf { it > 0 }?.toFloat()
            ?: (52f * resources.displayMetrics.density)) * 1.12f

        val w1 = evalSegment(pct, 42f, 50f)
        val w2 = evalSegment(pct, 45f, 53f)
        tvWordThink.translationY = wordHeight * (1f - w1)
        tvWordTech.translationY = wordHeight * (1f - w2)
    }

    private fun evalSegment(pct: Float, start: Float, end: Float): Float {
        if (pct <= start) return 0f
        if (pct >= end) return 1f
        return wordInterpolator.getInterpolation((pct - start) / (end - start))
    }

    private fun initiateAutoLogin() {
        if (authRepository.isTuyaSdkLoggedIn()) {
            Log.d("SplashActivity", "User already active in Tuya SDK session.")
            isAutoLoginComplete = true
            return
        }

        if (authRepository.isUserLoggedIn() && authRepository.hasSavedCredentials()) {
            Log.d("SplashActivity", "Starting silent auto-login in background...")
            authRepository.silentAutoLogin(
                onSuccess = { user ->
                    Log.d("SplashActivity", "Silent auto-login succeeded: ${user.nickName ?: user.username}")
                    isAutoLoginComplete = true
                    checkAndNavigate()
                },
                onError = { code, error ->
                    Log.w("SplashActivity", "Silent auto-login failed ($code): $error")
                    isAutoLoginComplete = true
                    if (code.contains("PASSWORD", ignoreCase = true) ||
                        code.contains("USER_NOT_EXISTS", ignoreCase = true) ||
                        code.contains("FORBIDDEN", ignoreCase = true)) {
                        forceLoginScreen = true
                    }
                    checkAndNavigate()
                }
            )
        } else {
            isAutoLoginComplete = true
        }
    }

    private fun checkAndNavigate() {
        if (isNavigating) return
        if (!isAnimationFinished) return
        if (authRepository.isUserLoggedIn() && !isAutoLoginComplete) return

        isNavigating = true

        val shouldGoToMain = !forceLoginScreen && authRepository.isUserLoggedIn()
        val destinationClass = if (shouldGoToMain) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }

        Log.d("SplashActivity", "Navigating to: ${destinationClass.simpleName} (isLoggedIn: ${authRepository.isUserLoggedIn()})")
        val intent = Intent(this, destinationClass)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        splashAnimator?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
