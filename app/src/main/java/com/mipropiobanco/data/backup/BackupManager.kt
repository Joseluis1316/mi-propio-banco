package com.mipropiobanco.data.backup

import androidx.room.withTransaction
import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.local.DeudaEntity
import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import com.mipropiobanco.data.local.TipoPrestamoEntity
import com.mipropiobanco.data.prefs.AjustesPreferencias
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.Tasas
import com.mipropiobanco.util.Formato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Exporta/importa todo el banco. JSON = respaldo completo; CSV = movimientos para Excel. */
class BackupManager(
    private val db: BancoDatabase,
    private val preferencias: AjustesPreferencias
) {

    private data class Datos(
        val aportes: List<AporteEntity>,
        val tipos: List<TipoPrestamoEntity>?,
        val prestamos: List<PrestamoEntity>,
        val pagos: List<PagoEntity>,
        val deudas: List<DeudaEntity>,
        val abonos: List<AbonoDeudaEntity>
    )

    suspend fun exportarJson(): String = withContext(Dispatchers.IO) {
        val raiz = JSONObject()
        raiz.put("app", APP_ID)
        raiz.put("version", VERSION)
        raiz.put("exportadoEn", Formato.hoyIso())
        raiz.put("presupuestoDeudas", preferencias.presupuestoDeudas.value)

        raiz.put("aportes", JSONArray().apply {
            db.aporteDao().obtenerTodos().forEach { a ->
                put(JSONObject().put("id", a.id).put("fecha", a.fecha).put("concepto", a.concepto).put("monto", a.monto))
            }
        })
        raiz.put("tiposPrestamo", JSONArray().apply {
            db.tipoPrestamoDao().obtenerTodos().forEach { t ->
                put(JSONObject().put("id", t.id).put("nombre", t.nombre).put("tasaEA", t.tasaEA).put("tasaMoraEA", t.tasaMoraEA))
            }
        })
        raiz.put("prestamos", JSONArray().apply {
            db.prestamoDao().obtenerTodos().forEach { p ->
                put(
                    JSONObject()
                        .put("id", p.id)
                        .put("concepto", p.concepto)
                        .put("monto", p.monto)
                        .put("plazo", p.plazo)
                        .put("tasaPeriodo", p.tasaPeriodo)
                        .put("cuota", p.cuota)
                        .put("fechaCreacion", p.fechaCreacion)
                        .put("pagado", p.pagado)
                        .put("periodicidad", p.periodicidad)
                        .put("tipo", p.tipo)
                        .put("tasaEA", p.tasaEA)
                        .put("tasaMoraEA", p.tasaMoraEA)
                )
            }
        })
        raiz.put("pagos", JSONArray().apply {
            db.pagoDao().obtenerTodos().forEach { g ->
                put(
                    JSONObject()
                        .put("id", g.id)
                        .put("prestamoId", g.prestamoId)
                        .put("numeroCuota", g.numeroCuota)
                        .put("fecha", g.fecha)
                        .put("capital", g.capital)
                        .put("interes", g.interes)
                        .put("mora", g.mora)
                        .put("diasAtraso", g.diasAtraso)
                )
            }
        })
        raiz.put("deudas", JSONArray().apply {
            db.deudaDao().obtenerTodas().forEach { d ->
                put(
                    JSONObject()
                        .put("id", d.id)
                        .put("acreedor", d.acreedor)
                        .put("saldoInicial", d.saldoInicial)
                        .put("pagoMinimo", d.pagoMinimo)
                        .put("tasaEA", d.tasaEA)
                        .put("diaPago", d.diaPago)
                        .put("fechaRegistro", d.fechaRegistro)
                )
            }
        })
        raiz.put("abonosDeuda", JSONArray().apply {
            db.abonoDeudaDao().obtenerTodos().forEach { a ->
                put(JSONObject().put("id", a.id).put("deudaId", a.deudaId).put("fecha", a.fecha).put("monto", a.monto))
            }
        })
        raiz.toString(2)
    }

    /** Reemplaza todos los datos actuales por los del respaldo (acepta respaldos v1 y v2). */
    suspend fun importarJson(texto: String): String = withContext(Dispatchers.IO) {
        val raiz = try {
            JSONObject(texto)
        } catch (e: JSONException) {
            throw IllegalArgumentException("El archivo no es un JSON válido")
        }
        require(raiz.optString("app") == APP_ID) { "El archivo no es un respaldo de Mi Propio Banco" }

        val datos = try {
            leer(raiz)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Respaldo incompleto o dañado: ${e.message}")
        }

        val idsPrestamos = datos.prestamos.map { it.id }.toSet()
        require(datos.pagos.all { it.prestamoId in idsPrestamos }) { "Respaldo inconsistente: hay pagos sin préstamo" }
        val idsDeudas = datos.deudas.map { it.id }.toSet()
        require(datos.abonos.all { it.deudaId in idsDeudas }) { "Respaldo inconsistente: hay pagos sin deuda" }
        val disponible = datos.aportes.sumOf { it.monto } - datos.prestamos.sumOf { it.monto } +
            datos.pagos.sumOf { it.capital + it.interes + it.mora }
        require(disponible >= 0L) { "Respaldo inconsistente: el disponible quedaría negativo" }

        db.withTransaction {
            db.pagoDao().borrarTodo()
            db.prestamoDao().borrarTodo()
            db.aporteDao().borrarTodo()
            db.abonoDeudaDao().borrarTodo()
            db.deudaDao().borrarTodo()
            db.aporteDao().insertarTodos(datos.aportes)
            db.prestamoDao().insertarTodos(datos.prestamos)
            db.pagoDao().insertarTodos(datos.pagos)
            db.deudaDao().insertarTodas(datos.deudas)
            db.abonoDeudaDao().insertarTodos(datos.abonos)
            if (datos.tipos != null && datos.tipos.isNotEmpty()) {
                db.tipoPrestamoDao().borrarTodo()
                db.tipoPrestamoDao().insertarTodos(datos.tipos)
            }
        }
        if (raiz.has("presupuestoDeudas")) {
            preferencias.cambiarPresupuestoDeudas(raiz.optLong("presupuestoDeudas", 0L))
        }
        "Respaldo importado: ${datos.aportes.size} aportes, ${datos.prestamos.size} préstamos, " +
            "${datos.deudas.size} deudas"
    }

    private fun leer(raiz: JSONObject): Datos {
        val aportes = raiz.getJSONArray("aportes").objetos().map {
            AporteEntity(
                id = it.getLong("id"),
                fecha = it.getLong("fecha"),
                concepto = it.getString("concepto"),
                monto = it.getLong("monto")
            )
        }
        val tipos = raiz.optJSONArray("tiposPrestamo")?.objetos()?.map {
            TipoPrestamoEntity(
                id = it.getLong("id"),
                nombre = it.getString("nombre"),
                tasaEA = it.getDouble("tasaEA"),
                tasaMoraEA = it.getDouble("tasaMoraEA")
            )
        }
        val prestamos = raiz.getJSONArray("prestamos").objetos().map {
            // v1 guardaba plazoMeses y tasaMensual (siempre mensual).
            val periodicidad = Periodicidad.desde(it.optString("periodicidad", "MENSUAL"))
            val tasaPeriodo = if (it.has("tasaPeriodo")) it.getDouble("tasaPeriodo") else it.getDouble("tasaMensual")
            val tasaEA = if (it.has("tasaEA")) it.getDouble("tasaEA") else Tasas.eaDesdePeriodo(tasaPeriodo, periodicidad)
            PrestamoEntity(
                id = it.getLong("id"),
                concepto = it.getString("concepto"),
                monto = it.getLong("monto"),
                plazo = if (it.has("plazo")) it.getInt("plazo") else it.getInt("plazoMeses"),
                tasaPeriodo = tasaPeriodo,
                cuota = it.getLong("cuota"),
                fechaCreacion = it.getLong("fechaCreacion"),
                pagado = it.getBoolean("pagado"),
                periodicidad = periodicidad.name,
                tipo = it.optString("tipo", "Personalizado"),
                tasaEA = tasaEA,
                tasaMoraEA = if (it.has("tasaMoraEA")) it.getDouble("tasaMoraEA") else tasaEA
            )
        }
        val pagos = raiz.getJSONArray("pagos").objetos().map {
            PagoEntity(
                id = it.getLong("id"),
                prestamoId = it.getLong("prestamoId"),
                numeroCuota = it.getInt("numeroCuota"),
                fecha = it.getLong("fecha"),
                capital = it.getLong("capital"),
                interes = it.getLong("interes"),
                mora = it.optLong("mora", 0L),
                diasAtraso = it.optInt("diasAtraso", 0)
            )
        }
        val deudas = raiz.optJSONArray("deudas")?.objetos()?.map {
            DeudaEntity(
                id = it.getLong("id"),
                acreedor = it.getString("acreedor"),
                saldoInicial = it.getLong("saldoInicial"),
                pagoMinimo = it.getLong("pagoMinimo"),
                tasaEA = it.getDouble("tasaEA"),
                diaPago = it.getInt("diaPago"),
                fechaRegistro = it.getLong("fechaRegistro")
            )
        }.orEmpty()
        val abonos = raiz.optJSONArray("abonosDeuda")?.objetos()?.map {
            AbonoDeudaEntity(
                id = it.getLong("id"),
                deudaId = it.getLong("deudaId"),
                fecha = it.getLong("fecha"),
                monto = it.getLong("monto")
            )
        }.orEmpty()
        return Datos(aportes, tipos, prestamos, pagos, deudas, abonos)
    }

    /** true si este celular no tiene aportes, préstamos ni deudas registrados. */
    suspend fun estaVacio(): Boolean = withContext(Dispatchers.IO) {
        db.aporteDao().obtenerTodos().isEmpty() &&
            db.prestamoDao().obtenerTodos().isEmpty() &&
            db.deudaDao().obtenerTodas().isEmpty()
    }

    /** Deja la app como recién instalada (se usa al cerrar sesión). */
    suspend fun borrarTodo() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.pagoDao().borrarTodo()
            db.prestamoDao().borrarTodo()
            db.aporteDao().borrarTodo()
            db.abonoDeudaDao().borrarTodo()
            db.deudaDao().borrarTodo()
            db.tipoPrestamoDao().borrarTodo()
            db.tipoPrestamoDao().insertarTodos(BancoDatabase.TIPOS_INICIALES)
        }
        preferencias.cambiarPresupuestoDeudas(0L)
    }

    /** CSV separado por punto y coma (lo abre Excel en español directamente). */
    suspend fun exportarCsv(): String = withContext(Dispatchers.IO) {
        val prestamos = db.prestamoDao().obtenerTodos().associateBy { it.id }
        val deudas = db.deudaDao().obtenerTodas().associateBy { it.id }
        val sb = StringBuilder("﻿")
        sb.append("tipo;fecha;concepto;monto;capital;interes;mora;dias_atraso;referencia_id;cuota_numero\n")
        db.aporteDao().obtenerTodos().forEach { a ->
            sb.fila("APORTE", Formato.fechaIso(a.fecha), a.concepto, a.monto, "", "", "", "", "", "")
        }
        prestamos.values.forEach { p ->
            val per = Periodicidad.desde(p.periodicidad)
            sb.fila(
                "PRESTAMO", Formato.fechaIso(p.fechaCreacion),
                "${p.concepto} (${p.tipo}, ${per.cantidad(p.plazo)} al ${Formato.tasa(p.tasaEA)} E.A.)",
                -p.monto, "", "", "", "", p.id, ""
            )
        }
        db.pagoDao().obtenerTodos().forEach { g ->
            val concepto = "Pago cuota ${g.numeroCuota} - ${prestamos[g.prestamoId]?.concepto.orEmpty()}"
            sb.fila(
                "PAGO_PRESTAMO", Formato.fechaIso(g.fecha), concepto,
                g.capital + g.interes + g.mora, g.capital, g.interes, g.mora, g.diasAtraso, g.prestamoId, g.numeroCuota
            )
        }
        deudas.values.forEach { d ->
            sb.fila(
                "DEUDA", Formato.fechaIso(d.fechaRegistro),
                "${d.acreedor} (mínimo ${Formato.cop(d.pagoMinimo)}, día ${d.diaPago})",
                d.saldoInicial, "", "", "", "", d.id, ""
            )
        }
        db.abonoDeudaDao().obtenerTodos().forEach { a ->
            sb.fila(
                "PAGO_DEUDA", Formato.fechaIso(a.fecha), "Pago a ${deudas[a.deudaId]?.acreedor.orEmpty()}",
                a.monto, "", "", "", "", a.deudaId, ""
            )
        }
        sb.toString()
    }

    private fun StringBuilder.fila(vararg campos: Any) {
        append(campos.joinToString(";") { campo ->
            val texto = campo.toString()
            if (texto.contains(';') || texto.contains('"') || texto.contains('\n')) {
                "\"" + texto.replace("\"", "\"\"") + "\""
            } else texto
        })
        append('\n')
    }

    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        private const val APP_ID = "MiPropioBanco"
        private const val VERSION = 2
    }
}
