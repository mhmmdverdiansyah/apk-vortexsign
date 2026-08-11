package com.vortexsign_tv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val deviceId    = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val appVersion  = "1.0.0"

        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SignageCoreEngine(deviceId, deviceModel, androidVersion, appVersion)
            }
        }
    }

    // ===== DETEKSI TOMBOL REMOTE (TOMBOL ANGKA 2) =====
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_2) {
            // Tampilkan/sembunyikan Debug Overlay
            DebugOverlay.toggle(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

// ── State download satu video ───────────────────────────────────────────────
data class ActiveDownload(
    val fileName: String,
    val percent: Int,
    val downloadedMB: Float,
    val totalMB: Float
)

// ─── Composable utama ─────────────────────────────────────────────────────────
@Composable
fun SignageCoreEngine(
    deviceId: String,
    deviceModel: String,
    androidVersion: String,
    appVersion: String
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val sharedPref  = remember { context.getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE) }

    var tokenState          by remember { mutableStateOf(sharedPref.getString("device_token", null)) }
    var statusText          by remember { mutableStateOf("Menginisialisasi...\nID TV: $deviceId") }
    var isLoading           by remember { mutableStateOf(true) }
    var daftarVideoPlay     by remember { mutableStateOf<List<VideoData>>(emptyList()) }
    var indexVideoSekarang  by remember { mutableStateOf(0) }

    // Download progress — null = tidak ada download aktif
    var activeDownload      by remember { mutableStateOf<ActiveDownload?>(null) }

    // Versi jadwal terakhir yang diketahui — untuk deteksi perubahan
    var versiTerakhir       by remember { mutableStateOf("") }
    var isPollingActive     by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }

    // ── Fungsi: download semua video lalu putar ──────────────────────────────
    fun downloadAndPlay(videos: List<VideoData>) {
        scope.launch {
            val localPaths = mutableListOf<String>()

            for (video in videos) {
                val url = video.videoUrl ?: continue

                // Sudah di-cache? langsung pakai
                val cached = VideoCache.localPathOrNull(context, url)
                if (cached != null) {
                    localPaths.add(cached)
                    Log.d("VortexsignTV", "Cache hit: $url")
                    continue
                }

                // Belum ada — download dengan progress
                val result = VideoCache.downloadVideo(context, url) { progress ->
                    activeDownload = if (progress.isFinished || progress.isFailed) {
                        null
                    } else {
                        ActiveDownload(
                            fileName      = progress.fileName,
                            percent       = progress.progressPercent,
                            downloadedMB  = progress.downloadedMB,
                            totalMB       = progress.totalMB
                        )
                    }
                }
                if (result != null) localPaths.add(result)
            }

            activeDownload = null

            if (localPaths.isNotEmpty()) {
                // Simpan playlist URL asli untuk offline fallback berikutnya
                VideoCache.saveLastPlaylist(context, videos.mapNotNull { it.videoUrl })

                // Buat daftarVideoPlay pakai path lokal
                daftarVideoPlay = localPaths.mapIndexed { i, path -> videos.getOrNull(i)?.copy(videoUrl = path) ?: VideoData(id = i, namaIklan = "video_$i", durasiDetik = null, videoUrl = path, hasVideo = true) }
                isLoading = false
                putarVideoKe(exoPlayer, localPaths[0])
            } else {
                statusText = "Gagal mengunduh semua konten.\nID TV: $deviceId"
            }
        }
    }

    // ── Fungsi: coba fallback ke cache offline ───────────────────────────────
    fun tryOfflineFallback() {
        val lastUrls = VideoCache.loadLastPlaylist(context)
        val cachedPaths = lastUrls.mapNotNull { VideoCache.localPathOrNull(context, it) }

        if (cachedPaths.isNotEmpty()) {
            Log.d("VortexsignTV", "Offline mode: memutar ${cachedPaths.size} video dari cache")
            daftarVideoPlay = cachedPaths.mapIndexed { i, path -> VideoData(id = i, namaIklan = "cache_$i", durasiDetik = null, videoUrl = path, hasVideo = true) }
            isLoading = false
            putarVideoKe(exoPlayer, cachedPaths[0])
        } else {
            statusText = "️ Tidak ada koneksi internet\ndan belum ada konten yang tersimpan.\n\nID TV: $deviceId"
        }
    }

    // ── Fungsi: cek jadwal terbaru (polling) ────────────────────────────────
    fun cekJadwalTerbaru() {
        val token = tokenState ?: return
        RetrofitClient.instance.ambilJadwal(deviceId, token)
            .enqueue(object : Callback<JadwalResponse> {
                override fun onResponse(call: Call<JadwalResponse>, response: Response<JadwalResponse>) {
                    if (response.code() == 200) {
                        val body = response.body() ?: return
                        val versiServer = body.versi ?: return
                        val semuaVideo = body.daftarJadwal?.flatMap { it.daftarVideo } ?: emptyList()

                        if (versiServer != versiTerakhir && semuaVideo.isNotEmpty()) {
                            Log.d("VortexsignTV", "Jadwal baru terdeteksi! Versi: $versiServer")
                            versiTerakhir = versiServer

                            // Set orientasi layar sesuai jadwal baru
                            (context as? Activity)?.requestedOrientation = when (body.orientasi) {
                                "Landscape"         -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                "Landscape Kiri"    -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                "Portrait"          -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                "Portrait Terbalik" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                else                -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }

                            // Kirim konfirmasi versi ke dashboard
                            RetrofitClient.instance.updateStatusKonten(
                                deviceId, token, StatusUpdateRequest(versiServer)
                            ).enqueue(object : Callback<StatusUpdateResponse> {
                                override fun onResponse(call: Call<StatusUpdateResponse>, res: Response<StatusUpdateResponse>) {
                                    Log.d("VortexsignTV", "Status versi terkirim: $versiServer")
                                }
                                override fun onFailure(call: Call<StatusUpdateResponse>, t: Throwable) {
                                    Log.e("VortexsignTV", "Gagal kirim status: ${t.message}")
                                }
                            })

                            // Download video baru & reload playlist
                            downloadAndPlay(semuaVideo)
                        } else {
                            Log.d("VortexsignTV", "Jadwal tidak berubah (versi: $versiServer)")
                        }
                    }
                }
                override fun onFailure(call: Call<JadwalResponse>, t: Throwable) {
                    Log.w("VortexsignTV", "Polling gagal (offline?): ${t.message}")
                    // Tidak perlu fallback — video cache tetap diputar
                }
            })
    }

    // ── Polling setiap 30 detik setelah token tersedia ───────────────────────
    LaunchedEffect(tokenState, isPollingActive) {
        if (tokenState != null && isPollingActive) {
            while (true) {
                delay(30_000L) // tunggu 30 detik
                cekJadwalTerbaru()
            }
        }
    }

    // ── LaunchedEffect: registrasi & ambil jadwal ────────────────────────────
    LaunchedEffect(tokenState) {
        if (tokenState == null) {
            statusText = "Menghubungkan Perangkat...\nID TV: $deviceId"
            val regRequest = RegisterRequest(deviceId, deviceModel, androidVersion, appVersion)
            RetrofitClient.instance.registerDevice(regRequest)
                .enqueue(object : Callback<RegisterResponse> {
                    override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                        if (response.isSuccessful) {
                            val token = response.body()?.device_token
                            if (token != null) {
                                sharedPref.edit().putString("device_token", token).apply()
                                tokenState = token
                            } else {
                                statusText = response.body()?.message ?: "Gagal mendapatkan token.\nID TV: $deviceId"
                                tryOfflineFallback()
                            }
                        } else {
                            statusText = "Gagal Registrasi (${response.code()}).\nID TV: $deviceId"
                            tryOfflineFallback()
                        }
                    }
                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        Log.e("VortexsignTV", "Registrasi gagal: ${t.message}")
                        tryOfflineFallback()
                    }
                })
        } else {
            statusText = "Memverifikasi & Mengunduh Jadwal...\nID TV: $deviceId"
            RetrofitClient.instance.ambilJadwal(deviceId, tokenState!!)
                .enqueue(object : Callback<JadwalResponse> {
                    override fun onResponse(call: Call<JadwalResponse>, response: Response<JadwalResponse>) {
                        when (response.code()) {
                            200 -> {
                                val body = response.body()
                                val semuaVideo = body?.daftarJadwal?.flatMap { it.daftarVideo } ?: emptyList()

                                if (semuaVideo.isNotEmpty()) {
                                    // Set orientasi layar
                                    (context as? Activity)?.requestedOrientation = when (body?.orientasi) {
                                        "Landscape"         -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        "Landscape Kiri"    -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                        "Portrait"          -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        "Portrait Terbalik" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                        else                -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }

                                    // Kirim status konten ke dashboard
                                    val versi = body?.versi ?: "default_version"
                                    RetrofitClient.instance.updateStatusKonten(
                                        deviceId, tokenState!!, StatusUpdateRequest(versi)
                                    ).enqueue(object : Callback<StatusUpdateResponse> {
                                        override fun onResponse(call: Call<StatusUpdateResponse>, res: Response<StatusUpdateResponse>) {
                                            Log.d("VortexsignTV", "Status konten terkirim")
                                        }
                                        override fun onFailure(call: Call<StatusUpdateResponse>, t: Throwable) {
                                            Log.e("VortexsignTV", "Gagal kirim status: ${t.message}")
                                        }
                                    })

                                    // Download semua video ke cache lalu putar
                                    versiTerakhir = body?.versi ?: ""
                                    downloadAndPlay(semuaVideo)
                                    isPollingActive = true // mulai polling setelah jadwal pertama berhasil

                                } else {
                                    statusText = "✅ Perangkat Aktif\n\nID TV: $deviceId\n\n(Belum ada jadwal iklan untuk hari ini)"
                                }
                            }
                            401, 403 -> {
                                statusText = "⛔ MENUNGGU AKTIVASI ADMIN ⛔\n\nID Perangkat TV ini:\n$deviceId\n\nBuka Dashboard Admin → aktifkan perangkat ini."
                            }
                            else -> {
                                statusText = "Server error (${response.code()}).\nID TV: $deviceId"
                                tryOfflineFallback()
                            }
                        }
                    }
                    override fun onFailure(call: Call<JadwalResponse>, t: Throwable) {
                        Log.e("VortexsignTV", "Jadwal gagal: ${t.message}")
                        // Tidak ada internet — pakai cache terakhir
                        tryOfflineFallback()
                    }
                })
        }
    }

    // ── Listener ExoPlayer: putar video berikutnya ───────────────────────────
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && daftarVideoPlay.isNotEmpty()) {
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

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        if (isLoading) {
            // Layar status teks
            Text(
                text = statusText,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        } else {
            // Player video fullscreen
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

        // ── Overlay download di pojok kanan bawah ───────────────────────────
        activeDownload?.let { dl ->
            DownloadOverlay(
                download = dl,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}

// ─── Komponen overlay download ────────────────────────────────────────────────
@Composable
fun DownloadOverlay(download: ActiveDownload, modifier: Modifier = Modifier) {

    // Animasi pulsa pada dot indikator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCC0D0D1A)) // hitam transparan
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(min = 220.dp, max = 320.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Baris atas: dot + label + ukuran
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dot pulsa
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFD4B8F4).copy(alpha = dotAlpha))
                )
                Text(
                    text = "Mengunduh konten",
                    color = Color(0xFFD4B8F4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                // Ukuran file
                Text(
                    text = if (download.totalMB > 0)
                        "${"%.1f".format(download.downloadedMB)} / ${"%.1f".format(download.totalMB)} MB"
                    else
                        "${"%.1f".format(download.downloadedMB)} MB",
                    color = Color(0xFF9B7EC8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Nama file (truncate jika panjang)
            Text(
                text = download.fileName,
                color = Color(0xFF666680),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2A1A4A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (download.percent / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD4B8F4))
                )
            }

            // Persentase
            Text(
                text = "${download.percent}%",
                color = Color(0xFF9B7EC8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// ─── Helper ExoPlayer ─────────────────────────────────────────────────────────
fun putarVideoKe(player: ExoPlayer, url: String?) {
    if (!url.isNullOrEmpty()) {
        val uri = if (url.startsWith("/")) {
            // Path lokal dari cache
            android.net.Uri.parse("file://$url")
        } else {
            android.net.Uri.parse(url.replace(" ", "%20"))
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        Log.d("VortexsignTV", "ExoPlayer memutar: $uri")
    }
}