/**
 * Mi Propio Banco · Lector de correos de Bancolombia
 *
 * Revisa cada 15 minutos los correos con la etiqueta MiBanco y los envía a tu
 * bandeja en Firebase. La app los interpreta y te los muestra (o los registra
 * sola si ya conoce esa cuenta o comercio).
 *
 * Configuración (una sola vez):
 *  1. Reemplaza TU_CODIGO por el código que muestra la app en Ajustes › Correos de Bancolombia.
 *  2. Verifica PROYECTO: es el "project_id" de tu archivo app/google-services.json.
 *  3. Menú Ejecutar › función "instalar" y acepta los permisos.
 */
const PROYECTO = 'mi-propio-banco-4b941';
const UID = 'TU_CODIGO';
const ETIQUETA = 'MiBanco';
const ETIQUETA_LEIDOS = 'MiBanco-Leido';

function instalar() {
  ScriptApp.getProjectTriggers().forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('revisarCorreos').timeBased().everyMinutes(15).create();
  revisarCorreos();
}

function revisarCorreos() {
  if (UID === 'TU_CODIGO') throw new Error('Falta poner tu código de usuario en UID');
  if (!GmailApp.getUserLabelByName(ETIQUETA)) return;
  const leidos = GmailApp.getUserLabelByName(ETIQUETA_LEIDOS) || GmailApp.createLabel(ETIQUETA_LEIDOS);
  const hilos = GmailApp.search('label:' + ETIQUETA + ' -label:' + ETIQUETA_LEIDOS + ' newer_than:60d', 0, 50);
  hilos.forEach(function (hilo) {
    let todoBien = true;
    hilo.getMessages().forEach(function (mensaje) {
      if (!enviar(mensaje.getId(), mensaje.getPlainBody(), mensaje.getDate())) todoBien = false;
    });
    if (todoBien) hilo.addLabel(leidos);
  });
}

function enviar(id, texto, fecha) {
  const url = 'https://firestore.googleapis.com/v1/projects/' + PROYECTO +
    '/databases/(default)/documents/usuarios/' + UID + '/bandeja/' + id;
  const cuerpo = {
    fields: {
      texto: { stringValue: String(texto).substring(0, 4000) },
      fecha: { integerValue: String(fecha.getTime()) },
      origen: { stringValue: 'gmail' }
    }
  };
  const respuesta = UrlFetchApp.fetch(url, {
    method: 'patch',
    contentType: 'application/json',
    payload: JSON.stringify(cuerpo),
    headers: {
      Authorization: 'Bearer ' + ScriptApp.getOAuthToken(),
      'X-Goog-User-Project': PROYECTO
    },
    muteHttpExceptions: true
  });
  const codigo = respuesta.getResponseCode();
  if (codigo >= 300) console.error('Error ' + codigo + ': ' + respuesta.getContentText());
  return codigo < 300;
}
