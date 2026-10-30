
package com.example.helpme

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

import com.example.helpme.controller.MapController
import com.example.helpme.model.Incident
import com.google.firebase.firestore.ListenerRegistration

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import java.text.SimpleDateFormat
import java.util.Locale

class IncidentMapActivity : Activity() {

    // Colores HelpMe
    private val ink = Color.rgb(22, 39, 70)
    private val blue = Color.rgb(39, 95, 222)
    private val violet = Color.rgb(113, 74, 216)
    private val teal = Color.rgb(18, 135, 121)
    private val grey = Color.rgb(100, 115, 138)
    private val amber = Color.rgb(193, 117, 18)
    private val red = Color.rgb(195, 55, 68)

    private val softBlue = Color.rgb(235, 241, 255)
    private val softTeal = Color.rgb(228, 246, 242)
    private val softAmber = Color.rgb(255, 245, 229)
    private val softRed = Color.rgb(255, 237, 239)

    private lateinit var map: MapView
    private lateinit var categorySpinner: Spinner
    private lateinit var heatSwitch: Switch
    private lateinit var counter: TextView

    private val mapController = MapController()

    private var registration: ListenerRegistration? = null

    private val heatOverlay = IncidentHeatmapOverlay()
    private val markers = mutableListOf<Marker>()

    private var incidents: List<Incident> = emptyList()
    private var incidentsLoaded = false

    private var lastAutoCenteredIncidentId: String? = null

    private val categories = listOf(
        "Todas",
        "Accidente de tránsito",
        "Calle dañada",
        "Fuga de agua",
        "Incendio",
        "Seguridad",
        "Otro problema"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración original de osmdroid.
        val config = Configuration.getInstance()

        config.load(
            applicationContext,
            getSharedPreferences(
                "osmdroid",
                MODE_PRIVATE
            )
        )

        config.userAgentValue =
            "HelpMeGuatemala/2.2 ($packageName)"

        // Configuración original de MapTiler.
        val apiKey = BuildConfig.MAPTILER_API_KEY

        if (apiKey.isBlank()) {
            Toast.makeText(
                this,
                "Falta configurar MAPTILER_API_KEY",
                Toast.LENGTH_LONG
            ).show()
        }

        val mapTilerSource =
            object : OnlineTileSourceBase(
                "MapTiler Streets",
                0,
                19,
                256,
                ".png",
                arrayOf(
                    "https://api.maptiler.com/maps/streets-v2/"
                ),
                "© MapTiler © OpenStreetMap contributors"
            ) {

                override fun getTileURLString(
                    pMapTileIndex: Long
                ): String {

                    val zoom =
                        MapTileIndex.getZoom(pMapTileIndex)

                    val x =
                        MapTileIndex.getX(pMapTileIndex)

                    val y =
                        MapTileIndex.getY(pMapTileIndex)

                    return "https://api.maptiler.com/maps/streets-v2/" +
                            "$zoom/$x/$y.png?key=$apiKey"
                }
            }

        // Crear mapa.
        map = MapView(this)

        map.setTileSource(mapTilerSource)
        map.setMultiTouchControls(true)

        // Vista inicial de Guatemala.
        map.controller.setZoom(7.0)

        map.controller.setCenter(
            GeoPoint(
                15.7835,
                -90.2308
            )
        )

        // Encabezado de filtros.
        val filterTitle = TextView(this).apply {
            text = "Filtrar incidentes por categoría"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, 0, 0, dp(8))
        }

        // Selector de categorías.
        categorySpinner = Spinner(this).apply {
            background = roundedBackground(
                Color.rgb(240, 244, 248),
                12
            )

            minimumHeight = dp(48)

            setPadding(
                dp(12),
                0,
                dp(12),
                0
            )
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        categorySpinner.adapter = adapter
        categorySpinner.setSelection(0)

        // Interruptor del mapa de calor.
        heatSwitch = Switch(this).apply {
            text = "Mostrar mapa de calor"
            textSize = 14f
            setTextColor(Color.DKGRAY)

            setPadding(
                0,
                dp(8),
                0,
                dp(8)
            )

            setOnCheckedChangeListener { _, _ ->
                renderMap()
            }
        }

        // Contador.
        counter = TextView(this).apply {
            text = "Cargando incidentes activos..."
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(blue)

            setPadding(
                0,
                dp(8),
                0,
                dp(4)
            )
        }

        // Panel de controles.
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)

            setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(12)
            )

            addView(filterTitle)

            addView(
                categorySpinner,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
            )

            addView(heatSwitch)
            addView(counter)
        }

        // Contenedor principal.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)

            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                map,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        setContentView(root)

        // Filtro de categorías.
        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    lastAutoCenteredIncidentId = null
                    renderMap()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        loadActiveIncidents()
    }

    // =========================================
    // FIRESTORE MEDIANTE MAPCONTROLLER
    // =========================================

    private fun loadActiveIncidents() {

        registration?.remove()

        registration = mapController.observeMapIncidents(

            onSuccess = { receivedIncidents ->

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    incidents = receivedIncidents
                    incidentsLoaded = true

                    renderMap()
                }
            },

            onError = { errorMessage ->

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    counter.text =
                        "Error al cargar incidentes"

                    Toast.makeText(
                        this,
                        "Error Firestore: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // =========================================
    // MAPA NORMAL Y MAPA DE CALOR
    // =========================================

    private fun renderMap() {

        if (
            !::map.isInitialized ||
            !::categorySpinner.isInitialized ||
            !::heatSwitch.isInitialized ||
            !::counter.isInitialized
        ) {
            return
        }

        if (!incidentsLoaded) {
            counter.text =
                "Cargando incidentes activos..."
            return
        }

        // Eliminar marcadores anteriores.
        markers.forEach { marker ->
            map.overlays.remove(marker)
        }

        markers.clear()

        // Eliminar calor anterior.
        map.overlays.remove(heatOverlay)

        val selectedCategory =
            categorySpinner.selectedItem
                ?.toString() ?: "Todas"

        // Filtro MVC.
        val filtered = mapController.getMarkerIncidents(
            incidents = incidents,
            category = selectedCategory
        )

        val total = filtered.size

        counter.text = when {

            selectedCategory == "Todas" ->
                "Total: $total incidentes activos con GPS"

            total == 1 ->
                "1 incidente de: $selectedCategory"

            else ->
                "$total incidentes de: $selectedCategory"
        }

        if (heatSwitch.isChecked) {

            // MODO CALOR:
            // No mostrar marcadores.

            val heatIncidents =
                mapController.getHeatmapIncidents(
                    incidents = incidents,
                    category = selectedCategory
                )

            heatOverlay.locations =
                heatIncidents.mapNotNull { incident ->

                    val latitude = incident.lat
                    val longitude = incident.lon

                    if (
                        latitude != null &&
                        longitude != null
                    ) {
                        GeoPoint(
                            latitude,
                            longitude
                        )
                    } else {
                        null
                    }
                }

            map.overlays.add(
                0,
                heatOverlay
            )

        } else {

            // MODO NORMAL:
            // Marcadores con tarjetas modernas.

            filtered.forEach { incident ->

                val latitude = incident.lat
                    ?: return@forEach

                val longitude = incident.lon
                    ?: return@forEach

                val marker = Marker(map).apply {

                    position = GeoPoint(
                        latitude,
                        longitude
                    )

                    title = incident.category

                    snippet = incident.description

                    icon = makeMarkerIcon(
                        colorForCategory(
                            incident.category
                        )
                    )

                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_CENTER
                    )
                }

                // NUEVO:
                // Al pulsar el marcador se abre
                // una tarjeta personalizada.
                marker.setOnMarkerClickListener { _, _ ->

                    showIncidentCard(incident)

                    true
                }

                markers.add(marker)
                map.overlays.add(marker)
            }
        }

        // Centrado automático original.
        autoCenterSingleIncident(filtered)

        map.invalidate()
    }

    // =========================================
    // NUEVA TARJETA VISUAL DEL INCIDENTE
    // =========================================

    private fun showIncidentCard(incident: Incident) {

        if (isFinishing || isDestroyed) return

        // Contenedor principal.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(18)
            )
        }

        // Encabezado con degradado.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, violet)
            ).apply {
                cornerRadius = dp(18).toFloat()
            }

            setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
            )
        }

        header.addView(
            labelText(
                "✦ HELP ME · GUATEMALA",
                11f,
                Color.WHITE,
                true
            )
        )

        header.addView(
            labelText(
                "${categoryIcon(incident.category)} ${incident.category}",
                20f,
                Color.WHITE,
                true
            )
        )

        header.addView(
            labelText(
                "Reporte ciudadano en el mapa",
                13f,
                Color.WHITE
            )
        )

        content.addView(header)

        addVerticalSpace(content, 15)

        // Estado del incidente.
        val stateColor = when (incident.status) {
            "Finalizado" -> teal
            "Por verificar" -> amber
            else -> red
        }

        val stateBackground = when (incident.status) {
            "Finalizado" -> softTeal
            "Por verificar" -> softAmber
            else -> softRed
        }

        val stateSymbol = when (incident.status) {
            "Finalizado" -> "✓"
            "Por verificar" -> "◷"
            else -> "●"
        }

        val status = labelText(
            "$stateSymbol ${incident.status}",
            13f,
            stateColor,
            true
        ).apply {

            gravity = Gravity.CENTER

            background = roundedBackground(
                stateBackground,
                100
            )

            setPadding(
                dp(13),
                dp(8),
                dp(13),
                dp(8)
            )
        }

        content.addView(
            status,
            LinearLayout.LayoutParams(
                -2,
                -2
            )
        )

        addVerticalSpace(content, 15)

        // Descripción.
        content.addView(
            labelText(
                "¿QUÉ SUCEDIÓ?",
                11f,
                grey,
                true
            )
        )

        content.addView(
            labelText(
                incident.description.ifBlank {
                    "Sin descripción disponible"
                },
                16f,
                ink
            )
        )

        addCardDivider(content)

        // Ubicación.
        content.addView(
            labelText(
                "UBICACIÓN",
                11f,
                grey,
                true
            )
        )

        val locationDescription =
            if (incident.address.isNotBlank()) {
                incident.address
            } else {
                "Ubicación GPS registrada"
            }

        content.addView(
            labelText(
                "📍 $locationDescription",
                14f,
                ink
            )
        )

        // Coordenadas para mayor precisión.
        if (
            incident.lat != null &&
            incident.lon != null
        ) {
            val coordinates = String.format(
                Locale.US,
                "%.5f, %.5f",
                incident.lat,
                incident.lon
            )

            content.addView(
                labelText(
                    "Coordenadas: $coordinates",
                    12f,
                    grey
                )
            )
        }

        addCardDivider(content)

        // Fecha.
        content.addView(
            labelText(
                "FECHA DEL REPORTE",
                11f,
                grey,
                true
            )
        )

        val date = incident.createdAt
            ?.toDate()
            ?.let {
                SimpleDateFormat(
                    "dd/MM/yyyy · HH:mm",
                    Locale.getDefault()
                ).format(it)
            } ?: "Fecha no disponible"

        content.addView(
            labelText(
                "🕒 $date",
                14f,
                ink
            )
        )

        // Fotografía opcional.
        val blob = incident.photoBlob

        if (blob != null) {

            try {
                val bytes = blob.toBytes()

                val bitmap = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )

                if (bitmap != null) {

                    addCardDivider(content)

                    content.addView(
                        labelText(
                            "FOTOGRAFÍA DEL INCIDENTE",
                            11f,
                            grey,
                            true
                        )
                    )

                    addVerticalSpace(content, 8)

                    val image = ImageView(this).apply {
                        setImageBitmap(bitmap)

                        adjustViewBounds = true

                        maxHeight = dp(230)

                        scaleType =
                            ImageView.ScaleType.FIT_CENTER

                        background = roundedBackground(
                            Color.rgb(244, 247, 253),
                            12
                        )

                        setPadding(
                            dp(5),
                            dp(5),
                            dp(5),
                            dp(5)
                        )
                    }

                    content.addView(
                        image,
                        LinearLayout.LayoutParams(
                            -1,
                            -2
                        )
                    )
                }

            } catch (_: Exception) {
                // Si una foto falla, el resto
                // de la tarjeta sigue visible.
            }
        }

        addVerticalSpace(content, 12)

        // Aviso informativo.
        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            background = roundedBackground(
                softBlue,
                12
            )

            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )
        }

        notice.addView(
            labelText(
                "ℹ️ Información ciudadana",
                13f,
                blue,
                true
            )
        )

        notice.addView(
            labelText(
                "Este reporte fue compartido por un ciudadano. Verifica la situación antes de actuar.",
                12f,
                grey
            )
        )

        content.addView(notice)

        // Scroll para pantallas pequeñas.
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }

        // Dialog de detalles.
        val dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton(
                "Cerrar",
                null
            )
            .setPositiveButton(
                "🗺️ Abrir ubicación"
            ) { _, _ ->
                openIncidentLocation(incident)
            }
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )?.setTextColor(blue)

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            )?.setTextColor(grey)
        }

        dialog.show()
    }

    // =========================================
    // GOOGLE MAPS DESDE LA TARJETA
    // =========================================

    private fun openIncidentLocation(incident: Incident) {

        val query = if (
            incident.lat != null &&
            incident.lon != null
        ) {
            "${incident.lat},${incident.lon}"
        } else {
            incident.address
        }

        if (query.isBlank()) {
            Toast.makeText(
                this,
                "Este reporte no tiene ubicación disponible",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val uri = Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=" +
                    Uri.encode(query)
        )

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Instala Google Maps o un navegador",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================
    // COMPONENTES DE DISEÑO DE LA TARJETA
    // =========================================

    private fun roundedBackground(
        color: Int,
        radius: Int = 14
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun labelText(
        value: String,
        size: Float = 14f,
        color: Int = ink,
        bold: Boolean = false
    ): TextView {

        return TextView(this).apply {
            text = value
            textSize = size

            setTextColor(color)

            if (bold) {
                setTypeface(
                    null,
                    Typeface.BOLD
                )
            }

            setPadding(
                dp(2),
                dp(4),
                dp(2),
                dp(4)
            )
        }
    }

    private fun addVerticalSpace(
        container: LinearLayout,
        height: Int
    ) {

        container.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun addCardDivider(
        container: LinearLayout
    ) {

        container.addView(
            View(this).apply {
                setBackgroundColor(
                    Color.rgb(226, 233, 245)
                )
            },
            LinearLayout.LayoutParams(
                -1,
                dp(1)
            ).apply {
                topMargin = dp(13)
                bottomMargin = dp(13)
            }
        )
    }

    private fun categoryIcon(
        category: String
    ): String {

        return when (category) {

            "Accidente de tránsito" -> "🚗"

            "Calle dañada" -> "🚧"

            "Fuga de agua" -> "💧"

            "Incendio" -> "🔥"

            "Seguridad" -> "🛡️"

            else -> "📍"
        }
    }

    // =========================================
    // CENTRADO AUTOMÁTICO
    // =========================================

    private fun autoCenterSingleIncident(
        filtered: List<Incident>
    ) {

        if (filtered.size != 1) {
            lastAutoCenteredIncidentId = null
            return
        }

        val incident = filtered.first()

        val latitude = incident.lat ?: return
        val longitude = incident.lon ?: return

        if (
            lastAutoCenteredIncidentId ==
            incident.id
        ) {
            return
        }

        lastAutoCenteredIncidentId = incident.id

        val location = GeoPoint(
            latitude,
            longitude
        )

        map.controller.setZoom(16.0)
        map.controller.animateTo(location)
    }

    // =========================================
    // MARCADORES ORIGINALES
    // =========================================

    private fun colorForCategory(
        category: String
    ): Int {

        return when (category) {

            "Accidente de tránsito" ->
                Color.RED

            "Calle dañada" ->
                Color.rgb(255, 140, 0)

            "Fuga de agua" ->
                Color.rgb(0, 120, 255)

            "Incendio" ->
                Color.rgb(180, 40, 40)

            "Seguridad" ->
                Color.rgb(140, 60, 200)

            "Otro problema" ->
                Color.rgb(0, 150, 100)

            else ->
                Color.GRAY
        }
    }

    private fun makeMarkerIcon(
        color: Int
    ): BitmapDrawable {

        val bitmap = Bitmap.createBitmap(
            52,
            52,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        // Borde blanco.
        paint.color = Color.WHITE

        canvas.drawCircle(
            26f,
            26f,
            23f,
            paint
        )

        // Centro de color.
        paint.color = color

        canvas.drawCircle(
            26f,
            26f,
            18f,
            paint
        )

        return BitmapDrawable(
            resources,
            bitmap
        )
    }

    private fun dp(value: Int): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    // =========================================
    // CICLO DE VIDA DEL MAPA
    // =========================================

    override fun onResume() {
        super.onResume()

        if (::map.isInitialized) {
            map.onResume()
        }
    }

    override fun onPause() {

        if (::map.isInitialized) {
            map.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {

        registration?.remove()
        registration = null

        if (::map.isInitialized) {
            map.onDetach()
        }

        super.onDestroy()
    }
}