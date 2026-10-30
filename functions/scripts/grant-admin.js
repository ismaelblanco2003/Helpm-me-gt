"use strict";
// Ejecutar únicamente en una computadora de confianza con credenciales
// administrativas de Google configuradas fuera del repositorio.
// Ejemplo: node scripts/grant-admin.js UID_DE_FIREBASE true
// Para revocar: node scripts/grant-admin.js UID_DE_FIREBASE false
const admin = require("firebase-admin");
const uid = process.argv[2];
const grant = process.argv[3];
if (!uid || !["true", "false"].includes(grant)) {
  process.stderr.write("Uso: node scripts/grant-admin.js UID true|false\n");
  process.exit(1);
}
admin.initializeApp({credential: admin.credential.applicationDefault()});
admin.auth().setCustomUserClaims(uid, {admin: grant === "true"})
  .then(() => { process.stdout.write("Permiso actualizado. El usuario debe volver a entrar.\n"); })
  .catch((e) => { process.stderr.write(e.message + "\n"); process.exitCode = 1; });
