package com.oppominer.service

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.oppominer.R
import com.oppominer.stratum.StratumClient
import kotlinx.coroutines.*

class MiningForegroundService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var stratumClient: StratumClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var activeThreads = 6
    private var maxTempCelsius = 43.0f
    private var minBatteryPercent = 20
    private var onlyWhileCharging = false
    private var isThrottledOrPaused = false

    companion object {
        const val CHANNEL_ID = "oppo_mining_service_channel"
        const val NOTIFICATION_ID = 1337
        const val ACTION_START = "ACTION_START_MINING"
        const val ACTION_STOP = "ACTION_STOP_MINING"

        init {
            System.loadLibrary("native-miner")
        }
    }

    // JNI Native methods
    external fun startNativeMining(midstate: IntArray, tail: IntArray, startNonce: Int, threadCount: Int, targetUpper: Long)
    external fun stopNativeMining()
    external fun getNativeHashCount(): Long

    inner class LocalBinder : Binder() {
        fun getService(): MiningForegroundService = this@MiningForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBatteryAndThermalReceiver()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeThreads = intent.getIntExtra("threads", 6)
                maxTempCelsius = intent.getFloatExtra("maxTemp", 43.0f)
                val poolHost = intent.getStringExtra("poolHost") ?: "solo.ckpool.org"
                val poolPort = intent.getIntExtra("poolPort", 3333)
                val btcAddress = intent.getStringExtra("btcAddress") ?: ""
                val workerName = intent.getStringExtra("workerName") ?: "oppo_a5"

                startForeground(NOTIFICATION_ID, buildNotification("Mining active: $activeThreads threads"))
                connectStratumAndMine(poolHost, poolPort, btcAddress, workerName)
            }
            ACTION_STOP -> {
                stopMining()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OPPOMiner::CpuMiningWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
    }

    private fun registerBatteryAndThermalReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // Thermal Protection Check
            if (temp >= maxTempCelsius) {
                if (!isThrottledOrPaused) {
                    isThrottledOrPaused = true
                    stopNativeMining()
                    updateNotification("Mining PAUSED: High Temperature (${temp}°C >= ${maxTempCelsius}°C)")
                }
            } else if (isThrottledOrPaused && temp <= (maxTempCelsius - 3.0f)) {
                // Resume when cooled down 3 degrees
                isThrottledOrPaused = false
                resumeNativeMining()
                updateNotification("Mining Resumed: ${temp}°C")
            }

            // Battery Limit Check
            if (onlyWhileCharging && !isCharging) {
                stopMining()
                updateNotification("Mining Paused: Disconnected from Charger")
            } else if (batteryPct < minBatteryPercent && !isCharging) {
                stopMining()
                updateNotification("Mining Paused: Battery low (${batteryPct}%)")
            }
        }
    }

    private fun connectStratumAndMine(host: String, port: Int, address: String, worker: String) {
        stratumClient = StratumClient(host, port, address, worker) { job ->
            if (!isThrottledOrPaused) {
                startNativeMining(job.midstate, job.tail, 0, activeThreads, job.targetUpper)
            }
        }
        serviceScope.launch {
            stratumClient?.connect()
        }
    }

    private fun resumeNativeMining() {
        // Restart native worker threads
    }

    private fun stopMining() {
        stopNativeMining()
        stratumClient?.disconnect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OPPO A5 Mining Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background Bitcoin mining service with screen-off wakelock"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OPPO A5 Stratum Miner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(batteryReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopMining()
        super.onDestroy()
    }
}
