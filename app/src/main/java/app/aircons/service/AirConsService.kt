package app.aircons.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.aircons.AirConsApp
import app.aircons.MainActivity
import app.aircons.R
import app.aircons.aap.AapClient
import app.aircons.core.ControllerEngine
import app.aircons.core.PluginManager
import app.aircons.data.ControlMode
import app.aircons.data.LinkState
import app.aircons.data.PodBattery
import app.aircons.data.SessionBus
import app.aircons.data.SettingsStore
import app.aircons.data.StemEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AirConsService : Service() {
    companion object {
        const val ACTION_START = "app.aircons.action.START"
        const val ACTION_STOP = "app.aircons.action.STOP"
        const val ACTION_RECENTER = "app.aircons.action.RECENTER"
        const val ACTION_CYCLE = "app.aircons.action.CYCLE"
        const val EXTRA_MODE = "mode"

        private const val CHANNEL_ID = "aircons.session"
        private const val NOTIFICATION_ID = 4097

        fun start(context: Context, mode: ControlMode) {
            val intent = Intent(context, AirConsService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.key)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AirConsService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun recenter(context: Context) {
            val intent = Intent(context, AirConsService::class.java).apply {
                action = ACTION_RECENTER
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: SettingsStore
    private lateinit var engine: ControllerEngine
    private lateinit var client: AapClient

    private var injectionJob: Job? = null
    private var settingsJob: Job? = null
    private var foreground = false
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        settings = AirConsApp.instance.settings
        engine = ControllerEngine(settings) { mode ->
            SessionBus.setMode(mode)
            updateNotification()
        }
        client = AapClient(this, clientListener)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RECENTER -> {
                engine.recenter()
                return START_NOT_STICKY
            }

            ACTION_CYCLE -> {
                val next = ControlMode.entries[(engine.mode.ordinal + 1) % ControlMode.entries.size]
                settings.update { it.copy(modeKey = next.key) }
                engine.setMode(next)
                SessionBus.setMode(next)
                updateNotification()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val mode = ControlMode.fromKey(intent.getStringExtra(EXTRA_MODE))
                if (SessionBus.sessionActive.value) {
                    engine.setMode(mode)
                    updateNotification()
                    return START_NOT_STICKY
                }
                startSession(mode)
                return START_NOT_STICKY
            }

            else -> {
                if (!SessionBus.sessionActive.value) stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startSession(mode: ControlMode) {
        startForegroundNow()
        SessionBus.clearError()
        SessionBus.setSessionActive(true)
        SessionBus.setMode(mode)
        engine.setMode(mode)
        settings.update { it.copy(modeKey = mode.key) }

        PluginManager.activate(
            if (settings.state.value.scriptsEnabled) settings.state.value.activePluginId else "builtin"
        )

        val device = resolveDevice()
        if (device == null) {
            fail(
                "No AirPods found. Pair them in Android Bluetooth settings, then pick them on the home screen."
            )
            return
        }

        SessionBus.setDevice(device.safeName())
        if (settings.state.value.targetAddress.isNullOrBlank()) {
            settings.setTarget(device.address, device.safeName())
        }

        startInjectionLoop()
        watchSettings()

        SessionBus.setLink(LinkState.CONNECTING, "Opening L2CAP 4097")
        updateNotification()
        client.connect(device, settings.state.value.alternatePackets)
    }

    private fun startInjectionLoop() {
        injectionJob?.cancel()
        injectionJob = scope.launch {
            while (isActive) {
                val state = settings.state.value
                if (SessionBus.sessionActive.value) {
                    AirConsAccessibilityService.instance?.inject(
                        SessionBus.frame.value,
                        engine.mode,
                        state
                    )
                }
                delay((1000L / state.injectionHz.coerceIn(8, 30)).toLong())
            }
        }
    }

    private fun watchSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settings.state.collect { state ->
                if (state.scriptsEnabled) {
                    PluginManager.activate(state.activePluginId)
                } else {
                    PluginManager.activate("builtin")
                }
                if (SessionBus.sessionActive.value &&
                    engine.mode.key != state.modeKey
                ) {
                    engine.setMode(ControlMode.fromKey(state.modeKey))
                }
            }
        }
    }

    private fun stopSession() {
        runCatching { client.stopTracking() }
        client.close()
        injectionJob?.cancel()
        injectionJob = null
        settingsJob?.cancel()
        settingsJob = null
        SessionBus.setSessionActive(false)
        SessionBus.setLink(LinkState.IDLE)
        SessionBus.resetSession()
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }

    private fun fail(message: String) {
        SessionBus.setError(message)
        SessionBus.setSessionActive(false)
        client.close()
        injectionJob?.cancel()
        injectionJob = null
        settingsJob?.cancel()
        settingsJob = null
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { client.destroy() }
        scope.cancel()
        if (SessionBus.sessionActive.value) {
            SessionBus.setSessionActive(false)
            SessionBus.setLink(LinkState.IDLE)
            SessionBus.resetSession()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNow() {
        if (foreground) return
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        foreground = true
    }

    private fun updateNotification() {
        if (!foreground) return
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < 900L) return
        lastNotificationAt = now
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val recenter = PendingIntent.getService(
            this,
            2,
            Intent(this, AirConsService::class.java).setAction(ACTION_RECENTER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            3,
            Intent(this, AirConsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val mode = SessionBus.mode.value
        val battery = SessionBus.battery.value
        val level = battery.minimum?.let { "$it%" } ?: "battery pending"
        val link = when (SessionBus.link.value) {
            LinkState.STREAMING -> "live · ${SessionBus.pps.value} Hz"
            LinkState.CONNECTED -> "linked"
            LinkState.CONNECTING -> "linking"
            LinkState.ERROR -> "error"
            LinkState.IDLE -> "idle"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aircons)
            .setContentTitle("AirCons · ${mode.title}")
            .setContentText("${SessionBus.deviceName.value ?: "AirPods"} · $level · $link")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_aircons, "Recenter", recenter)
            .addAction(R.drawable.ic_stat_aircons, "Stop", stop)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun resolveDevice(): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = getSystemService(BluetoothManager::class.java) ?: return null
        val adapter = manager.adapter ?: return null
        val savedAddress = settings.state.value.targetAddress
        if (!savedAddress.isNullOrBlank()) {
            runCatching { adapter.getRemoteDevice(savedAddress) }.getOrNull()?.let { return it }
        }
        return runCatching {
            adapter.bondedDevices?.firstOrNull {
                it.safeName()?.contains("AirPods", ignoreCase = true) == true
            }
        }.getOrNull()
    }

    private fun BluetoothDevice.safeName(): String? = runCatching { name }.getOrNull()

    private val clientListener = object : AapClient.Listener {
        override fun onLink(message: String?) {
            val state = if (message == "AAP link open") LinkState.CONNECTED else LinkState.CONNECTING
            SessionBus.setLink(state, message)
            if (state == LinkState.CONNECTED) engine.recenter()
            updateNotification()
        }

        override fun onBattery(battery: PodBattery) {
            SessionBus.setBattery(battery)
            updateNotification()
        }

        override fun onEar(ear: app.aircons.data.EarState) {
            SessionBus.setEar(ear)
        }

        override fun onRawMotion(raw: AapClient.RawMotion) {
            if (SessionBus.link.value != LinkState.STREAMING) {
                SessionBus.setLink(LinkState.STREAMING)
            }
            engine.onRawMotion(raw)
        }

        override fun onStem(event: StemEvent) {
            engine.onStem(event)
            updateNotification()
        }

        override fun onPacketsPerSecond(value: Int) {
            SessionBus.setPps(value)
            updateNotification()
        }

        override fun onFailure(message: String) {
            fail(message)
        }
    }
}
