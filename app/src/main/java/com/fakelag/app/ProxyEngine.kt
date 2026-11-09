package com.fakelag.app

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class ProxyEngine {

    private val running = AtomicBoolean(false)
    private var tunIn: FileInputStream? = null
    private var tunOut: FileOutputStream? = null
    private val pool = Executors.newCachedThreadPool()
    private val delayQueue = DelayQueue<DelayedPacket>()

    @Volatile private var latency = 200
    @Volatile private var lossPct = 0

    fun update(lat: Int, loss: Int) {
        latency = lat.coerceIn(0, 5000)
        lossPct = loss.coerceIn(0, 100)
    }

    fun start(tun: ParcelFileDescriptor) {
        if (!running.compareAndSet(false, true)) return
        tunIn  = FileInputStream(tun.fileDescriptor)
        tunOut = FileOutputStream(tun.fileDescriptor)

        // Hilo lector: lee paquetes del TUN y los encola con retardo
        pool.execute {
            val buf = ByteArray(32767)
            while (running.get()) {
                val n = try { tunIn!!.read(buf) } catch (_: Exception) { break }
                if (n <= 0) continue
                val pkt = buf.copyOf(n)
                if (Random.nextInt(100) < lossPct) continue   // pérdida simulada
                delayQueue.put(DelayedPacket(pkt, System.nanoTime() + latency * 1_000_000L))
            }
        }

        // Hilo escritor: saca paquetes vencidos y los reenvía al TUN
        pool.execute {
            while (running.get()) {
                val p = try {
                    delayQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (_: Exception) { null }
                if (p != null) {
                    try { tunOut!!.write(p.data) } catch (_: Exception) { break }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        delayQueue.clear()
        pool.shutdownNow()
        try { tunIn?.close()  } catch (_: Exception) {}
        try { tunOut?.close() } catch (_: Exception) {}
    }
}

class DelayedPacket(val data: ByteArray, private val releaseAt: Long) : Delayed {
    override fun getDelay(unit: TimeUnit): Long =
        unit.convert(releaseAt - System.nanoTime(), TimeUnit.NANOSECONDS)
    override fun compareTo(other: Delayed): Int =
        (getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS)).toInt()
}
