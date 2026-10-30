package com.example.helpme

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.example.helpme.controller.AdminController
import com.example.helpme.controller.AuthController
import com.example.helpme.controller.IncidentController
import com.example.helpme.model.Incident
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class CloudActivity : Activity() {

    // COLORES DEL DISEÑO

    private val ink = Color.rgb(22, 39, 70)
    private val blue = Color.rgb(39, 95, 222)
    private val violet = Color.rgb(113, 74, 216)
    private val teal = Color.rgb(18, 135, 121)
    private val grey = Color.rgb(100, 115, 138)
    private val amber = Color.rgb(193, 117, 18)
    private val red = Color.rgb(195, 55, 68)

    private val backgroundColor = Color.rgb(244, 247, 253)
    private val borderColor = Color.rgb(226, 233, 245)
    private val softBlue = Color.rgb(235, 241, 255)
    private val softTeal = Color.rgb(228, 246, 242)
    private val softAmber = Color.rgb(255, 245, 229)
    private val softRed = Color.rgb(255, 237, 239)

    // CONTROLADORES MVC

    private val authController = AuthController()
    private val incidentController = IncidentController()
    private val adminController = AdminController()

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseFirestore
    private lateinit var body: LinearLayout

    private var listener: ListenerRegistration? = null
    private var isAdministrator = false
    private var wantsAdminAccess = false

    private var currentPage = "login"
    private var currentFilter = "Todos"

    private val incidents = mutableListOf<Incident>()
    private var listResults: LinearLayout? = null

    private var photoBytes: ByteArray? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    private var locationLabel: TextView? = null
    private var photoLabel: TextView? = null

    private val cameraRequest = 701
    private val locationRequest = 702
    private val notificationRequest = 703

    // CICLO DE VIDA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseFirestore.getInstance()

        HelpMeMessagingService.createChannel(this)

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                android.util.Log.d(
                    "HelpMeFCM",
                    "TOKEN: $token"
                )
            }
            .addOnFailureListener { error ->
                android.util.Log.e(
                    "HelpMeFCM",
                    "Error al obtener token",
                    error
                )
            }

        wantsAdminAccess = getSharedPreferences(
            "helpme_access",
            MODE_PRIVATE
        ).getBoolean("wants_admin", false)

        if (authController.isLoggedIn()) {
            loadRole()
        } else {
            showLogin()
        }
    }

    override fun onDestroy() {
        listener?.remove()
        listener = null
        super.onDestroy()
    }

    // COMPONENTES VISUALES GENERALES

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun bgColor(
        color: Int,
        radius: Int = 16
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun borderedBackground(
        color: Int = Color.WHITE,
        radius: Int = 18
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), borderColor)
        }
    }

    private fun gradient(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(blue, violet)
        ).apply {
            cornerRadius = dp(24).toFloat()
        }
    }

    private fun premiumGradient(
        start: Int,
        end: Int,
        radius: Int = 21
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(start, end)
        ).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(
        value: String,
        size: Float = 15f,
        color: Int = ink,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }

            setPadding(
                dp(3),
                dp(5),
                dp(3),
                dp(5)
            )
        }
    }

    private fun button(
        title: String,
        color: Int = blue,
        action: () -> Unit
    ): TextView {
        return text(
            title,
            15f,
            Color.WHITE,
            true
        ).apply {
            gravity = Gravity.CENTER
            background = bgColor(color, 15)
            minHeight = dp(52)
            elevation = dp(2).toFloat()

            setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
            )

            setOnClickListener {
                action()
            }
        }
    }

    private fun space(height: Int = 12) {
        body.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun page(
        title: String,
        subtitle: String = ""
    ) {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
            clipToPadding = false
        }

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(36)
            )
        }

        scroll.addView(body)
        setContentView(scroll)

        val brand = text(
            "✦ HELP ME  /  GUATEMALA",
            13f,
            blue,
            true
        )

        brand.letterSpacing = 0.06f
        body.addView(brand)

        space(12)

        body.addView(
            text(title, 27f, ink, true)
        )

        if (subtitle.isNotEmpty()) {
            body.addView(
                text(subtitle, 14f, grey)
            )
        }

        space(18)
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = borderedBackground()
            elevation = dp(2).toFloat()

            setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
            )

            body.addView(
                this,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(14)
                }
            )
        }
    }

    private fun field(
        hint: String,
        multiLine: Boolean = false
    ): EditText {
        return EditText(this).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(grey)
            setSingleLine(!multiLine)

            if (multiLine) {
                minLines = 3
                gravity = Gravity.TOP
            }

            background = borderedBackground(
                Color.WHITE,
                13
            )

            setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
            )
        }
    }

    private fun addButton(
        title: String,
        color: Int = blue,
        action: () -> Unit
    ) {
        body.addView(button(title, color, action))
        space(11)
    }

    private fun sectionTitle(title: String) {
        space(5)
        body.addView(text(title, 18f, ink, true))
        space(8)
    }

    private fun statisticCard(
        number: Int,
        label: String,
        accent: Int,
        fill: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgColor(fill, 16)

            setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
            )

            addView(
                text(number.toString(), 26f, accent, true)
            )

            addView(
                text(label, 12f, ink, true)
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // =========================================
    // NUEVO DISEÑO DE BIENVENIDA
    // =========================================

    /**
     * Fondo decorativo de la tarjeta principal.
     * Dibujado en Android, sin imágenes externas.
     */
    private inner class WelcomeDecoration : View(this@CloudActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            if (w <= 0f || h <= 0f) return

            // Líneas suaves que recuerdan un mapa.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.argb(55, 255, 255, 255)

            val road = Path().apply {
                moveTo(w * 0.65f, h * 0.15f)
                lineTo(w * 0.90f, h * 0.25f)
                lineTo(w * 0.79f, h * 0.55f)
                lineTo(w * 0.99f, h * 0.74f)
            }

            canvas.drawPath(road, paint)

            val roadTwo = Path().apply {
                moveTo(w * 0.83f, 0f)
                lineTo(w * 0.72f, h * 0.35f)
                lineTo(w, h * 0.48f)
            }

            canvas.drawPath(roadTwo, paint)

            // Anillos de localización.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()

            val cx = w * 0.82f
            val cy = h * 0.42f

            canvas.drawCircle(
                cx,
                cy,
                dp(49).toFloat(),
                paint
            )

            canvas.drawCircle(
                cx,
                cy,
                dp(34).toFloat(),
                paint
            )

            // Pin translúcido.
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(90, 255, 255, 255)

            canvas.drawCircle(
                cx,
                cy - dp(7),
                dp(13).toFloat(),
                paint
            )

            val pin = Path().apply {
                moveTo(cx - dp(11), cy)
                lineTo(cx, cy + dp(23))
                lineTo(cx + dp(11), cy)
                close()
            }

            canvas.drawPath(pin, paint)

            paint.color = Color.argb(160, 39, 95, 222)

            canvas.drawCircle(
                cx,
                cy - dp(7),
                dp(5).toFloat(),
                paint
            )

            // Ondas en la parte inferior.
            val wave = Path().apply {
                moveTo(0f, h * 0.83f)
                cubicTo(
                    w * 0.22f,
                    h * 0.70f,
                    w * 0.37f,
                    h * 1.06f,
                    w * 0.60f,
                    h * 0.91f
                )
                cubicTo(
                    w * 0.78f,
                    h * 0.78f,
                    w * 0.90f,
                    h * 0.93f,
                    w,
                    h * 0.85f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            paint.color = Color.argb(48, 255, 255, 255)
            canvas.drawPath(wave, paint)

            val waveTwo = Path().apply {
                moveTo(0f, h * 0.91f)
                cubicTo(
                    w * 0.28f,
                    h * 0.82f,
                    w * 0.46f,
                    h * 1.05f,
                    w * 0.72f,
                    h * 0.94f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            paint.color = Color.argb(40, 147, 220, 255)
            canvas.drawPath(waveTwo, paint)
        }
    }

    /**
     * Botones grandes de acceso.
     */
    private fun welcomeAccessButton(
        title: String,
        symbol: String,
        startColor: Int,
        endColor: Int,
        action: () -> Unit
    ): LinearLayout {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = premiumGradient(
                startColor,
                endColor,
                19
            )

            elevation = dp(5).toFloat()
            minimumHeight = dp(76)

            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }

        val iconCircle = TextView(this).apply {
            text = symbol
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = bgColor(
                Color.argb(55, 0, 35, 100),
                100
            )
        }

        row.addView(
            iconCircle,
            LinearLayout.LayoutParams(
                dp(54),
                dp(54)
            )
        )

        val titleView = text(
            title,
            15f,
            Color.WHITE,
            true
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(11),
                0,
                dp(2),
                0
            )
        }

        row.addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                -1,
                1f
            )
        )

        row.addView(
            text(
                "❯",
                23f,
                Color.WHITE,
                true
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        return row
    }

    private fun addWelcomeButton(
        view: View
    ) {
        body.addView(
            view,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                bottomMargin = dp(15)
            }
        )
    }

    /**
     * Pantalla inspirada en el diseño
     * seleccionado para HelpMe Guatemala.
     */
    private fun showLogin() {
        currentPage = "login"

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(23),
                dp(18),
                dp(32)
            )
        }

        scroll.addView(body)
        setContentView(scroll)

        // MARCA SUPERIOR

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandIcon = text(
            "📍",
            21f,
            blue
        )

        brandRow.addView(brandIcon)

        brandRow.addView(
            text(
                "HELP ME  /  GUATEMALA",
                14f,
                blue,
                true
            ).apply {
                letterSpacing = 0.05f
            }
        )

        body.addView(brandRow)

        space(23)

        // BIENVENIDA

        body.addView(
            text(
                "Bienvenido a HelpMe",
                27f,
                ink,
                true
            )
        )

        body.addView(
            text(
                "Tu comunidad conectada, informada y más segura.",
                14f,
                grey
            )
        )

        space(23)

        // TARJETA PRINCIPAL

        val hero = FrameLayout(this).apply {
            background = premiumGradient(
                Color.rgb(0, 165, 225),
                Color.rgb(115, 51, 239),
                23
            )

            elevation = dp(5).toFloat()
            clipToOutline = true
        }

        // Altura limitada: evita que la decoración
        // estire la tarjeta dentro del ScrollView.
        hero.addView(
            WelcomeDecoration(),
            FrameLayout.LayoutParams(
                -1,
                dp(218)
            )
        )

        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(25)
            )
        }

        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val peopleIcon = TextView(this).apply {
            text = "👥"
            textSize = 35f
            gravity = Gravity.CENTER
        }

        heroTop.addView(
            peopleIcon,
            LinearLayout.LayoutParams(
                dp(66),
                dp(67)
            )
        )

        heroTop.addView(
            text(
                "Juntos hacemos\nla diferencia",
                22f,
                Color.WHITE,
                true
            ),
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        heroContent.addView(heroTop)

        heroContent.addView(
            text(
                "Comparte incidentes, consulta el mapa y recibe alertas ciudadanas de Guatemala.",
                14f,
                Color.WHITE
            ).apply {
                setPadding(
                    dp(5),
                    dp(13),
                    dp(5),
                    dp(11)
                )
            }
        )

        hero.addView(
            heroContent,
            FrameLayout.LayoutParams(
                -1,
                -2
            )
        )

        body.addView(
            hero,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        space(24)

        // OPCIONES DE ACCESO

        body.addView(
            text(
                "Selecciona tu acceso",
                20f,
                ink,
                true
            )
        )

        space(11)

        addWelcomeButton(
            welcomeAccessButton(
                "Entrar como ciudadano",
                "●",
                Color.rgb(32, 119, 250),
                Color.rgb(20, 83, 220)
            ) {
                showCredentials(false)
            }
        )

        addWelcomeButton(
            welcomeAccessButton(
                "Entrar como administrador",
                "◆",
                Color.rgb(16, 177, 157),
                Color.rgb(7, 139, 121)
            ) {
                showCredentials(true)
            }
        )

        space(7)

        // TARJETA DE SEGURIDAD

        val securityCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            background = borderedBackground(
                Color.rgb(235, 243, 255),
                21
            )

            elevation = dp(2).toFloat()

            setPadding(
                dp(16),
                dp(17),
                dp(16),
                dp(17)
            )
        }

        val lockCircle = TextView(this).apply {
            text = "🔒"
            textSize = 27f
            gravity = Gravity.CENTER

            background = bgColor(
                Color.rgb(217, 234, 255),
                100
            )
        }

        securityCard.addView(
            lockCircle,
            LinearLayout.LayoutParams(
                dp(67),
                dp(67)
            )
        )

        val securityText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(13),
                0,
                0,
                0
            )
        }

        securityText.addView(
            text(
                "Acceso protegido",
                16f,
                ink,
                true
            )
        )

        securityText.addView(
            text(
                "Solo las cuentas autorizadas por Firebase pueden administrar reportes. No existe un PIN fijo de administrador.",
                12f,
                grey
            )
        )

        securityCard.addView(
            securityText,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        body.addView(
            securityCard,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )
    }

    // =========================================
    // INICIO DE SESIÓN Y REGISTRO
    // =========================================

    private fun showCredentials(administrator: Boolean) {
        currentPage = "login"
        wantsAdminAccess = administrator

        getSharedPreferences(
            "helpme_access",
            MODE_PRIVATE
        ).edit()
            .putBoolean("wants_admin", administrator)
            .apply()

        page(
            if (administrator) {
                "Acceso administrativo"
            } else {
                "Acceso ciudadano"
            },
            if (administrator) {
                "Exclusivo para personal autorizado"
            } else {
                "Ingresa con tu cuenta o regístrate"
            }
        )

        if (administrator) {
            val banner = card().apply {
                background = gradient()
            }

            banner.addView(
                text(
                    "🛡️ Panel de administración",
                    21f,
                    Color.WHITE,
                    true
                )
            )

            banner.addView(
                text(
                    "Confirma el cierre de incidentes únicamente después de verificarlos.",
                    14f,
                    Color.WHITE
                )
            )
        }

        sectionTitle("Tus credenciales")

        val email = field("Correo electrónico")

        email.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        body.addView(email)
        space()

        val password = field("Contraseña privada")

        password.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        body.addView(password)
        space(17)

        addButton(
            if (administrator) {
                "Ingresar como administrador"
            } else {
                "Ingresar como ciudadano"
            },
            if (administrator) teal else blue
        ) {
            val e = email.text.toString().trim()
            val p = password.text.toString()

            if (e.isBlank() || p.isBlank()) {
                toast("Ingresa correo y contraseña")
                return@addButton
            }

            authController.login(
                email = e,
                password = p,
                onSuccess = {
                    loadRole()
                },
                onError = {
                    toast(
                        "No se pudo ingresar. Comprueba tus credenciales y conexión."
                    )
                }
            )
        }

        if (!administrator) {
            addButton(
                "Crear cuenta de ciudadano",
                violet
            ) {
                val e = email.text.toString().trim()
                val p = password.text.toString()

                if (e.isBlank() || p.length < 6) {
                    toast(
                        "Escribe un correo y contraseña de al menos 6 caracteres"
                    )
                    return@addButton
                }

                authController.register(
                    email = e,
                    password = p,
                    confirmPassword = p,
                    onSuccess = {
                        loadRole()
                    },
                    onError = {
                        toast(
                            "No se pudo crear la cuenta. Comprueba el correo y la conexión."
                        )
                    }
                )
            }
        }

        addButton(
            "← Elegir otro tipo de acceso",
            grey
        ) {
            showLogin()
        }

        if (administrator) {
            body.addView(
                text(
                    "El permiso administrativo se comprueba mediante Firebase. La contraseña no está guardada en la aplicación.",
                    13f,
                    grey
                )
            )
        }
    }

    private fun loadRole() {
        val user = authController.getCurrentUser()
            ?: run {
                showLogin()
                return
            }

        page(
            "Verificando permisos",
            "Validando tu cuenta de forma segura"
        )

        user.getIdToken(true)
            .addOnSuccessListener { token ->
                val authorized =
                    token.claims["admin"] == true

                if (wantsAdminAccess && !authorized) {
                    authController.logout()
                    isAdministrator = false

                    toast(
                        "Esta cuenta no está autorizada como administradora"
                    )

                    showCredentials(true)
                } else {
                    isAdministrator =
                        wantsAdminAccess && authorized

                    observeIncidents()
                    home()
                }
            }
            .addOnFailureListener {
                isAdministrator = false

                if (wantsAdminAccess) {
                    authController.logout()

                    toast(
                        "No se pudieron comprobar los permisos. Comprueba la conexión"
                    )

                    showCredentials(true)
                } else {
                    toast(
                        "No se pudieron verificar permisos; comprueba tu conexión"
                    )

                    observeIncidents()
                    home()
                }
            }
    }

    // =========================================
    // SINCRONIZACIÓN FIRESTORE
    // =========================================

    private fun observeIncidents() {
        listener?.remove()
        listener = null

        listener = incidentController.observeAllIncidents(
            onSuccess = { reports ->
                incidents.clear()
                incidents.addAll(reports)

                when (currentPage) {
                    "home" -> home()
                    "list" -> refreshListing()
                    "stats" -> stats()
                }
            },
            onError = { message ->
                toast(
                    "Error al sincronizar reportes: $message"
                )
            }
        )
    }

    private fun refreshListing() {
        if (currentPage != "list") return

        val container = listResults ?: return

        renderIncidents(
            container,
            currentFilter
        )
    }

    // =========================================
    // PANTALLA PRINCIPAL
    // =========================================

    private fun home() {
        currentPage = "home"
        listResults = null

        val alias = auth.currentUser
            ?.email
            ?.substringBefore('@')
            ?: "usuario"

        page(
            "¡Hola, $alias! 👋",
            if (isAdministrator) {
                "Panel de administración verificado"
            } else {
                "Bienvenido a tu comunidad"
            }
        )

        val hero = card().apply {
            background = gradient()

            setPadding(
                dp(21),
                dp(23),
                dp(21),
                dp(23)
            )
        }

        hero.addView(
            text(
                if (isAdministrator) {
                    "🛡️ Centro de control"
                } else {
                    "📍 Guatemala conectada"
                },
                23f,
                Color.WHITE,
                true
            )
        )

        hero.addView(
            text(
                if (isAdministrator) {
                    "Gestiona y verifica incidentes con información en tiempo real."
                } else {
                    "Reporta lo que sucede y ayuda a mantener informada a tu comunidad."
                },
                15f,
                Color.WHITE
            )
        )

        val total = incidents.size

        val active = incidents.count {
            it.status != "Finalizado"
        }

        val pending = incidents.count {
            it.status == "Por verificar"
        }

        sectionTitle("Resumen de la comunidad")

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        fun addStatistic(
            number: Int,
            label: String,
            color: Int,
            fill: Int
        ) {
            val item = statisticCard(
                number,
                label,
                color,
                fill
            )

            statsRow.addView(
                item,
                LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                ).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                }
            )
        }

        addStatistic(
            total,
            "Reportes",
            blue,
            softBlue
        )

        addStatistic(
            active,
            "En curso",
            teal,
            softTeal
        )

        addStatistic(
            pending,
            "Por verificar",
            amber,
            softAmber
        )

        body.addView(statsRow)
        space(21)

        sectionTitle("Accesos rápidos")

        addButton(
            "＋ Crear nuevo reporte",
            blue
        ) {
            create()
        }

        addButton(
            "📋 Ver reportes compartidos",
            violet
        ) {
            listing()
        }

        addButton(
            "🗺️ Explorar mapa de incidentes",
            teal
        ) {
            startActivity(
                Intent(
                    this,
                    IncidentMapActivity::class.java
                )
            )
        }

        if (isAdministrator) {
            sectionTitle("Administración")

            addButton(
                "🛡️ Revisar avisos de finalización",
                teal
            ) {
                listing("Por verificar")
            }
        }

        addButton(
            "📊 Consultar estadísticas",
            Color.rgb(28, 123, 169)
        ) {
            stats()
        }

        sectionTitle("Alertas y cuenta")

        val optedIn = getPreferences(MODE_PRIVATE)
            .getBoolean("alertas", false)

        addButton(
            if (optedIn) {
                "🔕 Desactivar avisos de Guatemala"
            } else {
                "🔔 Activar alertas de Guatemala"
            },
            if (optedIn) grey else teal
        ) {
            setNotifications(!optedIn)
        }

        addButton(
            "↪ Cerrar sesión",
            grey
        ) {
            listener?.remove()
            listener = null

            FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(
                    "reportes_guatemala"
                )

            getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean("alertas", false)
                .apply()

            authController.logout()

            isAdministrator = false
            wantsAdminAccess = false
            incidents.clear()
            listResults = null

            getSharedPreferences(
                "helpme_access",
                MODE_PRIVATE
            ).edit()
                .putBoolean("wants_admin", false)
                .apply()

            showLogin()
        }

        val notice = card().apply {
            background = bgColor(
                Color.rgb(236, 241, 250),
                16
            )
            elevation = 0f
        }

        notice.addView(
            text(
                "ℹ️ Información importante",
                14f,
                ink,
                true
            )
        )

        notice.addView(
            text(
                "Las alertas nacionales se envían solo si las activas. Los reportes son ciudadanos y no sustituyen los avisos oficiales de emergencia.",
                12f,
                grey
            )
        )
    }

    // =========================================
    // NOTIFICACIONES
    // =========================================

    private fun setNotifications(enabled: Boolean) {
        if (enabled) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    notificationRequest
                )
                return
            }

            FirebaseMessaging.getInstance()
                .subscribeToTopic(
                    "reportes_guatemala"
                )
                .addOnSuccessListener {
                    getPreferences(MODE_PRIVATE)
                        .edit()
                        .putBoolean("alertas", true)
                        .apply()

                    toast("Alertas nacionales activadas")
                    home()
                }
                .addOnFailureListener {
                    toast(
                        "No se pudieron activar: ${it.localizedMessage}"
                    )
                }
        } else {
            FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(
                    "reportes_guatemala"
                )
                .addOnSuccessListener {
                    getPreferences(MODE_PRIVATE)
                        .edit()
                        .putBoolean("alertas", false)
                        .apply()

                    home()
                }
                .addOnFailureListener {
                    toast(
                        "No se pudieron desactivar: ${it.localizedMessage}"
                    )
                }
        }
    }

    // =========================================
    // CREACIÓN DE REPORTES
    // =========================================

    private fun create() {
        currentPage = "create"
        listResults = null

        photoBytes = null
        latitude = null
        longitude = null

        page(
            "Nuevo reporte",
            "Comparte información útil para tu comunidad"
        )

        val introduction = card().apply {
            background = bgColor(softBlue, 17)
            elevation = 0f
        }

        introduction.addView(
            text(
                "📝 Cuéntanos qué sucedió",
                17f,
                ink,
                true
            )
        )

        introduction.addView(
            text(
                "La ubicación y la fotografía son opcionales si proporcionas una dirección. Ayudan a verificar el incidente.",
                13f,
                grey
            )
        )

        sectionTitle("Detalles del incidente")

        body.addView(
            text(
                "Tipo de problema",
                15f,
                ink,
                true
            )
        )

        val categories = listOf(
            "Accidente de tránsito",
            "Calle dañada",
            "Fuga de agua",
            "Incendio",
            "Seguridad",
            "Otro problema"
        )

        val type = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CloudActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categories
            )

            background = borderedBackground()
            minimumHeight = dp(50)

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )
        }

        body.addView(type)
        space()

        val description = field(
            "Describe qué sucedió",
            true
        )

        body.addView(description)
        space()

        val address = field(
            "Dirección o referencia (opcional con GPS)"
        )

        body.addView(address)
        space(14)

        sectionTitle("Ubicación y evidencia")

        locationLabel = text(
            "📍 Sin ubicación GPS",
            14f,
            grey
        )

        body.addView(locationLabel)

        addButton(
            "📍 Usar mi ubicación",
            violet
        ) {
            requestLocation()
        }

        photoLabel = text(
            "📷 Sin fotografía (evita fotografiar personas heridas)",
            14f,
            grey
        )

        body.addView(photoLabel)

        addButton(
            "📷 Tomar fotografía",
            teal
        ) {
            try {
                startActivityForResult(
                    Intent(
                        MediaStore.ACTION_IMAGE_CAPTURE
                    ),
                    cameraRequest
                )
            } catch (_: Exception) {
                toast(
                    "No se encontró una aplicación de cámara"
                )
            }
        }

        space(8)
        sectionTitle("Publicar")

        addButton(
            "✓ Publicar reporte para todos",
            blue
        ) {
            val user = auth.currentUser
                ?: run {
                    showLogin()
                    return@addButton
                }

            val detail = description.text
                .toString()
                .trim()

            val addressText = address.text
                .toString()
                .trim()

            val category = type.selectedItem.toString()

            val validationError =
                incidentController.validateIncident(
                    category = category,
                    description = detail,
                    latitude = latitude,
                    longitude = longitude,
                    address = addressText
                )

            if (validationError != null) {
                toast(validationError)
                return@addButton
            }

            val incidentId = database
                .collection("incidents")
                .document()
                .id

            val path = if (photoBytes != null) {
                "incidents/${user.uid}/$incidentId.jpg"
            } else {
                ""
            }

            val data = hashMapOf<String, Any?>(
                "category" to category,
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
                    val original =
                        BitmapFactory.decodeByteArray(
                            upload,
                            0,
                            upload.size
                        )

                    if (original == null) {
                        toast("No se pudo leer la fotografía")
                        return@addButton
                    }

                    val maxSide = 800

                    val ratio = minOf(
                        1f,
                        maxSide.toFloat() / maxOf(
                            original.width,
                            original.height
                        )
                    )

                    val foto = if (ratio < 1f) {
                        Bitmap.createScaledBitmap(
                            original,
                            maxOf(
                                1,
                                (original.width * ratio).toInt()
                            ),
                            maxOf(
                                1,
                                (original.height * ratio).toInt()
                            ),
                            true
                        )
                    } else {
                        original
                    }

                    val salida = ByteArrayOutputStream()

                    foto.compress(
                        Bitmap.CompressFormat.JPEG,
                        55,
                        salida
                    )

                    val bytes = salida.toByteArray()

                    if (bytes.size > 650_000) {
                        toast(
                            "La fotografía es demasiado grande"
                        )
                        return@addButton
                    }

                    data["photoBlob"] = Blob.fromBytes(bytes)
                    data["photoPath"] = ""

                    publish(incidentId, data)
                } catch (e: Exception) {
                    toast(
                        "Error preparando fotografía: ${e.localizedMessage}"
                    )
                }
            } else {
                publish(incidentId, data)
            }
        }

        addButton(
            "← Volver al inicio",
            grey
        ) {
            home()
        }
    }

    private fun publish(
        incidentId: String,
        data: Map<String, Any?>
    ) {
        incidentController.createIncident(
            incidentId = incidentId,
            data = data,
            onSuccess = {
                toast(
                    "Reporte compartido. Se avisará a quienes activaron alertas cuando el servidor esté desplegado."
                )
                home()
            },
            onError = { message ->
                toast(
                    "No se pudo publicar: $message"
                )
            }
        )
    }

    // =========================================
    // COMPONENTES DE REPORTES
    // =========================================

    private fun reportCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            background = borderedBackground(
                Color.WHITE,
                21
            )

            elevation = dp(3).toFloat()

            setPadding(
                dp(18),
                dp(17),
                dp(18),
                dp(18)
            )
        }
    }

    private fun statusBadge(status: String): TextView {
        val statusColor = when (status) {
            "Finalizado" -> teal
            "Por verificar" -> amber
            else -> red
        }

        val fillColor = when (status) {
            "Finalizado" -> softTeal
            "Por verificar" -> softAmber
            else -> softRed
        }

        val symbol = when (status) {
            "Finalizado" -> "✓"
            "Por verificar" -> "◷"
            else -> "●"
        }

        return text(
            "$symbol  $status",
            12f,
            statusColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            background = bgColor(fillColor, 100)

            setPadding(
                dp(12),
                dp(6),
                dp(12),
                dp(6)
            )
        }
    }

    private fun categoryBadge(category: String): TextView {
        val symbol = categoryIcon(category)

        return text(
            "$symbol  $category",
            14f,
            ink,
            true
        ).apply {
            background = bgColor(softBlue, 10)

            setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
            )
        }
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(
            View(this).apply {
                setBackgroundColor(borderColor)
            },
            LinearLayout.LayoutParams(
                -1,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        )
    }

    private fun subtleButton(
        title: String,
        accent: Int,
        fill: Int,
        action: () -> Unit
    ): TextView {
        return text(
            title,
            14f,
            accent,
            true
        ).apply {
            gravity = Gravity.CENTER
            minHeight = dp(46)
            background = bgColor(fill, 13)

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            setOnClickListener {
                action()
            }
        }
    }

    private fun addReportAction(
        container: LinearLayout,
        view: View
    ) {
        container.addView(
            view,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin = dp(9)
            }
        )
    }

    // =========================================
    // LISTADO MODERNO DE REPORTES
    // =========================================

    private fun listing(filter: String = "Todos") {
        currentPage = "list"
        currentFilter = filter

        page(
            "Reportes compartidos",
            if (isAdministrator) {
                "Revisa y administra los incidentes de la comunidad"
            } else {
                "Información ciudadana actualizada en tiempo real"
            }
        )

        val summary = card().apply {
            background = bgColor(softBlue, 17)
            elevation = 0f
        }

        summary.addView(
            text(
                "📋 Incidentes de la comunidad",
                17f,
                ink,
                true
            )
        )

        summary.addView(
            text(
                "Explora reportes, consulta sus ubicaciones y revisa su estado.",
                13f,
                grey
            )
        )

        sectionTitle("Filtrar por estado")

        val options = listOf(
            "Todos",
            "Activo",
            "Por verificar",
            "Finalizado"
        )

        val chooser = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CloudActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )

            background = borderedBackground()
            minimumHeight = dp(50)

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )
        }

        body.addView(chooser)
        space(17)

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(results)
        listResults = results

        chooser.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    currentFilter = options[position]

                    renderIncidents(
                        results,
                        currentFilter
                    )
                }
            }

        chooser.setSelection(
            options.indexOf(filter).coerceAtLeast(0)
        )

        renderIncidents(
            results,
            currentFilter
        )

        addButton(
            "← Volver al inicio",
            grey
        ) {
            home()
        }
    }

    private fun renderIncidents(
        results: LinearLayout,
        filter: String
    ) {
        results.removeAllViews()

        val filtered = adminController.filterByStatus(
            incidents = incidents,
            status = filter
        )

        if (filtered.isEmpty()) {
            val empty = reportCard().apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }

            empty.addView(text("📭", 32f, ink))

            empty.addView(
                text(
                    "No hay reportes",
                    19f,
                    ink,
                    true
                )
            )

            empty.addView(
                text(
                    "Todavía no hay incidentes para este filtro.",
                    14f,
                    grey
                )
            )

            results.addView(empty)
            return
        }

        results.addView(
            text(
                if (filtered.size == 1) {
                    "1 reporte encontrado"
                } else {
                    "${filtered.size} reportes encontrados"
                },
                13f,
                grey,
                true
            )
        )

        results.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(10)
            )
        )

        filtered.forEach { report ->
            val box = reportCard()

            results.addView(
                box,
                LinearLayout.LayoutParams(
                    -1,
                    -2
                ).apply {
                    bottomMargin = dp(16)
                }
            )

            box.addView(
                categoryBadge(report.category)
            )

            box.addView(
                View(this),
                LinearLayout.LayoutParams(
                    1,
                    dp(10)
                )
            )

            box.addView(
                statusBadge(report.status),
                LinearLayout.LayoutParams(
                    -2,
                    -2
                )
            )

            box.addView(
                View(this),
                LinearLayout.LayoutParams(
                    1,
                    dp(14)
                )
            )

            box.addView(
                text(
                    report.description,
                    16f,
                    ink
                )
            )

            addDivider(box)

            val locationText = when {
                report.address.isNotBlank() ->
                    report.address

                report.lat != null &&
                        report.lon != null ->
                    "Ubicación GPS registrada"

                else ->
                    "Sin ubicación disponible"
            }

            box.addView(
                text(
                    "UBICACIÓN",
                    11f,
                    grey,
                    true
                )
            )

            box.addView(
                text(
                    "📍 $locationText",
                    14f,
                    ink
                )
            )

            val date = report.createdAt
                ?.toDate()
                ?.let {
                    SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    ).format(it)
                } ?: "Enviando..."

            box.addView(
                View(this),
                LinearLayout.LayoutParams(
                    1,
                    dp(9)
                )
            )

            box.addView(
                text(
                    "FECHA DEL REPORTE",
                    11f,
                    grey,
                    true
                )
            )

            box.addView(
                text(
                    "🕒 $date",
                    14f,
                    ink
                )
            )

            val blob = report.photoBlob

            if (blob != null) {
                try {
                    val bytes = blob.toBytes()

                    val bmp = BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    )

                    if (bmp != null) {
                        addDivider(box)

                        box.addView(
                            text(
                                "FOTOGRAFÍA DEL REPORTE",
                                11f,
                                grey,
                                true
                            )
                        )

                        val image = ImageView(this).apply {
                            adjustViewBounds = true
                            maxHeight = dp(260)

                            scaleType =
                                ImageView.ScaleType.FIT_CENTER

                            setImageBitmap(bmp)

                            background = bgColor(
                                Color.rgb(246, 248, 252),
                                13
                            )

                            setPadding(
                                dp(4),
                                dp(4),
                                dp(4),
                                dp(4)
                            )
                        }

                        box.addView(
                            image,
                            LinearLayout.LayoutParams(
                                -1,
                                -2
                            ).apply {
                                topMargin = dp(9)
                            }
                        )
                    }
                } catch (_: Exception) {
                    box.addView(
                        text(
                            "No se pudo mostrar la fotografía",
                            12f,
                            grey
                        )
                    )
                }
            }

            if (report.status == "Finalizado") {
                addDivider(box)

                val finishedNote =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = bgColor(softTeal, 13)

                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    }

                finishedNote.addView(
                    text(
                        "✓ Incidente finalizado",
                        13f,
                        teal,
                        true
                    )
                )

                finishedNote.addView(
                    text(
                        "Cierre confirmado por un administrador.",
                        12f,
                        grey
                    )
                )

                box.addView(finishedNote)
            } else if (report.status == "Por verificar") {
                addDivider(box)

                val pendingNote =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = bgColor(softAmber, 13)

                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    }

                pendingNote.addView(
                    text(
                        "◷ Pendiente de verificación",
                        13f,
                        amber,
                        true
                    )
                )

                pendingNote.addView(
                    text(
                        "El incidente no se considera finalizado hasta su confirmación administrativa.",
                        12f,
                        grey
                    )
                )

                box.addView(pendingNote)
            }

            val hasLocation =
                (report.lat != null && report.lon != null) ||
                        report.address.isNotBlank()

            val canRequestClosure =
                !isAdministrator &&
                        report.status != "Finalizado"

            val canManage =
                isAdministrator &&
                        report.status != "Finalizado"

            if (
                hasLocation ||
                canRequestClosure ||
                canManage
            ) {
                addDivider(box)

                box.addView(
                    text(
                        "ACCIONES",
                        11f,
                        grey,
                        true
                    )
                )
            }

            if (hasLocation) {
                addReportAction(
                    box,
                    subtleButton(
                        "🗺️ Ver ubicación en Google Maps",
                        blue,
                        softBlue
                    ) {
                        openMap(report)
                    }
                )
            }

            if (canRequestClosure) {
                addReportAction(
                    box,
                    subtleButton(
                        "◷ Avisar que el incidente terminó",
                        violet,
                        Color.rgb(242, 237, 255)
                    ) {
                        AlertDialog.Builder(this)
                            .setTitle("Solicitar revisión")
                            .setMessage(
                                "Esto NO cerrará el incidente. Un administrador verificará la información."
                            )
                            .setPositiveButton(
                                "Enviar aviso"
                            ) { _, _ ->
                                requestClosure(report)
                            }
                            .setNegativeButton(
                                "Cancelar",
                                null
                            )
                            .show()
                    }
                )
            }

            if (canManage) {
                addReportAction(
                    box,
                    button(
                        "✓ Confirmar finalización",
                        teal
                    ) {
                        AlertDialog.Builder(this)
                            .setTitle(
                                "Verificación de administrador"
                            )
                            .setMessage(
                                "Confirma solo después de comprobar que terminó. " +
                                        "Se retirará de la lista de activos, pero conservará el historial."
                            )
                            .setPositiveButton(
                                "Confirmar"
                            ) { _, _ ->
                                changeStatus(
                                    report.id,
                                    "Finalizado"
                                )
                            }
                            .setNegativeButton(
                                "Cancelar",
                                null
                            )
                            .show()
                    }
                )

                addReportAction(
                    box,
                    subtleButton(
                        "↩ Mantener incidente activo",
                        amber,
                        softAmber
                    ) {
                        changeStatus(
                            report.id,
                            "Activo"
                        )
                    }
                )
            }
        }
    }

    // =========================================
    // SOLICITUDES DE CIERRE
    // =========================================

    private fun requestClosure(report: Incident) {
        val uid = auth.currentUser?.uid
            ?: run {
                toast("Debes iniciar sesión")
                return
            }

        incidentController.requestIncidentClosure(
            incidentId = report.id,
            userId = uid,
            onSuccess = {
                toast(
                    "Aviso enviado. El accidente permanecerá activo hasta la confirmación de un administrador."
                )
            },
            onError = { message ->
                toast(
                    "No se pudo enviar (quizás ya avisaste): $message"
                )
            }
        )
    }

    private fun changeStatus(
        id: String,
        status: String
    ) {
        if (!isAdministrator) {
            toast(
                "Esta acción requiere permisos administrativos"
            )
            return
        }

        adminController.updateIncidentStatus(
            incidentId = id,
            newStatus = status,
            onSuccess = {
                toast("Estado actualizado: $status")
            },
            onError = { message ->
                toast(
                    "Acceso rechazado o sin conexión: $message"
                )
            }
        )
    }

    // =========================================
    // ESTADÍSTICAS MODERNAS
    // =========================================

    private fun percentage(
        count: Int,
        total: Int
    ): Int {
        if (total <= 0) return 0

        return (
                count.toDouble() * 100.0 /
                        total.toDouble()
                ).toInt().coerceIn(0, 100)
    }

    private fun dashboardSummaryCard(
        number: Int,
        label: String,
        accent: Int,
        fill: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgColor(fill, 17)

            setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
            )

            addView(
                text(
                    number.toString(),
                    29f,
                    accent,
                    true
                )
            )

            addView(
                text(
                    label,
                    13f,
                    ink,
                    true
                )
            )
        }
    }

    private fun statisticsProgressRow(
        label: String,
        count: Int,
        total: Int,
        accent: Int,
        fill: Int
    ): LinearLayout {

        val percent = percentage(count, total)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(2),
                dp(11),
                dp(2),
                dp(11)
            )

            val heading = LinearLayout(
                this@CloudActivity
            ).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            heading.addView(
                text(
                    label,
                    14f,
                    ink,
                    true
                ),
                LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                )
            )

            heading.addView(
                text(
                    "$count  ·  $percent%",
                    13f,
                    accent,
                    true
                )
            )

            addView(heading)

            val progress = ProgressBar(
                this@CloudActivity,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                setProgress(percent)

                progressTintList =
                    ColorStateList.valueOf(accent)

                progressBackgroundTintList =
                    ColorStateList.valueOf(fill)
            }

            addView(
                progress,
                LinearLayout.LayoutParams(
                    -1,
                    dp(11)
                ).apply {
                    topMargin = dp(9)
                }
            )
        }
    }

    private fun categoryColor(category: String): Int {
        return when (category) {
            "Accidente de tránsito" -> red
            "Calle dañada" -> amber
            "Fuga de agua" -> blue
            "Incendio" -> Color.rgb(180, 40, 40)
            "Seguridad" -> violet
            "Otro problema" -> teal
            else -> grey
        }
    }

    private fun categoryIcon(category: String): String {
        return when (category) {
            "Accidente de tránsito" -> "🚗"
            "Calle dañada" -> "🚧"
            "Fuga de agua" -> "💧"
            "Incendio" -> "🔥"
            "Seguridad" -> "🛡️"
            else -> "📍"
        }
    }

    private fun stats() {
        currentPage = "stats"
        listResults = null

        page(
            "Estadísticas",
            "Actividad de la comunidad en tiempo real"
        )

        val total = incidents.size

        val statusCounts =
            adminController.countByStatus(incidents)

        val active = statusCounts["Activo"] ?: 0
        val pending = statusCounts["Por verificar"] ?: 0
        val finished = statusCounts["Finalizado"] ?: 0

        val hero = card().apply {
            background = gradient()

            setPadding(
                dp(20),
                dp(21),
                dp(20),
                dp(21)
            )
        }

        hero.addView(
            text(
                "📊 Resumen de incidentes",
                21f,
                Color.WHITE,
                true
            )
        )

        hero.addView(
            text(
                "Datos obtenidos de los reportes compartidos en Firebase.",
                14f,
                Color.WHITE
            )
        )

        hero.addView(
            text(
                total.toString(),
                43f,
                Color.WHITE,
                true
            )
        )

        hero.addView(
            text(
                if (total == 1) {
                    "reporte registrado"
                } else {
                    "reportes registrados"
                },
                14f,
                Color.WHITE
            )
        )

        sectionTitle("Estado general")

        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val ongoing = dashboardSummaryCard(
            active + pending,
            "En curso",
            blue,
            softBlue
        )

        val resolved = dashboardSummaryCard(
            finished,
            "Finalizados",
            teal,
            softTeal
        )

        summaryRow.addView(
            ongoing,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            ).apply {
                rightMargin = dp(5)
            }
        )

        summaryRow.addView(
            resolved,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            ).apply {
                leftMargin = dp(5)
            }
        )

        body.addView(summaryRow)
        space(14)

        val statusPanel = card()

        statusPanel.addView(
            text(
                "Distribución por estado",
                18f,
                ink,
                true
            )
        )

        statusPanel.addView(
            text(
                "Porcentaje sobre el total de reportes",
                12f,
                grey
            )
        )

        if (total == 0) {
            statusPanel.addView(
                text(
                    "Todavía no hay datos para mostrar.",
                    14f,
                    grey
                )
            )
        }

        statusPanel.addView(
            statisticsProgressRow(
                "🔴 Activos",
                active,
                total,
                red,
                softRed
            )
        )

        statusPanel.addView(
            statisticsProgressRow(
                "🟠 Por verificar",
                pending,
                total,
                amber,
                softAmber
            )
        )

        statusPanel.addView(
            statisticsProgressRow(
                "🟢 Finalizados",
                finished,
                total,
                teal,
                softTeal
            )
        )

        sectionTitle("Incidentes por categoría")

        val categoryPanel = card()

        categoryPanel.addView(
            text(
                "Tipos de incidentes",
                18f,
                ink,
                true
            )
        )

        categoryPanel.addView(
            text(
                "Distribución de los reportes registrados",
                12f,
                grey
            )
        )

        val categoryCounts =
            adminController.countByCategory(incidents)

        val categories = listOf(
            "Accidente de tránsito",
            "Calle dañada",
            "Fuga de agua",
            "Incendio",
            "Seguridad",
            "Otro problema"
        )

        if (total == 0) {
            categoryPanel.addView(
                text(
                    "Las categorías aparecerán aquí cuando se publiquen reportes.",
                    14f,
                    grey
                )
            )
        } else {
            categories.forEach { category ->
                val count = categoryCounts[category] ?: 0

                categoryPanel.addView(
                    statisticsProgressRow(
                        "${categoryIcon(category)} $category",
                        count,
                        total,
                        categoryColor(category),
                        softBlue
                    )
                )
            }

            categoryCounts
                .filterKeys { it !in categories }
                .forEach { (category, count) ->
                    categoryPanel.addView(
                        statisticsProgressRow(
                            "📍 $category",
                            count,
                            total,
                            grey,
                            softBlue
                        )
                    )
                }
        }

        sectionTitle("Seguimiento comunitario")

        val performancePanel = card().apply {
            background = bgColor(softTeal, 18)
            elevation = 0f
        }

        performancePanel.addView(
            text(
                "✓ Reportes finalizados",
                17f,
                teal,
                true
            )
        )

        performancePanel.addView(
            text(
                "${percentage(finished, total)}%",
                34f,
                teal,
                true
            )
        )

        performancePanel.addView(
            text(
                "$finished de $total reportes han sido marcados como finalizados por administración.",
                13f,
                grey
            )
        )

        performancePanel.addView(
            text(
                "Este porcentaje representa estados registrados, no una medición oficial de seguridad.",
                12f,
                grey
            )
        )

        space(8)

        addButton(
            "📋 Ver reportes compartidos",
            violet
        ) {
            listing()
        }

        addButton(
            "← Volver al inicio",
            grey
        ) {
            home()
        }
    }

    // =========================================
    // UBICACIÓN GPS
    // =========================================

    private fun requestLocation() {
        if (
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationRequest
            )
            return
        }

        try {
            val manager = getSystemService(
                LOCATION_SERVICE
            ) as LocationManager

            val found = listOfNotNull(
                manager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                ),
                manager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER
                )
            ).maxByOrNull {
                it.time
            }

            if (found != null) {
                latitude = found.latitude
                longitude = found.longitude

                locationLabel?.text =
                    "📍 Ubicación: %.5f, %.5f".format(
                        Locale.US,
                        latitude,
                        longitude
                    )
            } else {
                toast(
                    "Activa GPS y vuelve a intentar; también puedes escribir una dirección"
                )
            }
        } catch (_: SecurityException) {
            toast("Permite acceso a ubicación")
        } catch (_: Exception) {
            toast("No se pudo obtener ubicación")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == locationRequest &&
            grantResults.any {
                it == PackageManager.PERMISSION_GRANTED
            }
        ) {
            requestLocation()
        }

        if (
            requestCode == notificationRequest &&
            grantResults.any {
                it == PackageManager.PERMISSION_GRANTED
            }
        ) {
            setNotifications(true)
        }
    }

    // =========================================
    // CÁMARA
    // =========================================

    @Deprecated(
        "La cámara devuelve miniatura de demostración"
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == cameraRequest &&
            resultCode == RESULT_OK
        ) {
            @Suppress("DEPRECATION")
            val bitmap =
                data?.extras?.get("data") as? Bitmap

            if (bitmap == null) {
                toast("No se obtuvo la imagen")
                return
            }

            val out = ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                75,
                out
            )

            photoBytes = out.toByteArray()

            photoLabel?.text =
                "📷 Fotografía agregada"
        }
    }

    // =========================================
    // GOOGLE MAPS
    // =========================================

    private fun openMap(incident: Incident) {
        val query =
            if (
                incident.lat != null &&
                incident.lon != null
            ) {
                "${incident.lat},${incident.lon}"
            } else {
                incident.address
            }

        val uri = Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
        )

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        } catch (_: Exception) {
            toast(
                "Instala un navegador o Google Maps"
            )
        }
    }

    // =========================================
    // NAVEGACIÓN
    // =========================================

    @Deprecated("Navegación simple")
    override fun onBackPressed() {
        if (currentPage == "login") {
            super.onBackPressed()
        } else {
            home()
        }
    }
}