package com.example.helpme.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Repositorio de incidentes de HelpMe Guatemala.
 *
 * Centraliza la comunicación con Firestore.
 *
 * Los controladores utilizan este repositorio
 * para consultar, publicar y actualizar reportes.
 *
 * No contiene componentes de interfaz.
 */
class IncidentRepository(
    private val database: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        database.collection("incidents")

    /**
     * Convierte un documento de Firestore
     * en un objeto del modelo Incident.
     *
     * Conserva los campos de fotografías,
     * ubicación y estado.
     */
    private fun documentToIncident(
        document: DocumentSnapshot
    ): Incident {

        return Incident(
            id = document.id,

            category = document.getString("category")
                ?: "Otro problema",

            description = document.getString("description")
                ?: "",

            address = document.getString("address")
                ?: "",

            authorUid = document.getString("authorUid")
                ?: "",

            createdAt = document.getTimestamp(
                "createdAt"
            ),

            lat = document.getDouble("lat"),

            lon = document.getDouble("lon"),

            photoPath = document.getString("photoPath")
                ?: "",

            photoBlob = document.getBlob(
                "photoBlob"
            ),

            status = document.getString("status")
                ?: "Activo"
        )
    }

    /**
     * Escucha todos los incidentes
     * en tiempo real.
     *
     * Los resultados se ordenan desde
     * el más reciente hasta el más antiguo.
     */
    fun observeAll(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return collection
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    onError(
                        error.localizedMessage
                            ?: "Error al cargar incidentes"
                    )

                    return@addSnapshotListener
                }

                val incidents =
                    snapshot?.documents?.map { document ->
                        documentToIncident(document)
                    } ?: emptyList()

                onSuccess(incidents)
            }
    }

    /**
     * Escucha únicamente los incidentes
     * cuyo estado es "Activo".
     *
     * Este método se utiliza para mostrar
     * los reportes activos en el mapa.
     */
    fun observeActive(
        onSuccess: (List<Incident>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return collection
            .whereEqualTo(
                "status",
                "Activo"
            )
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    onError(
                        error.localizedMessage
                            ?: "Error al cargar incidentes activos"
                    )

                    return@addSnapshotListener
                }

                val incidents =
                    snapshot?.documents?.map { document ->
                        documentToIncident(document)
                    } ?: emptyList()

                onSuccess(incidents)
            }
    }

    /**
     * Publica un incidente nuevo
     * con un identificador generado
     * automáticamente por Firestore.
     *
     * Conserva la firma original
     * utilizada por los controladores.
     */
    fun createIncident(
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val document = collection.document()

        document.set(data)
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "No se pudo publicar el incidente"
                )
            }
    }

    /**
     * Publica un incidente utilizando
     * un identificador generado previamente.
     *
     * Mantiene la compatibilidad con
     * las fotografías de los reportes.
     */
    fun createIncident(
        incidentId: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (incidentId.isBlank()) {

            onError(
                "El identificador del incidente no es válido"
            )

            return
        }

        collection.document(incidentId)
            .set(data)
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "No se pudo publicar el incidente"
                )
            }
    }

    /**
     * Actualiza el estado de un incidente.
     *
     * Los valores previstos son:
     * Activo, Por verificar y Finalizado.
     *
     * Los permisos administrativos
     * deben verificarse mediante
     * las reglas de seguridad de Firestore.
     */
    fun updateStatus(
        incidentId: String,
        newStatus: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        collection.document(incidentId)
            .update(
                "status",
                newStatus
            )
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "No se pudo actualizar el estado"
                )
            }
    }

    /**
     * Registra una solicitud de revisión
     * para cerrar un incidente.
     *
     * La solicitud se guarda en:
     *
     * incidents/{incidentId}/
     * closureRequests/{userId}
     *
     * No modifica directamente
     * el estado del incidente.
     */
    fun requestClosure(
        incidentId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        collection.document(incidentId)
            .collection("closureRequests")
            .document(userId)
            .set(
                mapOf(
                    "requestedAt" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "No se pudo solicitar la revisión"
                )
            }
    }
}