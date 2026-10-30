package com.example.helpme

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.FirebaseApp

/**
 * Actividad principal de HelpMe Guatemala.
 *
 * Su responsabilidad es iniciar Firebase
 * y dirigir al usuario a CloudActivity,
 * donde funciona la aplicación real con MVC.
 *
 * No almacena reportes locales ni duplica
 * las funciones de autenticación, mapas
 * o administración.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase.
        val firebaseApp = FirebaseApp.initializeApp(this)

        if (firebaseApp == null) {
            Toast.makeText(
                this,
                "No se pudo iniciar Firebase. Verifica la configuración de HelpMe.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        // Abrir la aplicación principal.
        val intent = Intent(
            this,
            CloudActivity::class.java
        )

        startActivity(intent)

        // Cerrar esta actividad para evitar
        // regresar a una pantalla vacía.
        finish()
    }
}