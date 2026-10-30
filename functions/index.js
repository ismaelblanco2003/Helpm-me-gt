"use strict";

const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

// Se dispara en el servidor cuando cualquier ciudadano crea un incidente.
// Nunca se envían notificaciones desde el cliente: así no se expone una clave FCM.
exports.notifyNewIncident = onDocumentCreated(
  {document: "incidents/{incidentId}", region: "us-central1"},
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const incident = snapshot.data();
    if (!incident || incident.status !== "Activo") return;
    const location = String(incident.address || "Ubicación GPS compartida").slice(0, 85);
    await admin.messaging().send({
      topic: "reportes_guatemala",
      notification: {
        title: "HelpMe · Nuevo reporte sin verificar",
        body: `${String(incident.category || "Incidente").slice(0, 40)} · ${location}`
      },
      data: {incidentId: event.params.incidentId},
      android: {
        priority: "high",
        notification: {channelId: "helpme_alertas"}
      }
    });
  }
);

// Un usuario NO puede actualizar status directamente. Envía una solicitud
// cuyo registro activa esta función con permisos del servidor.
exports.flagClosureForReview = onDocumentCreated(
  {document: "incidents/{incidentId}/closureRequests/{userId}", region: "us-central1"},
  async (event) => {
    if (!event.data) return;
    const ref = admin.firestore().collection("incidents").doc(event.params.incidentId);
    await admin.firestore().runTransaction(async (transaction) => {
      const current = await transaction.get(ref);
      if (current.exists && current.get("status") === "Activo") {
        transaction.update(ref, {status: "Por verificar"});
      }
    });
  }
);
