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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
}

@Composable
    val context     = LocalContext.current
    val sharedPref  = remember { context.getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE) }

    var tokenState          by remember { mutableStateOf(sharedPref.getString("device_token", null)) }
    var isLoading           by remember { mutableStateOf(true) }
    var daftarVideoPlay     by remember { mutableStateOf<List<VideoData>>(emptyList()) }
    var indexVideoSekarang  by remember { mutableStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }

    LaunchedEffect(tokenState) {
        if (tokenState == null) {
            statusText = "Menghubungkan Perangkat...\nID TV: $deviceId"
            val regRequest = RegisterRequest(deviceId, deviceModel, androidVersion, appVersion)
            RetrofitClient.instance.registerDevice(regRequest)
                .enqueue(object : Callback<RegisterResponse> {
                    override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                            } else {
                                statusText = response.body()?.message ?: "Gagal mendapatkan token.\nID TV: $deviceId"
                            }
                        } else {
                        }
                    }
                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                    }
                })
        } else {
            RetrofitClient.instance.ambilJadwal(deviceId, tokenState!!)
                .enqueue(object : Callback<JadwalResponse> {
                    override fun onResponse(call: Call<JadwalResponse>, response: Response<JadwalResponse>) {
                        when (response.code()) {
                            200 -> {
                                val body = response.body()
                                val semuaVideo = body?.daftarJadwal?.flatMap { it.daftarVideo } ?: emptyList()

                                if (semuaVideo.isNotEmpty()) {
                                        "Landscape"         -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        "Landscape Kiri"    -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                        "Portrait"          -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        "Portrait Terbalik" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                        else                -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }

                                        override fun onResponse(call: Call<StatusUpdateResponse>, res: Response<StatusUpdateResponse>) {
                                        }
                                        override fun onFailure(call: Call<StatusUpdateResponse>, t: Throwable) {
                                        }
                                    })

                                } else {
                                }
                            }
                            401, 403 -> {
                            }
                            else -> {
                            }
                        }
                    }
                    override fun onFailure(call: Call<JadwalResponse>, t: Throwable) {
                    }
                })
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
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
        player.prepare()
        player.play()
    }
}