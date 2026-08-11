package com.vortexsign_tv

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vortexsign_tv.R
import java.text.SimpleDateFormat
import java.util.Locale

object DebugOverlay {

    private var overlayView: View? = null
    private var isShowing = false
    private val autoHideHandler = Handler(Looper.getMainLooper())

    // Fungsi untuk memunculkan/menyembunyikan overlay
    fun toggle(activity: Activity) {
        if (isShowing) hide() else show(activity)
    }

    private fun show(activity: Activity) {
        if (isShowing) return

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        overlayView = LayoutInflater.from(activity).inflate(R.layout.view_debug_overlay, contentRoot, false)

        updateData(activity)
        contentRoot.addView(overlayView)
        isShowing = true

        // Otomatis hilang setelah 15 detik
        autoHideHandler.postDelayed({ hide() }, 15000)
    }

    private fun hide() {
        overlayView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            overlayView = null
        }
        isShowing = false
        autoHideHandler.removeCallbacksAndMessages(null)
    }

    // Fungsi untuk update data secara real-time
    private fun updateData(context: Context) {
        val view = overlayView ?: return

        // 1. Cek Koneksi
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isOnline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNet = connMgr.activeNetwork
            val caps = connMgr.getNetworkCapabilities(activeNet)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            val activeNet = connMgr.activeNetworkInfo
            activeNet?.isConnected == true
        }

        val tvStatus = view.findViewById<TextView>(R.id.tv_status_net)
        tvStatus.text = if (isOnline) "ONLINE (LAN/WIFI)" else "OFFLINE"
        tvStatus.setTextColor(ContextCompat.getColor(context, if (isOnline) android.R.color.holo_green_light else android.R.color.holo_red_light))

        // 2. Update Data dari SharedPrefs (Jika ada)
        val prefs = context.getSharedPreferences("vortex_prefs", Context.MODE_PRIVATE)
        view.findViewById<TextView>(R.id.tv_playlist).text = prefs.getString("current_playlist", "Idle") ?: "Idle"
        view.findViewById<TextView>(R.id.tv_playing).text = prefs.getString("current_video", "Stopped") ?: "Stopped"
    }
}