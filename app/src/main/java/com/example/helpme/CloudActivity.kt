package com.example.helpme

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/** Datos provenientes de Firestore. No confiamos en un PIN ni en roles locales. */
private data class CloudIncident(
    val id: String,
    val category: String,
    val description: String,
    val address: String,
    val authorUid: String,
    val timestamp: Timestamp?,
    val latitude: Double?,
    val longitude: Double?,
    val photoPath: String,
    val photoBlob: com.google.firebase.firestore.Blob?,
    val status: String
)

class CloudActivity : Activity() {
    private val ink = Color.rgb(22, 39, 70)
    private val blue = Color.rgb(39, 95, 222)
    private val violet = Color.rgb(113, 74, 216)
    private val teal = Color.rgb(18, 135, 121)
    private val grey = Color.rgb(100, 115, 138)
    private val backgroundColor = Color.rgb(244, 247, 253)

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var body: LinearLayout
    private var listener: ListenerRegistration? = null
    private var isAdministrator = false
    // La elección de pantalla NO concede permisos; el servidor verifica admin=true.
    private var wantsAdminAccess = false
    private var currentPage = "login"
    private var currentFilter = "Todos"
    private val incidents = mutableListOf<CloudIncident>()

    private var photoBytes: ByteArray? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var locationLabel: TextView? = null
    private var photoLabel: TextView? = null
    private val cameraRequest = 701
    private val locationRequest = 702
    private val notificationRequest = 703

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        HelpMeMessagingService.createChannel(this)
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                android.util.Log.d("HelpMeFCM", "TOKEN: $token")
            }
            .addOnFailureListener { error ->
                android.util.Log.e("HelpMeFCM", "Error al obtener token", error)
            }
        wantsAdminAccess = getSharedPreferences("helpme_access", MODE_PRIVATE)
            .getBoolean("wants_admin", false)
        if (auth.currentUser == null) showLogin() else loadRole()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun bgColor(value: Int, radius: Int = 15): GradientDrawable =
        GradientDrawable().apply { setColor(value); cornerRadius = dp(radius).toFloat() }

    private fun gradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(blue, violet)
    ).apply { cornerRadius = dp(22).toFloat() }

    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(3), dp(6), dp(3), dp(6))
        }

    private fun button(title: String, color: Int = blue, action: () -> Unit): TextView =
        text(title, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = bgColor(color, 13)
            setPadding(dp(13), dp(15), dp(13), dp(15))
            setOnClickListener { action() }
        }

    private fun space(height: Int = 12) {
        body.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun page(title: String, subtitle: String = "") {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(22), dp(19), dp(35))
        }
        scroll.addView(body)
        setContentView(scroll)
        body.addView(text("✦ HelpMe · GUATEMALA", 15f, blue, true))
        space(10)
        body.addView(text(title, 27f, ink, true))
        if (subtitle.isNotEmpty()) body.addView(text(subtitle, 14f, grey))
        space(14)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bgColor(Color.WHITE, 18)
        elevation = dp(3).toFloat()
        setPadding(dp(16), dp(14), dp(16), dp(14))
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
    }

    private fun field(hint: String, multiLine: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(ink)
        setSingleLine(!multiLine)
        if (multiLine) { minLines = 3; gravity = Gravity.TOP }
        background = bgColor(Color.rgb(237, 242, 251), 11)
        setPadding(dp(13), dp(14), dp(13), dp(14))
    }

    private fun addButton(title: String, color: Int = blue, action: () -> Unit) {
        body.addView(button(title, color, action)); space(10)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun showLogin() {
        currentPage = "login"
        page("Bienvenido a HelpMe", "Elige el tipo de acceso a la comunidad")
        val banner = card().apply { background = gradient() }
        banner.addView(text("🚦 Una comunidad más informada", 22f, Color.WHITE, true))
        banner.addView(text("Reporta accidentes, comparte ubicaciones y consulta su estado.", 14f, Color.WHITE))
        space(5)
        addButton("👤 Entrar como ciudadano") { showCredentials(false) }
        addButton("🛡️ Entrar como administrador", teal) { showCredentials(true) }
        body.addView(text("Solo las cuentas autorizadas por el responsable de Firebase pueden administrar reportes. " +
            "La aplicación no muestra ni contiene un PIN de administrador.", 13f, grey))
    }

    private fun showCredentials(administrator: Boolean) {
        currentPage = "login"
        wantsAdminAccess = administrator
        getSharedPreferences("helpme_access", MODE_PRIVATE).edit()
            .putBoolean("wants_admin", administrator).apply()

        page(if (administrator) "Acceso administrativo" else "Acceso ciudadano",
            if (administrator) "Exclusivo para personal autorizado" else "Inicia sesión o crea una cuenta ciudadana")
        if (administrator) {
            val banner = card().apply { background = gradient() }
            banner.addView(text("🛡️ Panel de administración", 21f, Color.WHITE, true))
            banner.addView(text("Para confirmar el cierre de un accidente, entra con tu cuenta autorizada.", 14f, Color.WHITE))
        }
        val email = field("Correo electrónico")
        email.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        body.addView(email); space()
        val password = field("Contraseña privada")
        password.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        body.addView(password); space()
        addButton(if (administrator) "Ingresar como administrador" else "Ingresar como ciudadano",
            if (administrator) teal else blue) {
            val e = email.text.toString().trim()
            val p = password.text.toString()
            if (e.isBlank() || p.isBlank()) { toast("Ingresa correo y contraseña"); return@addButton }
            auth.signInWithEmailAndPassword(e, p)
                .addOnSuccessListener { loadRole() }
                .addOnFailureListener { toast("No se pudo ingresar. Comprueba tus credenciales y conexión.") }
        }
        if (!administrator) {
            addButton("Crear cuenta de ciudadano", violet) {
                val e = email.text.toString().trim()
                val p = password.text.toString()
                if (e.isBlank() || p.length < 6) {
                    toast("Escribe un correo y contraseña de al menos 6 caracteres")
                    return@addButton
                }
                auth.createUserWithEmailAndPassword(e, p)
                    .addOnSuccessListener { loadRole() }
                    .addOnFailureListener { toast("No se pudo crear la cuenta. Comprueba el correo y la conexión.") }
            }
        }
        addButton("← Elegir otro tipo de acceso", grey) { showLogin() }
        if (administrator) {
            body.addView(text("La contraseña no está escrita dentro de la aplicación. " +
                "Una cuenta solo tiene autoridad administrativa si Firebase le asignó ese permiso.", 13f, grey))
        }
    }

    private fun loadRole() {
        val user = auth.currentUser ?: run { showLogin(); return }
        page("Verificando permisos", "Validando tu cuenta de forma segura")
        // El acceso privilegiado depende EXCLUSIVAMENTE del token verificado por Firebase.
        user.getIdToken(true).addOnSuccessListener { token ->
            val authorized = token.claims["admin"] == true
            if (wantsAdminAccess && !authorized) {
                auth.signOut()
                isAdministrator = false
                toast("Esta cuenta no está autorizada como administradora")
                showCredentials(true)
            } else {
                isAdministrator = wantsAdminAccess && authorized
                observeIncidents()
                home()
            }
        }.addOnFailureListener {
            isAdministrator = false
            if (wantsAdminAccess) {
                auth.signOut()
                toast("No se pudieron comprobar los permisos. Comprueba la conexión")
                showCredentials(true)
            } else {
                toast("No se pudieron verificar permisos; comprueba tu conexión")
                observeIncidents()
                home()
            }
        }
    }

    private fun observeIncidents() {
        listener?.remove()
        listener = database.collection("incidents")

            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    toast("Error al sincronizar reportes: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                incidents.clear()
                snapshot?.documents?.forEach { doc -> incidents.add(readIncident(doc)) }
                when (currentPage) {
                    "home" -> home()
                    "list" -> listing(currentFilter)
                    "stats" -> stats()
                }
            }
    }

    private fun readIncident(doc: DocumentSnapshot): CloudIncident = CloudIncident(
        id = doc.id,
        category = doc.getString("category") ?: "Incidente",
        description = doc.getString("description") ?: "",
        address = doc.getString("address") ?: "",
        authorUid = doc.getString("authorUid") ?: "",
        timestamp = doc.getTimestamp("createdAt"),
        latitude = doc.getDouble("lat"),
        longitude = doc.getDouble("lon"),
        photoPath = doc.getString("photoPath") ?: "",
        photoBlob = doc.getBlob("photoBlob"),
        status = doc.getString("status") ?: "Activo"
    )

    private fun home() {
        currentPage = "home"
        val alias = auth.currentUser?.email?.substringBefore('@') ?: "usuario"
        page("¡Hola, $alias! 👋", if (isAdministrator) "Panel de administración verificado" else "Tu comunidad, más informada")
        val hero = card().apply { background = gradient() }
        hero.addView(text(if (isAdministrator) "🛡️ Centro de control" else "📍 Reportes en tiempo real", 22f, Color.WHITE, true))
        hero.addView(text(if (isAdministrator) "Solo tu cuenta puede confirmar los cierres." else "Comparte incidentes y consulta su estado.", 15f, Color.WHITE))
        val total = incidents.size
        val active = incidents.count { it.status != "Finalizado" }
        val pending = incidents.count { it.status == "Por verificar" }
        card().addView(text("$total reportes    •    $active activos    •    $pending por verificar", 16f, ink, true))
        addButton("＋ Reportar accidente o problema") { create() }
        addButton("📍 Ver reportes compartidos", violet) { listing() }
        if (isAdministrator) addButton("🛡️ Revisar avisos de finalización", teal) { listing("Por verificar") }
        addButton("📊 Estadísticas", Color.rgb(28, 123, 169)) { stats() }
        val optedIn = getPreferences(MODE_PRIVATE).getBoolean("alertas", false)
        addButton(if (optedIn) "🔕 Desactivar avisos de Guatemala" else "🔔 Activar alertas de Guatemala", if (optedIn) grey else teal) {
            setNotifications(!optedIn)
        }
        addButton("↪ Cerrar sesión", grey) {
            listener?.remove(); listener = null
            FirebaseMessaging.getInstance().unsubscribeFromTopic("reportes_guatemala")
            getPreferences(MODE_PRIVATE).edit().putBoolean("alertas", false).apply()
            auth.signOut(); isAdministrator = false; incidents.clear()
            wantsAdminAccess = false
            getSharedPreferences("helpme_access", MODE_PRIVATE).edit()
                .putBoolean("wants_admin", false).apply()
            showLogin()
        }
        body.addView(text("Las alertas nacionales se envían solo si las activas. Son reportes ciudadanos sin verificar, no avisos oficiales de emergencias.", 12f, grey))
    }

    private fun setNotifications(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
                return
            }
            FirebaseMessaging.getInstance().subscribeToTopic("reportes_guatemala")
                .addOnSuccessListener {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("alertas", true).apply()
                    toast("Alertas nacionales activadas"); home()
                }.addOnFailureListener { toast("No se pudieron activar: ${it.localizedMessage}") }
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic("reportes_guatemala")
                .addOnSuccessListener {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("alertas", false).apply()
                    home()
                }.addOnFailureListener { toast("No se pudieron desactivar: ${it.localizedMessage}") }
        }
    }

    private fun create() {
        currentPage = "create"
        photoBytes = null; latitude = null; longitude = null
        page("Nuevo reporte", "La ubicación y foto son opcionales, pero ayudan a verificar el incidente")
        body.addView(text("Tipo de problema", 15f, ink, true))
        val categories = listOf("Accidente de tránsito", "Calle dañada", "Fuga de agua", "Incendio", "Seguridad", "Otro problema")
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@CloudActivity, android.R.layout.simple_spinner_dropdown_item, categories) }
        body.addView(type); space()
        val description = field("Describe qué sucedió", true)
        body.addView(description); space()
        val address = field("Dirección o referencia (opcional con GPS)")
        body.addView(address); space()
        locationLabel = text("📍 Sin ubicación GPS", 14f, grey)
        body.addView(locationLabel)
        addButton("📍 Usar mi ubicación", violet) { requestLocation() }
        photoLabel = text("📷 Sin fotografía (evita fotografiar personas heridas)", 14f, grey)
        body.addView(photoLabel)
        addButton("📷 Tomar fotografía", teal) {
            try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), cameraRequest) }
            catch (_: Exception) { toast("No se encontró una aplicación de cámara") }
        }
        addButton("✓ Publicar reporte para todos") {
            val user = auth.currentUser ?: run { showLogin(); return@addButton }
            val detail = description.text.toString().trim()
            val addressText = address.text.toString().trim()
            if (detail.length < 8) { toast("Escribe una descripción de al menos 8 caracteres"); return@addButton }
            if (addressText.isBlank() && (latitude == null || longitude == null)) {
                toast("Indica una dirección o selecciona tu ubicación"); return@addButton
            }
            val docRef = database.collection("incidents").document()
            val path = if (photoBytes != null) "incidents/${user.uid}/${docRef.id}.jpg" else ""
            val data = hashMapOf<String, Any?>(
                "category" to type.selectedItem.toString(),
                "description" to detail,
                "address" to addressText,
                "authorUid" to user.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "lat" to latitude,
                "lon" to longitude,
                "photoPath" to path,
                "status" to "Activo"
            )

            val upload = photoBytes

            if (upload != null) {
                try {
                    val original = android.graphics.BitmapFactory.decodeByteArray(
                        upload, 0, upload.size
                    )

                    if (original == null) {
                        toast("No se pudo leer la fotografía")
                        return@addButton
                    }

                    val maxSide = 800
                    val ratio = minOf(
                        1f,
                        maxSide.toFloat() / maxOf(original.width, original.height)
                    )

                    val foto = if (ratio < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            original,
                            maxOf(1, (original.width * ratio).toInt()),
                            maxOf(1, (original.height * ratio).toInt()),
                            true
                        )
                    } else {
                        original
                    }

                    val salida = java.io.ByteArrayOutputStream()
                    foto.compress(
                        android.graphics.Bitmap.CompressFormat.JPEG,
                        55,
                        salida
                    )

                    val bytes = salida.toByteArray()

                    if (bytes.size > 650_000) {
                        toast("La fotografía es demasiado grande")
                        return@addButton
                    }

                    data["photoBlob"] =
                        com.google.firebase.firestore.Blob.fromBytes(bytes)
                    data["photoPath"] = ""

                    publish(docRef, data)

                } catch (e: Exception) {
                    toast("Error preparando fotografía: ${e.localizedMessage}")
                }
            } else {
                publish(docRef, data)
            }

        }
        addButton("← Volver", grey) { home() }
    }

    private fun publish(ref: com.google.firebase.firestore.DocumentReference, data: Map<String, Any?>) {
        ref.set(data).addOnSuccessListener {
            toast("Reporte compartido. Se avisará a quienes activaron alertas cuando el servidor esté desplegado.")
            home()
        }.addOnFailureListener { toast("No se pudo publicar: ${it.localizedMessage}") }
    }

    private fun listing(filter: String = "Todos") {
        currentPage = "list"; currentFilter = filter
        page("Reportes compartidos", if (isAdministrator) "Verifica y confirma el estado real" else "Los avisos de finalización no cierran el reporte")
        val options = listOf("Todos", "Activo", "Por verificar", "Finalizado")
        val chooser = Spinner(this).apply {
            adapter = ArrayAdapter(this@CloudActivity, android.R.layout.simple_spinner_dropdown_item, options)
        }
        body.addView(chooser); space()
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        fun render(choice: String) {
            currentFilter = choice
            results.removeAllViews()
            val list = incidents.filter { choice == "Todos" || it.status == choice }
            if (list.isEmpty()) results.addView(text("No hay reportes en esta categoría.", 15f, grey))
            list.forEach { report ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = bgColor(Color.WHITE, 18)
                    elevation = dp(2).toFloat()
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
                results.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                box.addView(text("🚨 ${report.category}", 18f, ink, true))
                val stateColor = when (report.status) {
                    "Finalizado" -> teal
                    "Por verificar" -> Color.rgb(195, 122, 24)
                    else -> Color.rgb(201, 55, 66)
                }
                box.addView(text("● ${report.status} · ${if (report.status == "Finalizado") "confirmado por administrador" else "reporte ciudadano"}", 13f, stateColor, true))
                box.addView(text(report.description, 15f))
                box.addView(text("📍 ${report.address.ifBlank { "Ubicación GPS" }}", 14f, grey))
                val date = report.timestamp?.toDate()?.let {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                } ?: "Enviando..."
                box.addView(text("Fecha: $date", 12f, grey))

                if (report.photoBlob != null) {
                    val bytes = report.photoBlob.toBytes()

                    val bmp = android.graphics.BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size
                    )

                    if (bmp != null) {
                        val image = ImageView(this).apply {
                            adjustViewBounds = true
                            maxHeight = dp(220)
                            setImageBitmap(bmp)
                        }
                        box.addView(image)
                    }
                }
                if (report.latitude != null && report.longitude != null || report.address.isNotBlank()) {
                    box.addView(button("🗺️ Ver lugar en Google Maps") { openMap(report) })
                }
                if (!isAdministrator && report.status != "Finalizado") {
                    box.addView(button("Avisar: creo que terminó", violet) {
                        AlertDialog.Builder(this).setTitle("Solicitar revisión")
                            .setMessage("Esto NO cerrará el incidente. Un administrador verificará la información.")
                            .setPositiveButton("Enviar aviso") { _, _ -> requestClosure(report) }
                            .setNegativeButton("Cancelar", null).show()
                    })
                }
                if (isAdministrator && report.status != "Finalizado") {
                    box.addView(button("✓ Confirmar finalización", teal) {
                        AlertDialog.Builder(this).setTitle("Verificación de administrador")
                            .setMessage("Confirma solo después de comprobar que terminó. Se retirará de la lista de activos, pero conservará el historial.")
                            .setPositiveButton("Confirmar") { _, _ -> changeStatus(report.id, "Finalizado") }
                            .setNegativeButton("Cancelar", null).show()
                    })
                    box.addView(button("↩ Mantener activo", Color.rgb(193, 117, 18)) {
                        changeStatus(report.id, "Activo")
                    })
                }
            }
        }
        chooser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                render(options[position])
            }
        }
        chooser.setSelection(options.indexOf(filter).coerceAtLeast(0))
        addButton("← Volver al inicio", grey) { home() }
    }

    private fun requestClosure(report: CloudIncident) {
        val uid = auth.currentUser?.uid ?: return
        database.collection("incidents").document(report.id)
            .collection("closureRequests").document(uid)
            .set(mapOf("requestedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                toast("Aviso enviado. El accidente permanecerá activo hasta la confirmación de un administrador.")
            }.addOnFailureListener {
                toast("No se pudo enviar (quizás ya avisaste): ${it.localizedMessage}")
            }
    }

    private fun changeStatus(id: String, status: String) {
        // Las reglas Firestore rechazan esta escritura si la cuenta no tiene admin=true.
        database.collection("incidents").document(id).update("status", status)
            .addOnSuccessListener { toast("Estado actualizado: $status") }
            .addOnFailureListener { toast("Acceso rechazado o sin conexión: ${it.localizedMessage}") }
    }

    private fun stats() {
        currentPage = "stats"
        page("Estadísticas", "Datos compartidos entre todos los dispositivos")
        listOf("Activo", "Por verificar", "Finalizado").forEach { status ->
            card().apply {
                addView(text("${incidents.count { it.status == status }}", 30f, blue, true))
                addView(text(status, 15f, grey))
            }
        }
        addButton("← Volver", grey) { home() }
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest)
            return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val found = listOfNotNull(
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ).maxByOrNull { it.time }
            if (found != null) {
                latitude = found.latitude; longitude = found.longitude
                locationLabel?.text = "📍 Ubicación: %.5f, %.5f".format(Locale.US, latitude, longitude)
            } else toast("Activa GPS y vuelve a intentar; también puedes escribir una dirección")
        } catch (_: SecurityException) { toast("Permite acceso a ubicación") }
        catch (_: Exception) { toast("No se pudo obtener ubicación") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocation()
        if (requestCode == notificationRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) setNotifications(true)
    }

    @Deprecated("La cámara devuelve miniatura de demostración")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == cameraRequest && resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap == null) { toast("No se obtuvo la imagen"); return }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            photoBytes = out.toByteArray()
            photoLabel?.text = "📷 Fotografía agregada"
        }
    }

    private fun openMap(incident: CloudIncident) {
        val query = if (incident.latitude != null && incident.longitude != null)
            "${incident.latitude},${incident.longitude}" else incident.address
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { toast("Instala un navegador o Google Maps") }
    }

    @Deprecated("Navegación simple")
    override fun onBackPressed() {
        if (currentPage == "login") super.onBackPressed() else home()
    }
}
