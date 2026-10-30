package com.example.helpme.controller

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.example.helpme.MainActivity

class NotificationController(
    context: Context
) {

    private val appContext = context.applicationContext

    companion object {

        const val CHANNEL_ID = "helpme_incidents"

        private const val CHANNEL_NAME =
            "Incidentes HelpMe"

        private const val CHANNEL_DESCRIPTION =
            "Notificaciones de incidentes y avisos de HelpMe Guatemala"
    }

    /**
     * Crea el canal de notificaciones
     * para Android 8.0 o superior.
     */
    fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description = CHANNEL_DESCRIPTION
            }

            val manager = appContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Comprueba si la aplicación tiene
     * permiso para mostrar notificaciones.
     */
    fun hasNotificationPermission(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Crea la acción para abrir HelpMe
     * al tocar una notificación.
     */
    private fun createOpenAppIntent(): PendingIntent {

        val intent = Intent(
            appContext,
            MainActivity::class.java
        ).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Muestra una notificación local.
     *
     * Devuelve true si pudo enviarse
     * al sistema de notificaciones.
     *
     * Devuelve false si falta el permiso
     * o se produce un error de seguridad.
     */
    fun showNotification(
        notificationId: Int,
        title: String,
        message: String
    ): Boolean {

        if (!hasNotificationPermission()) {
            return false
        }

        createNotificationChannel()

        val notification = NotificationCompat.Builder(
            appContext,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .setPriority(
                NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(
                createOpenAppIntent()
            )
            .setAutoCancel(true)
            .build()

        return try {

            NotificationManagerCompat.from(appContext)
                .notify(
                    notificationId,
                    notification
                )

            true

        } catch (exception: SecurityException) {

            false
        }
    }

    /**
     * Cancela una notificación local
     * mediante su identificador.
     */
    fun cancelNotification(
        notificationId: Int
    ) {

        NotificationManagerCompat.from(appContext)
            .cancel(notificationId)
    }

    /**
     * Cancela todas las notificaciones
     * locales de la aplicación.
     */
    fun cancelAllNotifications() {

        NotificationManagerCompat.from(appContext)
            .cancelAll()
    }
}