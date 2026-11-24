const PROYECTO = 'mi-propio-banco-4b941';
const UID = 'TU_CODIGO';
const ETIQUETA = 'MiBanco';
const ETIQUETA_LEIDOS = 'MiBanco-Leido';

function instalar() {
  ScriptApp.getProjectTriggers().forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('revisarCorreos').timeBased().everyMinutes(15).create();
  revisarCorreos();
}

/** La app llama aquí con el botón "🔄 Buscar correos ahora". */
function doGet(e) {
  const p = (e && e.parameter) || {};
  if (p.uid !== UID) return ContentService.createTextOutput('NO');
  const n = p.todo === '1' ? reenviarTodo() : revisarCorreos();
  return ContentService.createTextOutput('OK ' + n + ' correos enviados');
}

function revisarCorreos() {
  if (!GmailApp.getUserLabelByName(ETIQUETA)) return 0;
  const leidos = GmailApp.getUserLabelByName(ETIQUETA_LEIDOS) || GmailApp.createLabel(ETIQUETA_LEIDOS);
  const hilos = GmailApp.search('label:' + ETIQUETA + ' -label:' + ETIQUETA_LEIDOS + ' newer_than:60d', 0, 100);
  let enviados = 0;
  hilos.forEach(function (hilo) {
    let todoBien = true;
    hilo.getMessages().forEach(function (mensaje) {
      if (enviar(mensaje.getId(), mensaje.getPlainBody(), mensaje.getDate())) enviados++;
      else todoBien = false;
    });
    if (todoBien) hilo.addLabel(leidos);
  });
  return enviados;
}

function reenviarTodo() {
  const leidos = GmailApp.getUserLabelByName(ETIQUETA_LEIDOS);
  if (leidos) GmailApp.search('label:' + ETIQUETA_LEIDOS).forEach(function (h) { h.removeLabel(leidos); });
  return revisarCorreos();
}

function limpiar(texto) {
  return String(texto)
    .replace(/\[[^\]]*\]/g, ' ')
    .replace(/https?:\/\/\S+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function enviar(id, texto, fecha) {
  const url = 'https://firestore.googleapis.com/v1/projects/' + PROYECTO +
    '/databases/(default)/documents/usuarios/' + UID + '/bandeja/' + id;
  const cuerpo = {
    fields: {
      texto: { stringValue: limpiar(texto).substring(0, 4000) },
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
