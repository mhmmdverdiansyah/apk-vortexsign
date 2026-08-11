package com.vortexsign_tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import android.provider.Settings
import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val appVersion = "1.0.0"
        val sharedPref = getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE)

        setContent {
            SplashScreen(
                onReady = { navigateToMain() }
            )
        }

        // Jalankan proses koneksi/registrasi di background
        val existingToken = sharedPref.getString("device_token", null)

        if (existingToken == null) {
            // Belum punya token — lakukan registrasi dulu
            val regRequest = RegisterRequest(deviceId, deviceModel, androidVersion, appVersion)
            RetrofitClient.instance.registerDevice(regRequest)
                .enqueue(object : Callback<RegisterResponse> {
                    override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                        if (response.isSuccessful) {
                            val token = response.body()?.device_token
                            if (token != null) {
                                sharedPref.edit().putString("device_token", token).apply()
                                Log.d("VortexsignSplash", "Registrasi berhasil, token disimpan.")
                            }
                        } else {
                            Log.e("VortexsignSplash", "Registrasi gagal: ${response.code()}")
                        }
                        navigateToMain()
                    }
                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        Log.e("VortexsignSplash", "Gagal koneksi saat registrasi: ${t.localizedMessage}")
                        // Tetap lanjut ke MainActivity meski gagal, agar tidak stuck
                        navigateToMain()
                    }
                })
        } else {
            // Sudah punya token — lakukan pengecekan koneksi API ringan
            RetrofitClient.instance.ambilJadwal(deviceId, existingToken)
                .enqueue(object : Callback<JadwalResponse> {
                    override fun onResponse(call: Call<JadwalResponse>, response: Response<JadwalResponse>) {
                        Log.d("VortexsignSplash", "Ping API selesai: ${response.code()}")
                        navigateToMain()
                    }
                    override fun onFailure(call: Call<JadwalResponse>, t: Throwable) {
                        Log.e("VortexsignSplash", "Ping API gagal: ${t.localizedMessage}")
                        navigateToMain()
                    }
                })
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun SplashScreen(onReady: () -> Unit) {
    // Animasi rotasi spinner
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Animasi fade-in logo
    var alphaTarget by remember { mutableStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(durationMillis = 800),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        alphaTarget = 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Logo teks — VORTEX SIGN
            Text(
                text = "VORTEX",
                color = Color(0xFFD4B8F4),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SIGN",
                color = Color(0xFF9B7EC8),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Garis pembatas tipis
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(1.dp)
                    .background(Color(0xFF4A2A7A).copy(alpha = 0.6f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Digital Signage System",
                color = Color(0xFF555568),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Spinner animasi
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .rotate(rotation)
            ) {
                drawArc(
                    color = Color(0xFF6C63FF),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF6C63FF).copy(alpha = 0.2f),
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Menghubungkan ke server...",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
