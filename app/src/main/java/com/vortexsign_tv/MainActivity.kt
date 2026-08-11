package com.vortexsign_tv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val appVersion = "1.0.0"

        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SignageCoreEngine(deviceId, deviceModel, androidVersion, appVersion)
            }
        }
    }
}

@Composable
fun SignageCoreEngine(deviceId: String, deviceModel: String, androidVersion: String, appVersion: String) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE) }

    var tokenState by remember { mutableStateOf(sharedPref.getString("device_token", null)) }
    var statusText by remember { mutableStateOf("Menginisialisasi Sistem TV...\nID TV: $deviceId") }
    var isLoading by remember { mutableStateOf(true) }
    var daftarVideoPlay by remember { mutableStateOf<List<VideoData>>(emptyList()) }
    var indexVideoSekarang by remember { mutableStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }

    LaunchedEffect(tokenState) {
        if (tokenState == null) {
            // ─── FASE REGISTRASI ───
            statusText = "Menghubungkan Perangkat...\nID TV: $deviceId"
            val regRequest = RegisterRequest(deviceId, deviceModel, androidVersion, appVersion)
            RetrofitClient.instance.registerDevice(regRequest)
                .enqueue(object : Callback<RegisterResponse> {
                    override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                        if (response.isSuccessful && response.body() != null) {
                            val tokenDiterima = response.body()?.device_token
                            if (tokenDiterima != null) {
                                sharedPref.edit().putString("device_token", tokenDiterima).apply()
                                tokenState = tokenDiterima
                            } else {
                                statusText = response.body()?.message ?: "Gagal mendapatkan token.\nID TV: $deviceId"
                            }
                        } else {
                            statusText = "Gagal Registrasi Perangkat (Eror ${response.code()}).\nID Perangkat: $deviceId"
                        }
                    }
                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        statusText = "GAGAL TERHUBUNG KE CMS!\nDetail: ${t.localizedMessage}\nID Perangkat: $deviceId"
                    }
                })
        } else {
            // ─── FASE AMBIL JADWAL JALAN ───
            statusText = "Memverifikasi Otoritas & Mengunduh Jadwal Iklan...\nID TV: $deviceId"
            RetrofitClient.instance.ambilJadwal(deviceId, tokenState!!)
                .enqueue(object : Callback<JadwalResponse> {
                    override fun onResponse(call: Call<JadwalResponse>, response: Response<JadwalResponse>) {
                        when (response.code()) {
                            200 -> {
                                val body = response.body()
                                val semuaVideo = body?.daftarJadwal?.flatMap { it.daftarVideo } ?: emptyList()

                                if (semuaVideo.isNotEmpty()) {
                                    // 1️⃣ FIX ISSUE ORIENTASI: Ubah rotasi layar fisik TV sesuai perintah Dashboard
                                    val activity = context as? Activity
                                    activity?.let {
                                        it.requestedOrientation = when (body?.orientasi) {
                                            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            "Landscape Kiri" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            "Portrait Terbalik" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    }

                                    // 2️⃣ FIX ISSUE STATUS KONTEN: Kirim konfirmasi ke server bahwa TV sudah update
                                    val versiKonten = body?.versi ?: "default_version"
                                    RetrofitClient.instance.updateStatusKonten(deviceId, tokenState!!, StatusUpdateRequest(versiKonten))
                                        .enqueue(object : Callback<StatusUpdateResponse> {
                                            override fun onResponse(call: Call<StatusUpdateResponse>, res: Response<StatusUpdateResponse>) {
                                                Log.d("VortexsignTV", "Berhasil memperbarui status konten ke Dashboard!")
                                            }
                                            override fun onFailure(call: Call<StatusUpdateResponse>, t: Throwable) {
                                                Log.e("VortexsignTV", "Gagal sync status ke server: ${t.localizedMessage}")
                                            }
                                        })

                                    daftarVideoPlay = semuaVideo
                                    isLoading = false
                                    putarVideoKe(exoPlayer, semuaVideo[indexVideoSekarang].videoUrl)
                                } else {
                                    statusText = "✅ Perangkat Aktif Terverifikasi\n\nID TV: $deviceId\n\n(Belum ada Jadwal Iklan yang didistribusikan untuk hari ini)"
                                }
                            }
                            401, 403 -> {
                                // ✨ TAMPILAN JIKA TV BELUM DIAKTIFKAN OLEH ADMIN ✨
                                statusText = "⛔ MENUNGGU AKTIVASI ADMIN ⛔\n\nID Perangkat TV ini:\n$deviceId\n\nSilakan buka Dashboard Admin, cari ID perangkat di atas,\ndan ubah statusnya menjadi 'Aktif'."
                            }
                            else -> {
                                statusText = "Server Merespons dengan Hambatan (Eror ${response.code()}).\nID Perangkat: $deviceId"
                            }
                        }
                    }
                    override fun onFailure(call: Call<JadwalResponse>, t: Throwable) {
                        statusText = "Gagal Sinkronisasi Jadwal Jaringan.\nDetail: ${t.localizedMessage}\nID Perangkat: $deviceId"
                    }
                })
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && daftarVideoPlay.isNotEmpty()) {
                    indexVideoSekarang = (indexVideoSekarang + 1) % daftarVideoPlay.size
                    putarVideoKe(exoPlayer, daftarVideoPlay[indexVideoSekarang].videoUrl)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    if (isLoading) {
        Text(
            text = statusText,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun putarVideoKe(player: ExoPlayer, url: String?) {
    if (!url.isNullOrEmpty()) {
        val urlAman = url.replace(" ", "%20")
        val mediaItem = MediaItem.fromUri(urlAman)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        Log.d("VortexsignTV", "ExoPlayer memutar: $urlAman")
    }
}