package com.example.helpme.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob

data class Incident(
    val id: String = "",
    val category: String = "Otro problema",
    val description: String = "",
    val address: String = "",
    val authorUid: String = "",
    val createdAt: Timestamp? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val photoPath: String = "",
    val photoBlob: Blob? = null,
    val status: String = "Activo"
) {
    fun hasValidLocation(): Boolean {
        return lat != null &&
                lon != null &&
                lat.isFinite() &&
                lon.isFinite() &&
                lat in -90.0..90.0 &&
                lon in -180.0..180.0
    }

    fun isActive(): Boolean {
        return status == "Activo"
    }
}