
package com.example.helpme

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Capa visual del mapa de calor
 * de incidentes de HelpMe Guatemala.
 *
 * Recibe las ubicaciones filtradas
 * desde IncidentMapActivity.
 *
 * No consulta Firebase directamente.
 * La lógica de datos corresponde
 * a MapController.
 */
class IncidentHeatmapOverlay : Overlay() {

    /**
     * Ubicaciones que se dibujarán
     * sobre el mapa.
     */
    var locations: List<GeoPoint> = emptyList()

    /**
     * Radio visual de cada punto
     * del mapa de calor.
     */
    private val heatRadius = 75f

    /**
     * Reutilizamos Paint para evitar
     * crear uno nuevo en cada dibujo.
     */
    private val heatPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    /**
     * Colores originales del mapa
     * de calor de HelpMe.
     */
    private val heatColors = intArrayOf(
        Color.argb(160, 255, 40, 0),
        Color.argb(100, 255, 180, 0),
        Color.TRANSPARENT
    )

    /**
     * Distribución de colores
     * dentro del gradiente.
     */
    private val heatStops = floatArrayOf(
        0f,
        0.45f,
        1f
    )

    /**
     * Dibuja los puntos de calor
     * en sus posiciones geográficas.
     */
    override fun draw(
        canvas: Canvas,
        mapView: MapView,
        shadow: Boolean
    ) {

        if (shadow) {
            return
        }

        if (locations.isEmpty()) {
            return
        }

        val projection = mapView.projection

        locations.forEach { location ->

            val point = projection.toPixels(
                location,
                null
            )

            val centerX = point.x.toFloat()
            val centerY = point.y.toFloat()

            heatPaint.shader = RadialGradient(
                centerX,
                centerY,
                heatRadius,
                heatColors,
                heatStops,
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(
                centerX,
                centerY,
                heatRadius,
                heatPaint
            )
        }

        // Limpiar shader para el
        // siguiente redibujado.
        heatPaint.shader = null
    }
}