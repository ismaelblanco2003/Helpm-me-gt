package com.example.helpme.controller

import com.example.helpme.model.Incident
import com.example.helpme.model.IncidentRepository
import com.google.firebase.firestore.ListenerRegistration

class IncidentController(
    private val repository: IncidentRepository = IncidentRepository()
) {

    /**
     * Escucha todos los reportes en tiempo real.
     */
    fun observeAllIncidents(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return repository.observeAll(
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Escucha solamente los reportes activos.
     */
    fun observeActiveIncidents(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return repository.observeActive(
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Filtra reportes por categoría.
     */
    fun filterByCategory(
        incidents: List<Incident>,
        category: String
    ): List<Incident> {

        val selected = category.trim()

        if (
            selected.isBlank() ||
            selected.equals("Todas", ignoreCase = true) ||
            selected.equals("Todos", ignoreCase = true) ||
            selected.equals(
                "Todas las categorías",
                ignoreCase = true
            )
        ) {
            return incidents
        }

        return incidents.filter {
            it.category.equals(
                selected,
                ignoreCase = true
            )
        }
    }

    /**
     * Obtiene los reportes con coordenadas válidas.
     */
    fun getIncidentsWithLocation(
        incidents: List<Incident>
    ): List<Incident> {

        return incidents.filter {
            it.hasValidLocation()
        }
    }

    /**
     * Valida los datos antes de publicar.
     *
     * Se permite:
     * - Dirección sin GPS.
     * - GPS sin dirección.
     * - Dirección y GPS juntos.
     *
     * No se permite publicar sin ninguna ubicación.
     */
    fun validateIncident(
        category: String,
        description: String,
        latitude: Double?,
        longitude: Double?,
        address: String = ""
    ): String? {

        if (category.isBlank()) {
            return "Selecciona una categoría"
        }

        if (description.trim().length < 8) {
            return "Escribe una descripción de al menos 8 caracteres"
        }

        val hasLatitude = latitude != null
        val hasLongitude = longitude != null

        if (hasLatitude != hasLongitude) {
            return "La ubicación GPS está incompleta"
        }

        if (latitude != null && longitude != null) {

            if (
                !latitude.isFinite() ||
                !longitude.isFinite() ||
                latitude !in -90.0..90.0 ||
                longitude !in -180.0..180.0
            ) {
                return "Las coordenadas GPS no son válidas"
            }
        }

        if (
            address.isBlank() &&
            (latitude == null || longitude == null)
        ) {
            return "Indica una dirección o selecciona tu ubicación"
        }

        return null
    }

    /**
     * Publica un reporte con un identificador
     * generado automáticamente por el repositorio.
     *
     * Se conserva para mantener compatibilidad
     * con las demás pantallas.
     */
    fun createIncident(
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val validationError = validateIncidentData(data)

        if (validationError != null) {
            onError(validationError)
            return
        }

        repository.createIncident(
            data = data,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * NUEVO:
     * Publica un reporte usando un ID previamente
     * generado por la pantalla.
     *
     * Esto conserva la relación entre el reporte
     * y su identificador original.
     *
     * También conserva photoBlob y todos los
     * campos existentes del documento.
     */
    fun createIncident(
        incidentId: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (incidentId.isBlank()) {
            onError("El identificador del reporte no es válido")
            return
        }

        val validationError = validateIncidentData(data)

        if (validationError != null) {
            onError(validationError)
            return
        }

        repository.createIncident(
            incidentId = incidentId,
            data = data,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Extrae los datos necesarios para validarlos
     * sin modificar los campos de Firestore.
     */
    private fun validateIncidentData(
        data: Map<String, Any?>
    ): String? {

        val category = data["category"] as? String
            ?: ""

        val description = data["description"] as? String
            ?: ""

        val address = data["address"] as? String
            ?: ""

        val latitude = (data["lat"] as? Number)
            ?.toDouble()

        val longitude = (data["lon"] as? Number)
            ?.toDouble()

        return validateIncident(
            category = category,
            description = description,
            latitude = latitude,
            longitude = longitude,
            address = address
        )
    }

    /**
     * Actualiza el estado de un reporte.
     *
     * Los permisos de administrador continúan
     * verificándose mediante Firebase.
     */
    fun updateIncidentStatus(
        incidentId: String,
        newStatus: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (incidentId.isBlank()) {
            onError("El identificador del reporte no es válido")
            return
        }

        val validStatuses = listOf(
            "Activo",
            "Por verificar",
            "Finalizado"
        )

        if (newStatus !in validStatuses) {
            onError("El estado seleccionado no es válido")
            return
        }

        repository.updateStatus(
            incidentId = incidentId,
            newStatus = newStatus,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Solicita una revisión de cierre.
     *
     * Un ciudadano no puede finalizar directamente
     * un reporte mediante esta función.
     */
    fun requestIncidentClosure(
        incidentId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (incidentId.isBlank()) {
            onError("El identificador del reporte no es válido")
            return
        }

        if (userId.isBlank()) {
            onError("Debes iniciar sesión para solicitar revisión")
            return
        }

        repository.requestClosure(
            incidentId = incidentId,
            userId = userId,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}