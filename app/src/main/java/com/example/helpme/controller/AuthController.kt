package com.example.helpme.controller

import android.util.Patterns

import com.example.helpme.model.AuthRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Controlador de autenticación
 * de HelpMe Guatemala.
 *
 * Valida los datos ingresados
 * por el usuario y delega las
 * operaciones de Firebase al
 * repositorio AuthRepository.
 */
class AuthController(
    private val repository: AuthRepository = AuthRepository()
) {

    /**
     * Obtiene el usuario autenticado.
     */
    fun getCurrentUser(): FirebaseUser? {
        return repository.getCurrentUser()
    }

    /**
     * Obtiene el UID del usuario actual.
     */
    fun getCurrentUserId(): String? {
        return repository.getCurrentUserId()
    }

    /**
     * Comprueba si existe una sesión activa.
     */
    fun isLoggedIn(): Boolean {
        return repository.isLoggedIn()
    }

    /**
     * Valida el correo y la contraseña
     * antes de iniciar sesión.
     */
    fun login(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        val cleanEmail = email.trim()

        if (cleanEmail.isEmpty()) {
            onError("Ingresa tu correo electrónico")
            return
        }

        if (!Patterns.EMAIL_ADDRESS
                .matcher(cleanEmail)
                .matches()
        ) {
            onError("Ingresa un correo electrónico válido")
            return
        }

        if (password.isEmpty()) {
            onError("Ingresa tu contraseña")
            return
        }

        repository.signIn(
            email = cleanEmail,
            password = password,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Valida los datos del usuario
     * antes de registrarlo en Firebase.
     */
    fun register(
        email: String,
        password: String,
        confirmPassword: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        val cleanEmail = email.trim()

        if (cleanEmail.isEmpty()) {
            onError("Ingresa tu correo electrónico")
            return
        }

        if (!Patterns.EMAIL_ADDRESS
                .matcher(cleanEmail)
                .matches()
        ) {
            onError("Ingresa un correo electrónico válido")
            return
        }

        if (password.length < 6) {
            onError(
                "La contraseña debe tener al menos 6 caracteres"
            )
            return
        }

        if (password != confirmPassword) {
            onError("Las contraseñas no coinciden")
            return
        }

        repository.register(
            email = cleanEmail,
            password = password,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Solicita el restablecimiento
     * de contraseña por correo.
     */
    fun resetPassword(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val cleanEmail = email.trim()

        if (cleanEmail.isEmpty()) {
            onError("Ingresa tu correo electrónico")
            return
        }

        if (!Patterns.EMAIL_ADDRESS
                .matcher(cleanEmail)
                .matches()
        ) {
            onError("Ingresa un correo electrónico válido")
            return
        }

        repository.resetPassword(
            email = cleanEmail,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Cierra la sesión actual.
     */
    fun logout() {
        repository.signOut()
    }
}