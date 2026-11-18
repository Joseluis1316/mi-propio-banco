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

    private fun leerModo(): ModoTema =
        runCatching { ModoTema.valueOf(prefs.getString(CLAVE_TEMA, null) ?: ModoTema.SISTEMA.name) }
            .getOrDefault(ModoTema.SISTEMA)

    fun cambiarModoTema(modo: ModoTema) {
        prefs.edit().putString(CLAVE_TEMA, modo.name).apply()
        _modoTema.value = modo
    }

    private companion object {
        const val CLAVE_TEMA = "modo_tema"
    }
}
