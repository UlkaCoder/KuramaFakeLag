package com.fakelag.app

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

class FloatingButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var button: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = Notification.Builder(this, "floating")
            .setContentTitle("FakeLag botón flotante")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(2, notif)

        if (button == null) addButton()
        return START_STICKY
    }

    private fun addButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        button = Button(this).apply {
            text = "LAG"
            setOnClickListener { toggle() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 300
        }
        windowManager?.addView(button, params)
    }

    private fun toggle() {
        val i = Intent(this, LagVpnService::class.java)
        i.action = if (LagVpnService.isRunning) LagVpnService.ACTION_STOP
                   else                         LagVpnService.ACTION_START
        startService(i)
    }

    override fun onDestroy() {
        button?.let { windowManager?.removeView(it) }
        button = null
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel("floating") == null) {
            mgr.createNotificationChannel(
                NotificationChannel("floating", "Botón flotante", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
