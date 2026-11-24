package pt.pine3.wall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * O "cérebro" do painel: serviço em primeiro plano que mantém a câmara a analisar movimento,
 * acorda o ecrã com movimento (ou quando uma regra do Home Assistant dispara, ex.: campainha)
 * e desliga-o X segundos depois da última atividade. Também mantém a ligação ao HA viva com o ecrã desligado.
 */
class MotionService : Service() {

    companion object {
        private const val CHANNEL = "motion"
        private const val NOTIF_ID = 1
        private const val IGNORE_AFTER_SLEEP_MS = 3500L

        @Volatile private var instance: MotionService? = null

        fun start(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, MotionService::class.java)) } catch (_: Exception) {}
        }

        /** Toque ou atividade do utilizador. */
        fun poke() { PanelState.lastTouch = System.currentTimeMillis(); instance?.main?.post { instance?.onActivity() } }

        fun isAdminActive(ctx: Context): Boolean {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var detector: MotionDetector? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var sleptAt = 0L
    private var keepUntil = 0L
    private val sleepRunnable = Runnable { goToSleep() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            else startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        if (detector == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pine3:cpu").apply { setReferenceCounted(false); acquire() }
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pine3:wifi").apply { setReferenceCounted(false); acquire() }
            detector = MotionDetector(this, { prefs.sensitivity }) { main.post { reportMotion(); onActivity() } }.also { it.start() }
            scheduleSleep()
            PanelState.sleepListeners += screenListener
            main.postDelayed(periodic, 60_000)
        }
        Ha.onWake = { rule -> holdAwake(rule.holdSec) }
        // comandos dos serviços pine3_wall.*: acordar (o ecrã principal trata do resto: página, câmara, mensagem, screensaver)
        Ha.onPanelCommand = { cmd ->
            when (cmd.optString("command")) {
                "wake" -> holdAwake(cmd.optInt("seconds", 60))
                "show_page" -> holdAwake(60)
                "show_camera" -> holdAwake(cmd.optInt("seconds", 120))
                "notify" -> if (cmd.optBoolean("wake", true)) holdAwake(cmd.optInt("seconds", 30))
                "screensaver" -> if (!cmd.optBoolean("on")) holdAwake(60)
            }
        }
        Ha.onPanelConnected = { reportAll() }
        Ha.configure(this)
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        Ha.onWake = null
        Ha.onPanelCommand = null
        Ha.onPanelConnected = null
        PanelState.sleepListeners -= screenListener
        main.removeCallbacksAndMessages(null)
        detector?.stop(); detector = null
        cpuLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        PanelState.set(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- estado para as entidades do HA

    private var motionOn = false
    private var motionReportedAt = 0L
    private val motionOff = Runnable { motionOn = false; Ha.reportState("motion" to false) }
    private val screenListener: (Boolean) -> Unit = { sleeping -> Ha.reportState("screen_on" to !sleeping) }
    private val periodic = object : Runnable {
        override fun run() { reportDevice(); main.postDelayed(this, 5 * 60_000L) }
    }

    /** Movimento visto pela câmara frontal: "on" logo, "off" 30 s depois do último (no máximo um envio a cada 10 s). */
    private fun reportMotion() {
        val now = SystemClock.elapsedRealtime()
        if (!motionOn || now - motionReportedAt > 10_000) { Ha.reportState("motion" to true); motionReportedAt = now }
        motionOn = true
        main.removeCallbacks(motionOff); main.postDelayed(motionOff, 30_000)
    }

    /** Bateria, carga e brilho do ecrã. */
    private fun reportDevice() {
        val values = ArrayList<Pair<String, Any>>()
        registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b ->
            val level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) values += "battery" to (level * 100 / scale)
            values += "charging" to (b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0)
        }
        try { values += "brightness" to (android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) * 100 / 255) } catch (_: Exception) {}
        Ha.reportState(*values.toTypedArray())
    }

    private fun reportAll() {
        Ha.reportState("screen_on" to !PanelState.sleeping, "motion" to motionOn)
        if (Ha.currentPage.isNotEmpty()) Ha.reportState("page" to Ha.currentPage)
        reportDevice()
    }

    private fun onActivity() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // também acorda se o próprio Android apagou o ecrã (ex.: tempo limite do sistema nas definições)
        if (PanelState.sleeping || !pm.isInteractive) {
            if (SystemClock.elapsedRealtime() - sleptAt < IGNORE_AFTER_SLEEP_MS) return
            wake()
        }
        scheduleSleep()
    }

    private fun scheduleSleep() {
        main.removeCallbacks(sleepRunnable)
        val delay = maxOf(prefs.timeoutSec * 1000L, keepUntil - SystemClock.elapsedRealtime())
        main.postDelayed(sleepRunnable, delay)
    }

    /** Uma regra do HA disparou: acorda e mantém o ecrã ligado pelo menos estes segundos. */
    private fun holdAwake(seconds: Int) {
        keepUntil = SystemClock.elapsedRealtime() + seconds * 1000L
        if (PanelState.sleeping) wake()
        scheduleSleep()
    }

    @Suppress("DEPRECATION")
    private fun wake() {
        PanelState.set(false)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "pine3:wake",
        ).acquire(5000)
        if (PanelState.settingsVisible) return // as definições já estão à frente: só liga o ecrã
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {}
    }

    private fun goToSleep() {
        if (PanelState.sleeping) return
        sleptAt = SystemClock.elapsedRealtime()
        PanelState.set(true)
        if (isAdminActive(this)) {
            // deixa o ecrã desvanecer para preto antes de o bloquear (mesma duração do acordar)
            main.postDelayed({
                if (PanelState.sleeping) try { (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow() } catch (_: Exception) {}
            }, PanelState.FADE_MS + 60)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open).setOngoing(true).build()
    }
}
