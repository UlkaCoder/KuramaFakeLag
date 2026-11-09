package com.fakelag.app

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import java.util.concurrent.atomic.AtomicBoolean

class LagVpnService : VpnService() {

    companion object {
        const val ACTION_START  = "com.fakelag.START"
        const val ACTION_STOP   = "com.fakelag.STOP"
        const val ACTION_UPDATE = "com.fakelag.UPDATE"
        const val EXTRA_LATENCY = "latency_ms"
        const val EXTRA_LOSS    = "loss_pct"
        const val CHANNEL_ID    = "fakelag_vpn"

        @Volatile var currentLatency: Int  = 200
        @Volatile var currentLoss: Int     = 0
        @Volatile var isRunning: Boolean   = false
    }

    private var tun: ParcelFileDescriptor? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val proxy = ProxyEngine()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentLatency = intent.getIntExtra(EXTRA_LATENCY, currentLatency)
                currentLoss    = intent.getIntExtra(EXTRA_LOSS,    currentLoss)
                startVpn()
            }
            ACTION_UPDATE -> {
                currentLatency = intent.getIntExtra(EXTRA_LATENCY, currentLatency)
                currentLoss    = intent.getIntExtra(EXTRA_LOSS,    currentLoss)
                proxy.update(currentLatency, currentLoss)
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (running.get()) return
        createChannel()

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FakeLag activo")
            .setContentText("Latencia: ${currentLatency}ms · Pérdida: ${currentLoss}%")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notif)

        val builder = Builder()
            .setSession("FakeLag")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setBlocking(true)
            .setMtu(1500)

        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        tun = builder.establish() ?: run { stopSelf(); return }
        running.set(true)
        isRunning = true
        proxy.update(currentLatency, currentLoss)
        proxy.start(tun!!)

        thread = Thread {
            while (running.get()) Thread.sleep(1000)
        }.also { it.start() }
    }

    private fun stopVpn() {
        running.set(false)
        isRunning = false
        proxy.stop()
        thread?.interrupt()
        thread = null
        tun?.close()
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FakeLag", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
