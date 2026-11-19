package com.mipropiobanco.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModoTema(val etiqueta: String) {
    SISTEMA("Según el sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro")
}

class AjustesPreferencias(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _modoTema = MutableStateFlow(leerModo())
    val modoTema: StateFlow<ModoTema> = _modoTema.asStateFlow()

    /** Cuánto puedes destinar cada mes a pagar deudas (plan bola de nieve). */
    private val _presupuestoDeudas = MutableStateFlow(prefs.getLong(CLAVE_PRESUPUESTO, 0L))
    val presupuestoDeudas: StateFlow<Long> = _presupuestoDeudas.asStateFlow()

    /** true = el usuario eligió usar la app sin cuenta (solo en este celular). */
    private val _modoLocal = MutableStateFlow(prefs.getBoolean(CLAVE_MODO_LOCAL, false))
    val modoLocal: StateFlow<Boolean> = _modoLocal.asStateFlow()

    /** Registrar solos los correos del banco que ya sabe clasificar. */
    private val _autoCorreos = MutableStateFlow(prefs.getBoolean(CLAVE_AUTO_CORREOS, true))
    val autoCorreos: StateFlow<Boolean> = _autoCorreos.asStateFlow()

    /** Desde qué fecha se llevan las cuentas (ms). 0 = desde siempre. Correos anteriores se ignoran. */
    private val _fechaInicio = MutableStateFlow(prefs.getLong(CLAVE_FECHA_INICIO, 0L))
    val fechaInicio: StateFlow<Long> = _fechaInicio.asStateFlow()

    fun cambiarFechaInicio(valor: Long) {
        prefs.edit().putLong(CLAVE_FECHA_INICIO, valor).apply()
        _fechaInicio.value = valor
    }

    /** Enlace de la app web del script de Gmail, para pedirle que revise los correos ya. */
    private val _urlScript = MutableStateFlow(prefs.getString(CLAVE_URL_SCRIPT, "") ?: "")
    val urlScript: StateFlow<String> = _urlScript.asStateFlow()

    fun cambiarUrlScript(valor: String) {
        val v = valor.trim()
        prefs.edit().putString(CLAVE_URL_SCRIPT, v).apply()
        _urlScript.value = v
    }

    fun cambiarAutoCorreos(valor: Boolean) {
        prefs.edit().putBoolean(CLAVE_AUTO_CORREOS, valor).apply()
        _autoCorreos.value = valor
    }

    private fun leerModo(): ModoTema =
        runCatching { ModoTema.valueOf(prefs.getString(CLAVE_TEMA, null) ?: ModoTema.SISTEMA.name) }
            .getOrDefault(ModoTema.SISTEMA)

    fun cambiarModoTema(modo: ModoTema) {
        prefs.edit().putString(CLAVE_TEMA, modo.name).apply()
        _modoTema.value = modo
    }

    fun cambiarPresupuestoDeudas(valor: Long) {
        val v = valor.coerceAtLeast(0L)
        prefs.edit().putLong(CLAVE_PRESUPUESTO, v).apply()
        _presupuestoDeudas.value = v
    }

    fun cambiarModoLocal(valor: Boolean) {
        prefs.edit().putBoolean(CLAVE_MODO_LOCAL, valor).apply()
        _modoLocal.value = valor
    }

    // ---- Control de sincronización ----

    /** Momento del último cambio hecho en este celular. */
    fun localModificado(): Long = prefs.getLong(CLAVE_MODIFICADO, 0L)

    fun fijarModificado(millis: Long) = prefs.edit().putLong(CLAVE_MODIFICADO, millis).apply()

    fun marcarModificado() = fijarModificado(System.currentTimeMillis())

    /** Momento de la última subida exitosa a la nube. */
    fun ultimaSincronizacion(): Long = prefs.getLong(CLAVE_SINCRONIZADO, 0L)

    fun marcarSincronizado(millis: Long) = prefs.edit().putLong(CLAVE_SINCRONIZADO, millis).apply()

    fun reiniciarSincronizacion() {
        prefs.edit().remove(CLAVE_MODIFICADO).remove(CLAVE_SINCRONIZADO).apply()
    }

    private companion object {
        const val CLAVE_TEMA = "modo_tema"
        const val CLAVE_FECHA_INICIO = "fecha_inicio"
        const val CLAVE_URL_SCRIPT = "url_script"
        const val CLAVE_PRESUPUESTO = "presupuesto_deudas"
        const val CLAVE_MODO_LOCAL = "modo_local"
        const val CLAVE_MODIFICADO = "local_modificado"
        const val CLAVE_SINCRONIZADO = "ultima_sincronizacion"
        const val CLAVE_AUTO_CORREOS = "auto_correos"
    }
}
