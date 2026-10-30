package com.example.helpme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

import com.example.helpme.controller.NotificationController
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio de Firebase Cloud Messaging
 * para HelpMe Guatemala.
 *
 * Recibe mensajes push de Firebase
 * y utiliza NotificationController
 * para mostrar notificaciones locales.
 *
 * Firebase muestra automáticamente
 * las notificaciones con payload
 * "notification" cuando la aplicación
 * está en segundo plano.
 */
class HelpMeMessagingService : FirebaseMessagingService() {

    private val notificationController: NotificationController
        get() = NotificationController(applicationContext)

    /**
     * Recibe mensajes enviados mediante FCM.
     *
     * En primer plano mostramos una
     * notificación local.
     *
     * También permite mostrar mensajes
     * de tipo "data" recibidos por el servicio.
     */
    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        super.onMessageReceived(message)

        val title =
            message.notification?.title
                ?: message.data["title"]
                ?: "HelpMe · Nuevo reporte ciudadano"

        val body =
            message.notification?.body
                ?: message.data["body"]
                ?: "Hay un incidente reportado en Guatemala; verifica antes de actuar."

        val notificationId =
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val displayed =
            notificationController.showNotification(
                notificationId = notificationId,
                title = title,
                message = body
            )

        if (!displayed) {
            Log.w(
                TAG,
                "No se pudo mostrar la notificación. " +
                        "Verifica el permiso de notificaciones."
            )
        }
    }

    /**
     * Firebase puede renovar el token
     * de este dispositivo.
     *
     * Lo registramos para diagnóstico.
     * La suscripción al tema
     * reportes_guatemala continúa
     * gestionándose desde CloudActivity.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(
            TAG,
            "Token FCM actualizado"
        )
    }

    companion object {

        private const val TAG =
            "HelpMeMessagingService"

        /**
         * Canal original de HelpMe.
         *
         * Se conserva para mantener
         * compatibilidad con el código
         * existente de CloudActivity.
         */
        private const val CHANNEL_ID =
            "helpme_alertas"

        /**
         * Mantiene el método original
         * createChannel(context).
         *
         * CloudActivity puede seguir
         * llamándolo sin cambios.
         */
        fun createChannel(context: Context) {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                val manager =
                    context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                    ) as NotificationManager

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alertas ciudadanas HelpMe",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {

                    description =
                        "Avisos de incidentes reportados " +
                                "por ciudadanos, aún sin verificar"
                }

                manager.createNotificationChannel(
                    channel
                )
            }

            // Preparar también el canal
            // administrado por MVC.
            NotificationController(
                context.applicationContext
            ).createNotificationChannel()
        }
    }
}