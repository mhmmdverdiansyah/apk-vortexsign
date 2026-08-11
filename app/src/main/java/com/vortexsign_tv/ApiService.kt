package com.vortexsign_tv

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// ✨ TAMBAHKAN INI: Cetakan data untuk mengirim laporan versi konten ke Laravel
data class StatusUpdateRequest(
    val versi_konten: String
)

data class StatusUpdateResponse(
    val success: Boolean,
    val message: String
)

interface ApiService {

    // 1️⃣ Fungsi Baru: Untuk Fase Registrasi (Handshake Awal)
    @POST("device/register")
    fun registerDevice(
        @Body request: RegisterRequest
    ): Call<RegisterResponse>

    // 2️⃣ Fungsi Lama: Untuk Mengambil Playlist Iklan
    @GET("device/{device_id}/jadwal")
    fun ambilJadwal(
        @Path("device_id") deviceId: String,
        @Header("X-Device-Token") token: String
    ): Call<JadwalResponse>

    // 3️⃣ ✨ FUNGSI BARU: Untuk Laporan Sinkronisasi Status Konten ke Dashboard
    @POST("device/{device_id}/status")
    fun updateStatusKonten(
        @Path("device_id") deviceId: String,
        @Header("X-Device-Token") token: String,
        @Body request: StatusUpdateRequest
    ): Call<StatusUpdateResponse>
}