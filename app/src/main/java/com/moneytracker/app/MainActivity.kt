package com.moneytracker.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moneytracker.app.ui.AppNav
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.LocalHideBalance
import com.moneytracker.app.ui.lock.LockScreen
import com.moneytracker.app.ui.lock.OnboardingScreen
import com.moneytracker.app.ui.theme.MoneyTrackerTheme
import com.moneytracker.app.work.Reminders
import com.moneytracker.app.ui.fx.Celebration
import com.moneytracker.app.ui.fx.CelebrationOverlay
import com.moneytracker.app.ui.fx.IntroScreen

/** State UI global yang bisa diubah dari Pengaturan. */
class UiState(app: MoneyTrackerApp) {
    var onboarded by mutableStateOf(app.security.hasPin)
    var locked by mutableStateOf(app.security.hasPin)
    var theme by mutableStateOf(app.security.theme)
    var hideBalances by mutableStateOf(app.security.hideBalances)
    var motion by mutableStateOf(app.security.motionEnabled)
    var showIntro by mutableStateOf(false)
    /** Animasi umpan balik transaksi yang sedang tampil. */
    var celebration by mutableStateOf<Celebration?>(null)

    fun celebrate(c: Celebration) { if (motion) celebration = c }
}

val LocalUi = staticCompositionLocalOf<UiState> { error("UiState tidak tersedia") }
val LocalActivity = staticCompositionLocalOf<MainActivity> { error("Activity tidak tersedia") }

class MainActivity : FragmentActivity() {
    private lateinit var ui: UiState
    /** Layar yang diminta notifikasi (mis. "tx_new"), diproses oleh AppNav. */
    val pendingOpen = mutableStateOf<String?>(null)
    private var backgroundedAt = 0L
    private val app get() = application as MoneyTrackerApp

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundedAt = System.currentTimeMillis() }
        override fun onStart(owner: LifecycleOwner) {
            if (!::ui.isInitialized || !app.security.hasPin || backgroundedAt == 0L) return
            if (System.currentTimeMillis() - backgroundedAt > app.security.lockTimeoutSec * 1000L) ui.locked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureFlag()
        applyRefreshRate()
        ui = UiState(app)
        pendingOpen.value = intent?.getStringExtra(Reminders.EXTRA_OPEN)
        // Intro hanya saat cold start (bukan rotasi layar / dibuka dari notifikasi)
        ui.showIntro = savedInstanceState == null && app.security.introEnabled && pendingOpen.value == null
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        setContent {
            CompositionLocalProvider(LocalApp provides app, LocalUi provides ui, LocalActivity provides this, LocalHideBalance provides ui.hideBalances) {
                MoneyTrackerTheme(ui.theme) {
                    // Surface (bukan Box) agar warna teks default = onBackground. Tanpa ini teks
                    // di layar sambutan & PIN berwarna hitam dan tidak terlihat pada tema gelap.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                      Box(Modifier.fillMaxSize()) {
                        if (!ui.onboarded) {
                            OnboardingScreen(onDone = { ui.onboarded = true; ui.locked = false })
                        } else {
                            // Navigasi tetap hidup di bawah layar kunci, sehingga posisi layar & hasil
                            // pemilih file/foto tidak hilang saat aplikasi terkunci otomatis.
                            Box(if (ui.locked) Modifier.clearAndSetSemantics { } else Modifier) { AppNav() }
                            AnimatedVisibility(ui.locked, enter = fadeIn(), exit = fadeOut()) {
                                BackHandler { moveTaskToBack(true) }
                                // Menahan semua sentuhan agar tidak tembus ke layar di bawahnya.
                                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                                    LockScreen(onUnlocked = { ui.locked = false })
                                }
                            }
                        }
                        ui.celebration?.let { c ->
                            CelebrationOverlay(c, onDone = { if (ui.celebration?.id == c.id) ui.celebration = null })
                        }
                        if (ui.showIntro) IntroScreen(onDone = { ui.showIntro = false })
                      }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    fun applySecureFlag() {
        if (app.security.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Reminders.EXTRA_OPEN)?.let { pendingOpen.value = it }
    }

    override fun onResume() {
        super.onResume()
        applyRefreshRate() // beberapa perangkat mereset mode layar saat aplikasi kembali aktif
    }

    /**
     * Meminta refresh rate tertinggi yang didukung layar (mis. 90/120/144 Hz) pada resolusi saat ini,
     * sehingga animasi & scroll Compose dirender pada FPS maksimal. Bila dimatikan di Pengaturan,
     * sistem kembali memilih mode default (hemat baterai).
     */
    fun applyRefreshRate() {
        val d = (if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return
        val params = window.attributes
        if (app.security.highRefreshRate) {
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            params.preferredDisplayModeId = best.modeId
            params.preferredRefreshRate = best.refreshRate
        } else {
            params.preferredDisplayModeId = 0
            params.preferredRefreshRate = 0f
        }
        window.attributes = params
    }

    /** Refresh rate maksimum layar (untuk ditampilkan di Pengaturan). */
    fun maxRefreshRate(): Int {
        val d = (if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return 60
        return d.supportedModes.maxOfOrNull { it.refreshRate }?.toInt() ?: 60
    }

    fun canUseBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticateBiometric(onSuccess: () -> Unit) {
        if (!canUseBiometric()) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Buka MoneyTracker")
                .setSubtitle("Gunakan sidik jari atau wajah")
                .setNegativeButtonText("Gunakan PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    /** Memulai ulang aplikasi (dipakai setelah restore / hapus akun). */
    fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
