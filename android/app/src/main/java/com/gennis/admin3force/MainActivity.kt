package com.gennis.admin3force

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hito LOGIN de Admin 3FORCE.
 *
 * Flujo:
 *  - Sin sesión guardada → WebView del panel real (Gennis teclea sus datos y
 *    resuelve el captcha EN LA PÁGINA; la app no tiene campos de credenciales).
 *  - Al salir de /auth/login se escanea storage + cookies buscando el JWT;
 *    si aparece, se guarda cifrado (Keystore) y se pasa a la pantalla Sesión.
 *  - Con sesión guardada y access vigente → bloqueo biométrico → Sesión.
 *  - Con access expirado NO se inventa refresh: se vuelve al WebView.
 */
class MainActivity : AppCompatActivity() {

    private enum class State { LOGIN, LOCK, SESSION }

    private lateinit var store: SessionStore
    private lateinit var capture: SessionCapture
    private lateinit var webView: WebView
    private lateinit var loginContainer: View
    private lateinit var lockContainer: View
    private lateinit var sessionContainer: View
    private lateinit var loginStatus: TextView
    private lateinit var lockStatus: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var claimsContainer: LinearLayout
    private lateinit var btnBackToSession: Button

    private var state: State = State.LOGIN
    private var browsingMode = false       // true cuando se abrió el panel desde Sesión
    private var captureDone = false        // ya se capturó en este ciclo de login
    private var lockPromptShown = false
    private var sessionExpSec: Long = -1

    private val handler = Handler(Looper.getMainLooper())
    private val countdownTick = object : Runnable {
        override fun run() {
            if (state != State.SESSION) return
            val remaining = sessionExpSec - nowSec()
            if (remaining <= 0) {
                showLogin("El token de acceso expiró. Entra de nuevo en el panel para renovar la sesión.")
                return
            }
            txtCountdown.text = "El token de acceso expira en ${fmtDuration(remaining)}"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = SessionStore(this)

        loginContainer = findViewById(R.id.loginContainer)
        lockContainer = findViewById(R.id.lockContainer)
        sessionContainer = findViewById(R.id.sessionContainer)
        loginStatus = findViewById(R.id.loginStatus)
        lockStatus = findViewById(R.id.lockStatus)
        txtCountdown = findViewById(R.id.txtCountdown)
        claimsContainer = findViewById(R.id.claimsContainer)
        btnBackToSession = findViewById(R.id.btnBackToSession)
        webView = findViewById(R.id.webView)

        setupWebView()
        capture = SessionCapture(webView)

        findViewById<Button>(R.id.btnUnlock).setOnClickListener { promptUnlock() }
        findViewById<Button>(R.id.btnLockLogout).setOnClickListener { logout() }
        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
        findViewById<Button>(R.id.btnOpenPanel).setOnClickListener { openPanel() }
        btnBackToSession.setOnClickListener {
            browsingMode = false
            showSessionFromStore()
        }

        // Estado inicial según la sesión guardada.
        val saved = loadSavedSession()
        if (saved != null) {
            val exp = JwtUtils.decodePayload(saved.optString("accessToken"))?.optLong("exp", -1) ?: -1
            if (exp > nowSec()) {
                showLock()
            } else {
                showLogin("La sesión guardada expiró. Entra de nuevo en el panel para renovarla.")
            }
        } else {
            showLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        if (state == State.SESSION) {
            handler.removeCallbacks(countdownTick)
            handler.post(countdownTick)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(countdownTick)
    }

    // ---------------- WebView / Login ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                maybeCapture(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                maybeCapture(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                maybeCapture(url)
            }
        }
    }

    /** Si ya salimos de /auth/login (y no estamos navegando desde Sesión), busca el JWT. */
    private fun maybeCapture(url: String?) {
        if (url == null || state != State.LOGIN || browsingMode || captureDone) return
        val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { return }
        if (uri.host != PanelConfig.HOST) return
        if ((uri.path ?: "").contains(PanelConfig.LOGIN_PATH)) return
        // Varios intentos: el token puede escribirse un instante después de navegar.
        for (delay in longArrayOf(0, 900, 2200)) {
            handler.postDelayed({
                if (state == State.LOGIN && !browsingMode && !captureDone) {
                    capture.attempt { sessionJson ->
                        captureDone = true
                        store.save(sessionJson.toString())
                        runOnUiThread {
                            Toast.makeText(this, "Sesión detectada y guardada de forma cifrada", Toast.LENGTH_LONG).show()
                            showSession(sessionJson)
                        }
                    }
                }
            }, delay)
        }
    }

    private fun showLogin(statusMsg: String? = null) {
        state = State.LOGIN
        handler.removeCallbacks(countdownTick)
        browsingMode = false
        btnBackToSession.visibility = View.GONE
        loginContainer.visibility = View.VISIBLE
        lockContainer.visibility = View.GONE
        sessionContainer.visibility = View.GONE
        if (statusMsg != null) {
            loginStatus.text = statusMsg
            loginStatus.visibility = View.VISIBLE
        } else {
            loginStatus.visibility = View.GONE
        }
        if (webView.url == null || statusMsg != null) {
            webView.loadUrl(PanelConfig.LOGIN_URL)
        }
    }

