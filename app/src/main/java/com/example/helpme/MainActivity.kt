package com.example.helpme

import android.Manifest
import android.app.Activity
import com.google.firebase.FirebaseApp
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Incident(
    val id: Long, val category: String, val description: String,
    val address: String, val created: String, val author: String,
    val lat: Double?, val lon: Double?, val photo: String,
    var status: String = "Activo", var votes: Int = 0
)

class MainActivity : Activity() {
    private val ink = Color.rgb(22, 39, 70)
    private val primary = Color.rgb(39, 95, 222)
    private val violet = Color.rgb(113, 74, 216)
    private val muted = Color.rgb(100, 115, 138)
    private val bg = Color.rgb(244, 247, 253)
    private val items = mutableListOf<Incident>()
    private lateinit var body: LinearLayout
    private var currentUser = ""
    private val admin = false
    private var photoPath = ""
    private var lat: Double? = null
    private var lon: Double? = null
    private var locationInfo: TextView? = null
    private var photoInfo: TextView? = null
    private val cameraRequest = 301
    private val locationRequest = 302
    private val notificationRequest = 303

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun gradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(primary, violet)
    ).apply { cornerRadius = dp(22).toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si google-services.json está configurado, usamos las cuentas seguras
        // y los reportes compartidos en Firebase. De lo contrario queda una demo local.
        if (FirebaseApp.initializeApp(this) != null) {
            startActivity(Intent(this, CloudActivity::class.java))
            finish()
            return
        }
        load()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
        }
        login()
    }

    private fun text(s: String, size: Float = 15f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }

    private fun button(s: String, color: Int = primary, click: () -> Unit): TextView =
        text(s, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = shape(color, 13)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { click() }
        }

    private fun space(h: Int = 12) { body.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(Color.WHITE)
        elevation = dp(3).toFloat()
        setPadding(dp(17), dp(15), dp(17), dp(15))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(13)
        body.addView(this, lp)
    }
    private fun section(title: String) { space(9); body.addView(text(title, 20f, ink, true)); space(8) }
    private fun page(title: String, subtitle: String = "") {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg) }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(22), dp(19), dp(32))
        }
        scroll.addView(body); setContentView(scroll)
        body.addView(text("✦ HelpMe  ·  GUATEMALA", 15f, primary, true))
        space(10)
        body.addView(text(title, 29f, ink, true))
        if (subtitle.isNotEmpty()) body.addView(text(subtitle, 14f, muted))
        space(15)
    }
    private fun field(hint: String, lines: Int = 1): EditText = EditText(this).apply {
        this.hint = hint; textSize = 15f; setTextColor(ink)
        setSingleLine(lines == 1)
        if (lines > 1) { minLines = lines; gravity = Gravity.TOP }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = shape(Color.rgb(237, 242, 251), 11)
    }
    private fun addButton(title: String, color: Int = primary, click: () -> Unit) {
        body.addView(button(title, color, click)); space(10)
    }

    private fun login() {
        page("Bienvenido a HelpMe", "Reportes ciudadanos, alertas y ubicaciones en Guatemala")
        val banner = card().apply { background = gradient() }
        banner.addView(text("🚦  Una comunidad más informada", 23f, Color.WHITE, true))
        banner.addView(text("Reporta accidentes, comparte ubicaciones y consulta su estado.", 15f, Color.WHITE))
        section("Entrar como ciudadano")
        val name = field("Tu nombre o alias")
        body.addView(name); space()
        addButton("Continuar como ciudadano") {
            val value = name.text.toString().trim()
            if (value.isBlank()) { toast("Escribe tu nombre o alias"); return@addButton }
            currentUser = value; home()
        }
        section("Acceso del personal autorizado")
        addButton("🛡️ Entrar como administrador", Color.rgb(20, 133, 122)) {
            AlertDialog.Builder(this)
                .setTitle("Acceso administrativo protegido")
                .setMessage("La entrada administrativa se activa al conectar HelpMe con Firebase. " +
                    "Allí iniciarás sesión con tu cuenta y contraseña privadas. " +
                    "Por seguridad, la aplicación no lleva un PIN ni una contraseña fija.")
                .setPositiveButton("Entendido", null)
                .show()
        }
        space(8)
        body.addView(text("MODO DE DEMOSTRACIÓN · Puedes probar los reportes locales. " +
            "Para usar el acceso administrativo seguro, compartir reportes y recibir alertas, " +
            "primero conecta Firebase.", 13f, muted))
    }

    private fun home() {
        page("Hola, $currentUser 👋", if (admin) "Panel de administración" else "Tu comunidad, más segura e informada")
        val hero = card().apply { background = gradient() }
        hero.addView(text(if (admin) "🛡️  Centro de control" else "📍  Reporta lo que sucede", 23f, Color.WHITE, true))
        hero.addView(text(if (admin) "Verifica y confirma los incidentes." else "Comparte el lugar y ayuda a tu comunidad.", 15f, Color.WHITE))
        val active = items.count { it.status != "Finalizado" }
        val waiting = items.count { it.status == "Por verificar" }
        val stats = card()
        stats.addView(text("${items.size} reportes    •    $active activos    •    $waiting por verificar", 16f, ink, true))
        section("Accesos rápidos")
        addButton("＋  Reportar accidente o problema") { create() }
        addButton("📍  Ver reportes y ubicaciones", violet) { listing() }
        if (admin) addButton("🛡️  Revisar solicitudes de cierre", Color.rgb(20, 133, 122)) { listing("Por verificar") }
        addButton("📊  Estadísticas", Color.rgb(28, 123, 169)) { stats() }
        addButton("↪  Cambiar de usuario", muted) { login() }
        body.addView(text("Modo local de demostración. Para sincronizar y recibir alertas necesitas configurar Firebase.", 12f, muted))
    }

    private fun create() {
        photoPath = ""; lat = null; lon = null
        page("Nuevo reporte", "Agrega los detalles y, si deseas, tu ubicación y una fotografía")
        body.addView(text("Tipo de incidente", 15f, ink, true))
        val types = listOf("Accidente de tránsito", "Calle dañada", "Fuga de agua", "Incendio", "Seguridad", "Otro problema")
        val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types) }
        body.addView(category); space()
        val description = field("¿Qué sucedió?", 3)
        body.addView(description); space()
        val address = field("Dirección o referencia (opcional con GPS)")
        body.addView(address); space()
        locationInfo = text("📍 Sin ubicación GPS seleccionada", 14f, muted)
        body.addView(locationInfo)
        addButton("📍 Usar mi ubicación", violet) { requestLocation() }
        photoInfo = text("📷 Sin fotografía", 14f, muted)
        body.addView(photoInfo)
        addButton("📷 Tomar fotografía", Color.rgb(20, 133, 122)) {
            try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), cameraRequest) }
            catch (_: Exception) { toast("No hay una cámara disponible") }
        }
        addButton("✓ Publicar reporte") {
            if (description.text.isBlank()) { toast("Escribe una descripción"); return@addButton }
            if (address.text.isBlank() && (lat == null || lon == null)) {
                toast("Agrega una dirección o activa la ubicación"); return@addButton
            }
            val item = Incident(
                System.currentTimeMillis(), category.selectedItem.toString(),
                description.text.toString().trim(), address.text.toString().trim(),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                currentUser, lat, lon, photoPath
            )
            items.add(item); save(); localNotification(item)
            toast("Reporte guardado en este dispositivo (demo)"); home()
        }
        addButton("← Volver", muted) { home() }
    }

    private fun listing(filter: String = "Todos") {
        page("Reportes", if (admin) "Revisa y confirma el estado de cada incidente" else "Consulta incidentes y avisa si alguno terminó")
        val choices = listOf("Todos", "Activo", "Por verificar", "Finalizado")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
        }
        body.addView(spinner); space()
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        fun render(chosen: String) {
            results.removeAllViews()
            val shown = items.filter { chosen == "Todos" || it.status == chosen }.asReversed()
            if (shown.isEmpty()) results.addView(text("Todavía no hay reportes en esta categoría.", 15f, muted))
            shown.forEach { item ->
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = shape(Color.WHITE)
                    elevation = dp(2).toFloat()
                    setPadding(dp(15), dp(13), dp(15), dp(13))
                }
                val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                results.addView(panel, params)
                val statusColor = when (item.status) {
                    "Finalizado" -> Color.rgb(20, 133, 122)
                    "Por verificar" -> Color.rgb(193, 117, 18)
                    else -> Color.rgb(208, 60, 68)
                }
                panel.addView(text("🚨 ${item.category}", 18f, ink, true))
                panel.addView(text("● ${item.status}", 14f, statusColor, true))
                panel.addView(text(item.description))
                panel.addView(text("📍 ${item.address.ifBlank { "Ubicación GPS" }}", 14f, muted))
                panel.addView(text("Por ${item.author}  ·  ${item.created}", 12f, muted))
                if (item.photo.isNotEmpty()) {
                    val image = ImageView(this)
                    val file = File(item.photo)
                    if (file.exists()) {
                        image.setImageURI(Uri.fromFile(file))
                        image.adjustViewBounds = true
                        image.maxHeight = dp(180)
                        panel.addView(image)
                    }
                }
                if (item.lat != null && item.lon != null) {
                    panel.addView(button("🗺️ Abrir ubicación en Google Maps", primary) { openMap(item) })
                }
                if (item.status == "Activo" && !admin) {
                    panel.addView(button("Avisar: creo que ya terminó", violet) {
                        AlertDialog.Builder(this).setTitle("Solicitar verificación")
                            .setMessage("El reporte seguirá visible hasta que un administrador confirme que terminó.")
                            .setPositiveButton("Enviar aviso") { _, _ ->
                                item.votes += 1; item.status = "Por verificar"; save(); listing(chosen)
                            }.setNegativeButton("Cancelar", null).show()
                    })
                }
                if (admin && item.status != "Finalizado") {
                    panel.addView(button("✓ Confirmar finalización", Color.rgb(20, 133, 122)) {
                        AlertDialog.Builder(this).setTitle("Confirmar cierre")
                            .setMessage("Solo confirma después de verificar que el incidente terminó.")
                            .setPositiveButton("Confirmar") { _, _ -> item.status = "Finalizado"; save(); listing(chosen) }
                            .setNegativeButton("Cancelar", null).show()
                    })
                    if (item.status == "Por verificar") {
                        panel.addView(button("↩ Mantener accidente activo", Color.rgb(193, 117, 18)) {
                            item.status = "Activo"; save(); listing(chosen)
                        })
                    }
                }
            }
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { render(choices[position]) }
        }
        spinner.setSelection(choices.indexOf(filter).coerceAtLeast(0))
        addButton("← Volver al inicio", muted) { home() }
    }

    private fun stats() {
        page("Estadísticas", "Resumen de reportes en este dispositivo")
        val counts = listOf("Activo", "Por verificar", "Finalizado")
        counts.forEach { status ->
            val panel = card()
            panel.addView(text("${items.count { it.status == status }}", 30f, primary, true))
            panel.addView(text(status, 15f, muted))
        }
        section("Por categoría")
        items.groupingBy { it.category }.eachCount().forEach { (type, count) -> body.addView(text("$type: $count")) }
        addButton("← Volver", muted) { home() }
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest)
            return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val location = listOfNotNull(gps, network).maxByOrNull { it.time }
            if (location != null) {
                lat = location.latitude; lon = location.longitude
                locationInfo?.text = "📍 Ubicación: %.5f, %.5f".format(Locale.US, lat, lon)
            } else toast("Activa la ubicación del emulador o teléfono y vuelve a intentarlo")
        } catch (_: SecurityException) { toast("Debes permitir el acceso a la ubicación") }
        catch (_: Exception) { toast("No se pudo obtener la ubicación") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocation()
    }

    @Deprecated("Camera thumbnail demonstration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == cameraRequest && resultCode == RESULT_OK) {
            val bitmap = if (Build.VERSION.SDK_INT >= 33) {
                @Suppress("DEPRECATION")
                data?.extras?.get("data") as? Bitmap
            } else {
                @Suppress("DEPRECATION")
                data?.extras?.get("data") as? Bitmap
            }
            if (bitmap != null) {
                try {
                    val file = File(filesDir, "foto_${System.currentTimeMillis()}.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    photoPath = file.absolutePath
                    photoInfo?.text = "📷 Fotografía agregada correctamente"
                } catch (_: Exception) { toast("No se pudo guardar la fotografía") }
            } else toast("La cámara no devolvió una imagen")
        }
    }

    private fun openMap(item: Incident) {
        val uri = Uri.parse("geo:${item.lat},${item.lon}?q=${item.lat},${item.lon}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try { startActivity(intent) }
        catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${item.lat},${item.lon}")))
            } catch (_: Exception) { toast("Instala Google Maps o un navegador") }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("helpme_demo", "Alertas locales HelpMe", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
    private fun localNotification(item: Incident) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, "helpme_demo")
            else android.app.Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("HelpMe · Nuevo reporte")
            .setContentText("${item.category}: ${item.address.ifBlank { "ubicación compartida" }}")
            .setAutoCancel(true).setContentIntent(open)
        manager.notify((item.id % Int.MAX_VALUE).toInt(), builder.build())
    }
    private fun save() {
        val arr = JSONArray()
        items.forEach { i -> arr.put(JSONObject().apply {
            put("id", i.id); put("category", i.category); put("description", i.description)
            put("address", i.address); put("created", i.created); put("author", i.author)
            put("lat", i.lat ?: JSONObject.NULL); put("lon", i.lon ?: JSONObject.NULL)
            put("photo", i.photo); put("status", i.status); put("votes", i.votes)
        }) }
        getSharedPreferences("helpme2", MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
    }
    private fun load() {
        try {
            val arr = JSONArray(getSharedPreferences("helpme2", MODE_PRIVATE).getString("items", "[]"))
            for (n in 0 until arr.length()) {
                val j = arr.getJSONObject(n)
                items.add(Incident(j.getLong("id"), j.getString("category"), j.getString("description"),
                    j.getString("address"), j.getString("created"), j.getString("author"),
                    if (j.isNull("lat")) null else j.getDouble("lat"),
                    if (j.isNull("lon")) null else j.getDouble("lon"),
                    j.optString("photo"), j.optString("status", "Activo"), j.optInt("votes")))
            }
        } catch (_: Exception) { }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    @Deprecated("Back navigation for simple demo")
    override fun onBackPressed() { home() }
}
