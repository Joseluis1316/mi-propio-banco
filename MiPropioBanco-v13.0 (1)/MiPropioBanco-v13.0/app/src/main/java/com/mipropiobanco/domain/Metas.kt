package com.mipropiobanco.domain

import org.json.JSONArray
import org.json.JSONObject

/** Meta de ahorro: "Moto", "Viaje", "Fondo de emergencia"... */
data class Meta(
    val id: Long,
    val nombre: String,
    val icono: String,
    val objetivo: Long,
    val ahorrado: Long,
    /** Fecha límite en ms (0 = sin fecha). */
    val fechaLimite: Long = 0L
) {
    val fraccion: Float get() = if (objetivo > 0L) (ahorrado.toFloat() / objetivo).coerceIn(0f, 1f) else 0f
    val falta: Long get() = (objetivo - ahorrado).coerceAtLeast(0L)
    val cumplida: Boolean get() = objetivo in 1..ahorrado
}

object MetasJson {
    fun leer(texto: String): List<Meta> = runCatching {
        val arr = JSONArray(texto)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Meta(
                id = o.optLong("id"),
                nombre = o.optString("nombre"),
                icono = o.optString("icono", "🎯"),
                objetivo = o.optLong("objetivo"),
                ahorrado = o.optLong("ahorrado"),
                fechaLimite = o.optLong("fechaLimite", 0L)
            )
        }
    }.getOrDefault(emptyList())

    fun escribir(metas: List<Meta>): String = JSONArray().apply {
        metas.forEach { m ->
            put(
                JSONObject().put("id", m.id).put("nombre", m.nombre).put("icono", m.icono)
                    .put("objetivo", m.objetivo).put("ahorrado", m.ahorrado).put("fechaLimite", m.fechaLimite)
            )
        }
    }.toString()
}