    private fun openPanel() {
        browsingMode = true
        state = State.LOGIN
        handler.removeCallbacks(countdownTick)
        btnBackToSession.visibility = View.VISIBLE
        loginStatus.visibility = View.GONE
        loginContainer.visibility = View.VISIBLE
        lockContainer.visibility = View.GONE
        sessionContainer.visibility = View.GONE
        webView.loadUrl(PanelConfig.BASE_URL)
    }

    // ---------------- Bloqueo biométrico ----------------

    private fun showLock() {
        state = State.LOCK
        loginContainer.visibility = View.GONE
        lockContainer.visibility = View.VISIBLE
        sessionContainer.visibility = View.GONE
        lockStatus.visibility = View.GONE
        if (!lockPromptShown) {
            lockPromptShown = true
            promptUnlock()
        }
    }

    private fun promptUnlock() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(authenticators)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (can != BiometricManager.BIOMETRIC_SUCCESS && !keyguard.isDeviceSecure) {
            // El teléfono no tiene huella/rostro ni PIN/patrón: no hay con qué bloquear.
            showSessionFromStore()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    showSessionFromStore()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lockStatus.text = errString
                    lockStatus.visibility = View.VISIBLE
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Admin 3FORCE")
            .setSubtitle("Desbloquea para ver tu sesión")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(builder.build())
    }

    // ---------------- Pantalla de Sesión ----------------

    private fun loadSavedSession(): JSONObject? {
        val raw = store.load() ?: return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    private fun showSessionFromStore() {
        val saved = loadSavedSession()
        if (saved == null) {
            showLogin()
            return
        }
        showSession(saved)
    }

    /** Muestra SOLO datos reales derivados del token capturado. Sin claims → sin fila. */
    private fun showSession(sessionJson: JSONObject) {
        val accessToken = sessionJson.optString("accessToken")
        val payload = JwtUtils.decodePayload(accessToken)
        val exp = payload?.optLong("exp", -1) ?: -1
        if (exp in 1..nowSec()) {
            showLogin("El token de acceso expiró. Entra de nuevo en el panel para renovar la sesión.")
            return
        }
        sessionExpSec = exp
        state = State.SESSION
        loginContainer.visibility = View.GONE
        lockContainer.visibility = View.GONE
        sessionContainer.visibility = View.VISIBLE

        claimsContainer.removeAllViews()
        addRow("Panel", sessionJson.optString("panelHost", PanelConfig.HOST))
        if (payload != null) {
            claimRow(payload, "sub", "Usuario (sub)")
            claimRow(payload, "username", "Usuario")
            claimRow(payload, "name", "Nombre")
            claimRow(payload, "preferred_username", "Usuario")
            claimRow(payload, "email", "Correo")
            claimRow(payload, "role", "Rol")
            claimRow(payload, "roles", "Roles")
            claimRow(payload, "iss", "Emisor")
            claimRow(payload, "iat", "Emitido el", isDate = true)
            claimRow(payload, "exp", "Expira el", isDate = true)
        }
        val hasRefresh = sessionJson.has("refreshToken")
        addRow("Tokens guardados", if (hasRefresh) "access + refresh" else "access")
        addRow("Fuente del access", sessionJson.optString("accessSource", "") +
            sessionJson.optString("accessKey", "").let { if (it.isNotEmpty()) " · $it" else "" })
        val capturedAt = sessionJson.optLong("capturedAt", 0)
        if (capturedAt > 0) addRow("Sesión capturada", fmtDate(capturedAt / 1000))

        handler.removeCallbacks(countdownTick)
        handler.post(countdownTick)
    }

    private fun claimRow(payload: JSONObject, key: String, label: String, isDate: Boolean = false) {
        if (!payload.has(key)) return
        val raw = payload.opt(key) ?: return
        val value = when {
            isDate -> (raw as? Number)?.let { fmtDate(it.toLong()) } ?: raw.toString()
            raw is org.json.JSONArray -> (0 until raw.length()).joinToString(", ") { raw.optString(it) }
            else -> raw.toString()
        }
        if (value.isNotEmpty()) addRow(label, value)
    }

    private fun addRow(label: String, value: String) {
        if (value.isEmpty()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }
        val lpLabel = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        val lpValue = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f)
        row.addView(TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text2))
            textSize = 13f
        }, lpLabel)
        row.addView(TextView(this).apply {
            text = value
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
            textSize = 13f
        }, lpValue)
        claimsContainer.addView(row)
    }

    // ---------------- Cerrar sesión ----------------

    private fun logout() {
        store.clear()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        WebStorage.getInstance().deleteAllData()
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        captureDone = false
        lockPromptShown = false
        showLogin("Sesión cerrada. Entra de nuevo en el panel.")
    }

    // ---------------- Utilidades ----------------

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    private fun fmtDate(epochSec: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(epochSec * 1000))

    private fun fmtDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}
