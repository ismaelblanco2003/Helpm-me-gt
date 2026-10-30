package com.example.helpme.controller

import com.example.helpme.model.Incident
import com.example.helpme.model.IncidentRepository
import com.google.firebase.firestore.ListenerRegistration

class MapController(
    private val repository: IncidentRepository = IncidentRepository()
) {

    /**
     * Escucha los incidentes activos en Firestore.
     *
     * Devuelve únicamente los incidentes
     * con coordenadas válidas.
     */
    fun observeMapIncidents(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return repository.observeActive(
            onSuccess = { incidents ->

                val validIncidents = incidents.filter {
                    it.hasValidLocation()
                }

                onSuccess(validIncidents)
            },
            onError = onError
        )
    }

    /**
     * Filtra los incidentes según la
     * categoría seleccionada en el mapa.
     *
     * "Todas" muestra todas las categorías.
     */
    fun filterByCategory(
        incidents: List<Incident>,
        category: String
    ): List<Incident> {

        val selectedCategory = category.trim()

        if (
            selectedCategory.isEmpty() ||
            selectedCategory.equals(
                "Todas",
                ignoreCase = true
            ) ||
            selectedCategory.equals(
                "Todos",
                ignoreCase = true
            ) ||
            selectedCategory.equals(
                "Todas las categorías",
                ignoreCase = true
            )
        ) {
            return incidents
        }

        return incidents.filter { incident ->
            incident.category.equals(
                selectedCategory,
                ignoreCase = true
            )
        }
    }

    /**
     * Obtiene los incidentes que deben
     * mostrarse como marcadores.
     */
    fun getMarkerIncidents(
        incidents: List<Incident>,
        category: String
    ): List<Incident> {

        return filterByCategory(
            incidents = incidents,
            category = category
        ).filter {
            it.hasValidLocation()
        }
    }

    /**
     * Obtiene los incidentes que deben
     * utilizarse en el mapa de calor.
     *
     * Utiliza los mismos filtros para
     * mantener consistencia con los marcadores.
     */
    fun getHeatmapIncidents(
        incidents: List<Incident>,
        category: String
    ): List<Incident> {

        return getMarkerIncidents(
            incidents = incidents,
            category = category
        )
    }

    /**
     * Cuenta los incidentes activos
     * de una categoría determinada.
     */
    fun countIncidents(
        incidents: List<Incident>,
        category: String
    ): Int {

        return getMarkerIncidents(
            incidents = incidents,
            category = category
        ).size
    }

    /**
     * Agrupa los incidentes por categoría.
     *
     * Podrá utilizarse más adelante
     * para estadísticas.
     */
    fun countByCategory(
        incidents: List<Incident>
    ): Map<String, Int> {

        return incidents
            .filter { it.hasValidLocation() }
            .groupingBy { it.category }
            .eachCount()
    }

    /**
     * Indica si existen incidentes
     * disponibles para mostrar en el mapa.
     */
    fun hasIncidents(
        incidents: List<Incident>,
        category: String
    ): Boolean {

        return getMarkerIncidents(
            incidents = incidents,
            category = category
        ).isNotEmpty()
    }
}