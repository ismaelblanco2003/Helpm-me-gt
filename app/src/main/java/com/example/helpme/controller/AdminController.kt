package com.example.helpme.controller

import com.example.helpme.model.Incident
import com.example.helpme.model.IncidentRepository
import com.google.firebase.firestore.ListenerRegistration

class AdminController(
    private val repository: IncidentRepository = IncidentRepository()
) {

    /**
     * Estados permitidos para los reportes.
     */
    private val validStatuses = setOf(
        "Activo",
        "Por verificar",
        "Finalizado"
    )

    /**
     * Escucha todos los incidentes en tiempo real
     * para el panel de administración.
     *
     * Las consultas siguen sujetas a las reglas
     * de seguridad de Firebase Firestore.
     */
    fun observeIncidents(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return repository.observeAll(
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Obtiene solamente los incidentes activos.
     */
    fun getActiveIncidents(
        incidents: List<Incident>
    ): List<Incident> {

        return incidents.filter { incident ->
            incident.status == "Activo"
        }
    }

    /**
     * Filtra los incidentes por estado.
     *
     * Un filtro vacío o "Todos" muestra
     * todos los reportes.
     */
    fun filterByStatus(
        incidents: List<Incident>,
        status: String
    ): List<Incident> {

        val selectedStatus = status.trim()

        if (
            selectedStatus.isEmpty() ||
            selectedStatus.equals(
                "Todos",
                ignoreCase = true
            )
        ) {
            return incidents
        }

        return incidents.filter { incident ->
            incident.status.equals(
                selectedStatus,
                ignoreCase = true
            )
        }
    }

    /**
     * Solicita actualizar el estado de
     * un incidente en Firestore.
     *
     * Solamente acepta:
     * - Activo
     * - Por verificar
     * - Finalizado
     *
     * La autorización real depende de las
     * reglas de seguridad de Firestore.
     */
    fun updateIncidentStatus(
        incidentId: String,
        newStatus: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (incidentId.isBlank()) {
            onError(
                "El identificador del incidente no es válido"
            )
            return
        }

        if (newStatus !in validStatuses) {
            onError(
                "Selecciona un estado válido"
            )
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
     * Cuenta todos los incidentes
     * recibidos por el panel.
     */
    fun countAllIncidents(
        incidents: List<Incident>
    ): Int {

        return incidents.size
    }

    /**
     * Cuenta solamente los incidentes
     * cuyo estado es "Activo".
     */
    fun countActiveIncidents(
        incidents: List<Incident>
    ): Int {

        return getActiveIncidents(incidents).size
    }

    /**
     * Agrupa y cuenta incidentes por estado.
     *
     * Ejemplo:
     * Activo -> 5
     * Por verificar -> 2
     * Finalizado -> 3
     */
    fun countByStatus(
        incidents: List<Incident>
    ): Map<String, Int> {

        return incidents
            .groupingBy { incident ->
                incident.status
            }
            .eachCount()
    }

    /**
     * Agrupa y cuenta incidentes por categoría.
     *
     * Ejemplo:
     * Accidente de tránsito -> 4
     * Calle dañada -> 3
     */
    fun countByCategory(
        incidents: List<Incident>
    ): Map<String, Int> {

        return incidents
            .groupingBy { incident ->
                incident.category
            }
            .eachCount()
    }
}