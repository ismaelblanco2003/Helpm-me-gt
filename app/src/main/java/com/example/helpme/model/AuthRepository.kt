package com.example.helpme.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Repositorio de autenticación
 * de HelpMe Guatemala.
 *
 * Centraliza la comunicación
 * con Firebase Authentication.
 *
 * AuthController utiliza este repositorio
 * para iniciar sesión, registrar usuarios
 * y administrar la sesión actual.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Devuelve el usuario que tiene
     * una sesión iniciada.
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Devuelve el UID del usuario actual.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Comprueba si existe una sesión activa.
     */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Inicia sesión con correo electrónico
     * y contraseña mediante Firebase.
     */
    fun signIn(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        auth.signInWithEmailAndPassword(
            email.trim(),
            password
        )
            .addOnSuccessListener { result ->

                val user = result.user

                if (user != null) {
                    onSuccess(user)
                } else {
                    onError(
                        "No se pudo obtener el usuario"
                    )
                }
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "Error al iniciar sesión"
                )
            }
    }

    /**
     * Registra un usuario nuevo
     * con correo electrónico y contraseña.
     */
    fun register(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        auth.createUserWithEmailAndPassword(
            email.trim(),
            password
        )
            .addOnSuccessListener { result ->

                val user = result.user

                if (user != null) {
                    onSuccess(user)
                } else {
                    onError(
                        "No se pudo registrar el usuario"
                    )
                }
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "Error al registrar el usuario"
                )
            }
    }

    /**
     * Envía un correo electrónico
     * para restablecer la contraseña.
     */
    fun resetPassword(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        auth.sendPasswordResetEmail(
            email.trim()
        )
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.localizedMessage
                        ?: "No se pudo enviar el correo"
                )
            }
    }

    /**
     * Cierra la sesión actual
     * de Firebase Authentication.
     */
    fun signOut() {
        auth.signOut()
    }
}