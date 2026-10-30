# HelpMe 2.2 — instrucciones para Firebase

## 1. Lo que ya está preparado

La app tiene dos modos según exista el archivo oficial `app/google-services.json`:

- **Sin Firebase**: demostración local, solo para revisar el diseño. Se muestra el botón **Entrar como administrador**, pero el acceso real necesita Firebase. No hay PIN fijo ni acceso administrativo sin configurar. No envía reportes a otros teléfonos.
- **Con Firebase**: presenta por separado **Entrar como ciudadano** y **Entrar como administrador**, inicia sesión con Firebase Authentication, comparte incidentes a través de Firestore, sube fotografías a Cloud Storage y recibe mensajes FCM. Los permisos administrativos se verifican con custom claims. Las notificaciones desde el servidor dependen del despliegue de Functions.

La contraseña la establece la persona titular de la cuenta y nunca se escribe en la APK. Se puede cambiar mediante el restablecimiento de contraseña de Firebase sin modificar el código. No existe una cuenta o PIN de administrador predefinidos. **Ningún usuario puede convertirse a sí mismo en administrador**.

## 2. Crea un proyecto Firebase

En [Firebase Console](https://console.firebase.google.com/) crea un proyecto. Registra una app Android con este **ID exacto**: `com.example.helpme`.

Descarga el archivo `google-services.json` y cópialo a `HelpMe_2_Guatemala/app/google-services.json`. No lo renombres ni cambies el ID. Firebase explica estos pasos en su [documentación oficial](https://firebase.google.com/docs/android/setup).

En Firebase Console activa **Authentication → Email/Password**, configura **Firestore Database** en modo producción, y prepara **Cloud Storage**. **No utilices reglas abiertas para pruebas**.

## 3. Instala la seguridad y el servidor

Con Node.js 22 y Firebase CLI instalados en la computadora del responsable del proyecto:

```bash
npm install -g firebase-tools
firebase login
firebase use --add
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,storage,functions
```

Si `firebase use --add` indica que no estás en un proyecto, confirma que la terminal esté abierta en esta carpeta, que incluye `firebase.json`.

El despliegue de Cloud Functions y el uso de Storage pueden requerir **Blaze y facturación habilitada**. Revisa el coste con el responsable del proyecto antes de activarlo. Sin desplegar las Functions, los reportes pueden compartirse, **pero las notificaciones automáticas y el cambio a “Por verificar” no funcionarán correctamente**.

## 4. Cómo conceder acceso al administrador

1. Instala la aplicación conectada a Firebase.
2. Registra con correo y contraseña la cuenta del futuro administrador (nace como ciudadano).
3. En Firebase Console → Authentication → Users busca su `UID`.
4. Un responsable técnico autorizado debe ejecutar `functions/scripts/grant-admin.js` en su entorno de confianza con **Application Default Credentials** de Firebase Admin SDK:

```bash
cd functions
node scripts/grant-admin.js UID_DE_USUARIO true
```

Para revocar:

```bash
node scripts/grant-admin.js UID_DE_USUARIO false
```

El administrador debe volver a iniciar sesión. **Nunca introduzcas una clave de cuenta de servicio en la app ni la envíes por WhatsApp.** Consulta la [documentación sobre claims](https://firebase.google.com/docs/auth/admin/custom-claims).

> `grant-admin.js` requiere credenciales administrativas del proyecto en la computadora del responsable. No funcionará solo con un usuario/contraseña de la app. No coloques esas credenciales dentro del ZIP compartido.

## 5. Prueba entre celulares

- Genera una APK nueva **después de copiar `google-services.json`** y terminar la sincronización de Gradle.
- Instala esa misma APK en dos Android. Regístrate con dos correos diferentes.
- Activa el permiso de notificaciones y toca **Activar alertas de Guatemala**.
- Envía un reporte desde A. Comprueba en B que aparece.
- Pide cerrar desde B. Quedará pendiente de verificación (si `flagClosureForReview` está desplegada).
- Solo el usuario con `admin=true` podrá confirmar cierre.

Las alertas se suscriben al tema nacional `reportes_guatemala`. **No hay filtro por distancia** todavía. Google Maps se abre externamente, aún no está incrustado.

## 6. Cómo obtener la nueva APK

En Android Studio abre la carpeta `HelpMe_2_Guatemala`, espera a que termine **Sync Now**, y usa **Build → Generate App Bundles or APKs → Generate APKs**. El archivo generado será generalmente `app/build/outputs/apk/debug/app-debug.apk`.

**Importante:** sin configuración Firebase, esa APK es solo una demostración local. No ofrece las funciones remotas.
