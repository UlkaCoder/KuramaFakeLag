package com.fakelag.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var latencyLabel: TextView
    private lateinit var lossLabel: TextView
    private lateinit var latencyBar: SeekBar
    private lateinit var lossBar: SeekBar
    private lateinit var toggleBtn: Button

    private val vpnReq = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        latencyLabel = findViewById(R.id.latencyLabel)
        lossLabel    = findViewById(R.id.lossLabel)
        latencyBar   = findViewById(R.id.latencyBar)
        lossBar      = findViewById(R.id.lossBar)
        toggleBtn    = findViewById(R.id.toggleBtn)

        latencyBar.max      = 2000
        latencyBar.progress = 200
        lossBar.max         = 100
        lossBar.progress    = 0

        latencyBar.setOnSeekBarChangeListener(simpleListener { p ->
            latencyLabel.text = "Latencia: ${p}ms"
            sendUpdate(p, lossBar.progress)
        })
        lossBar.setOnSeekBarChangeListener(simpleListener { p ->
            lossLabel.text = "Pérdida: ${p}%"
            sendUpdate(latencyBar.progress, p)
        })

        latencyLabel.text = "Latencia: 200ms"
        lossLabel.text    = "Pérdida: 0%"

        toggleBtn.setOnClickListener {
            if (LagVpnService.isRunning) stopVpn() else requestVpn()
        }

        // Permiso de overlay para el botón flotante
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        startService(Intent(this, FloatingButtonService::class.java))
    }

    private fun requestVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) startActivityForResult(prep, vpnReq)
        else startVpn()
    }

    private fun startVpn() {
        val i = Intent(this, LagVpnService::class.java).apply {
            action = LagVpnService.ACTION_START
            putExtra(LagVpnService.EXTRA_LATENCY, latencyBar.progress)
            putExtra(LagVpnService.EXTRA_LOSS,    lossBar.progress)
        }
        startForegroundService(i)
        toggleBtn.text = "Detener"
    }

    private fun stopVpn() {
        val i = Intent(this, LagVpnService::class.java).apply {
            action = LagVpnService.ACTION_STOP
        }
        startService(i)
        toggleBtn.text = "Activar"
    }

    private fun sendUpdate(lat: Int, loss: Int) {
        val i = Intent(this, LagVpnService::class.java).apply {
            action = LagVpnService.ACTION_UPDATE
            putExtra(LagVpnService.EXTRA_LATENCY, lat)
            putExtra(LagVpnService.EXTRA_LOSS,    loss)
        }
        startService(i)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == vpnReq && resultCode == Activity.RESULT_OK) startVpn()
    }

    private fun simpleListener(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
            if (fromUser) block(p)
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
