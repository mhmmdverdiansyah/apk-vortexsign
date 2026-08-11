package com.vortexsign_tv

import com.google.gson.annotations.SerializedName

// 1️⃣ Model Utama dari Response API
data class JadwalResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("versi") val versi: String?,
    @SerializedName("orientasi") val orientasi: String?,
    @SerializedName("jadwal") val daftarJadwal: List<JadwalData>
)

// 2️⃣ Model untuk Data Distribusi Jadwal
data class JadwalData(
    @SerializedName("distribusi_id") val distribusiId: Int,
    @SerializedName("nama_jadwal") val namaJadwal: String,
    @SerializedName("waktu_mulai") val waktuMulai: String,
    @SerializedName("waktu_selesai") val waktuSelesai: String,
    @SerializedName("videos") val daftarVideo: List<VideoData>
)

// 3️⃣ Model untuk Detail File Video Iklan
data class VideoData(
    @SerializedName("id") val id: Int,
    @SerializedName("nama_iklan") val namaIklan: String,
    @SerializedName("durasi_detik") val durasiDetik: Int?,
    @SerializedName("video_url") val videoUrl: String?,

    // ✨ FIX: Diubah menjadi Boolean agar cocok dengan respons server Laravel
    @SerializedName("has_video") val hasVideo: Boolean
)

data class RegisterRequest(
    val device_id: String,
    val device_model: String,
    val android_version: String,
    val app_version: String
)

data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val device_token: String?
)