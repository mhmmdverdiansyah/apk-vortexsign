package com.vortexsign_tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

class BootService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VortexsignBoot", "BootService dimulai")

        // Buat notifikasi agar bisa jalan sebagai foreground service
        startForeground(1, buildNotification())

        // Wake lock: paksa CPU tetap aktif saat proses boot
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VortexsignTV::BootWakeLock"
        ).apply { acquire(30_000L) } // max 30 detik

        // Tunggu 6 detik lalu buka SplashActivity
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launchIntent = Intent(applicationContext, SplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                applicationContext.startActivity(launchIntent)
                Log.d("VortexsignBoot", "Vortex Player berhasil diluncurkan dari BootService!")
            } catch (e: Exception) {
                Log.e("VortexsignBoot", "Gagal membuka app dari BootService: ${e.message}")
            } finally {
                wakeLock?.release()
                stopSelf()
            }
        }, 6_000)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun buildNotification(): Notification {
        val channelId = "vortexsign_boot"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vortex Player Boot",
                NotificationManager.IMPORTANCE_MIN // tidak bunyi, tidak muncul di layar
            ).apply {
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("Vortex Player")
            .setContentText("Memulai sistem...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}
