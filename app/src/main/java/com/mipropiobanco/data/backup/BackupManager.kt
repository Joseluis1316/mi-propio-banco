package com.mipropiobanco.data.backup

import androidx.room.withTransaction
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import com.mipropiobanco.util.Formato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Exporta/importa todo el banco. JSON = respaldo completo; CSV = movimientos para Excel. */
class BackupManager(private val db: BancoDatabase) {

    suspend fun exportarJson(): String = withContext(Dispatchers.IO) {
        val raiz = JSONObject()
        raiz.put("app", APP_ID)
        raiz.put("version", VERSION)
        raiz.put("exportadoEn", Formato.hoyIso())

        val aportes = JSONArray()
        db.aporteDao().obtenerTodos().forEach { a ->
            aportes.put(
                JSONObject()
                    .put("id", a.id)
                    .put("fecha", a.fecha)
                    .put("concepto", a.concepto)
                    .put("monto", a.monto)
            )
        }
        val prestamos = JSONArray()
        db.prestamoDao().obtenerTodos().forEach { p ->
            prestamos.put(
                JSONObject()
                    .put("id", p.id)
                    .put("concepto", p.concepto)
                    .put("monto", p.monto)
                    .put("plazoMeses", p.plazoMeses)
                    .put("tasaMensual", p.tasaMensual)
                    .put("cuota", p.cuota)
                    .put("fechaCreacion", p.fechaCreacion)
                    .put("pagado", p.pagado)
            )
        }
        val pagos = JSONArray()
        db.pagoDao().obtenerTodos().forEach { g ->
            pagos.put(
                JSONObject()
                    .put("id", g.id)
                    .put("prestamoId", g.prestamoId)
                    .put("numeroCuota", g.numeroCuota)
                    .put("fecha", g.fecha)
                    .put("capital", g.capital)
                    .put("interes", g.interes)
            )
        }
        raiz.put("aportes", aportes)
        raiz.put("prestamos", prestamos)
        raiz.put("pagos", pagos)
        raiz.toString(2)
    }

    /** Reemplaza todos los datos actuales por los del respaldo. Devuelve un resumen. */
    suspend fun importarJson(texto: String): String = withContext(Dispatchers.IO) {
        val raiz = try {
            JSONObject(texto)
        } catch (e: JSONException) {
            throw IllegalArgumentException("El archivo no es un JSON válido")
        }
        require(raiz.optString("app") == APP_ID) { "El archivo no es un respaldo de Mi Propio Banco" }

        val (aportes, prestamos, pagos) = try {
            Triple(leerAportes(raiz), leerPrestamos(raiz), leerPagos(raiz))
        } catch (e: JSONException) {
            throw IllegalArgumentException("Respaldo incompleto o dañado: ${e.message}")
        }

        val idsPrestamos = prestamos.map { it.id }.toSet()
        require(pagos.all { it.prestamoId in idsPrestamos }) { "Respaldo inconsistente: hay pagos sin préstamo" }
        val disponible = aportes.sumOf { it.monto } - prestamos.sumOf { it.monto } +
            pagos.sumOf { it.capital + it.interes }
        require(disponible >= 0L) { "Respaldo inconsistente: el disponible quedaría negativo" }

        db.withTransaction {
            db.pagoDao().borrarTodo()
            db.prestamoDao().borrarTodo()
            db.aporteDao().borrarTodo()
            db.aporteDao().insertarTodos(aportes)
            db.prestamoDao().insertarTodos(prestamos)
            db.pagoDao().insertarTodos(pagos)
        }
        "Respaldo importado: ${aportes.size} aportes, ${prestamos.size} préstamos, ${pagos.size} pagos"
    }

    /** CSV separado por punto y coma (lo abre Excel en español directamente). */
    suspend fun exportarCsv(): String = withContext(Dispatchers.IO) {
        val prestamos = db.prestamoDao().obtenerTodos().associateBy { it.id }
        val sb = StringBuilder("\uFEFF")
        sb.append("tipo;fecha;concepto;monto;capital;interes;prestamo_id;cuota_numero\n")
        db.aporteDao().obtenerTodos().forEach { a ->
            sb.fila("APORTE", Formato.fechaIso(a.fecha), a.concepto, a.monto, "", "", "", "")
        }
        prestamos.values.forEach { p ->
            sb.fila(
                "PRESTAMO", Formato.fechaIso(p.fechaCreacion),
                "${p.concepto} (${p.plazoMeses} meses al ${Formato.tasa(p.tasaMensual)} mensual)",
                -p.monto, "", "", p.id, ""
            )
        }
        db.pagoDao().obtenerTodos().forEach { g ->
            val concepto = "Pago cuota ${g.numeroCuota} - ${prestamos[g.prestamoId]?.concepto.orEmpty()}"
            sb.fila(
                "PAGO", Formato.fechaIso(g.fecha), concepto,
                g.capital + g.interes, g.capital, g.interes, g.prestamoId, g.numeroCuota
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

    private fun leerAportes(raiz: JSONObject): List<AporteEntity> =
        raiz.getJSONArray("aportes").objetos().map {
            AporteEntity(
                id = it.getLong("id"),
                fecha = it.getLong("fecha"),
                concepto = it.getString("concepto"),
                monto = it.getLong("monto")
            )
        }

    private fun leerPrestamos(raiz: JSONObject): List<PrestamoEntity> =
        raiz.getJSONArray("prestamos").objetos().map {
            PrestamoEntity(
                id = it.getLong("id"),
                concepto = it.getString("concepto"),
                monto = it.getLong("monto"),
                plazoMeses = it.getInt("plazoMeses"),
                tasaMensual = it.getDouble("tasaMensual"),
                cuota = it.getLong("cuota"),
                fechaCreacion = it.getLong("fechaCreacion"),
                pagado = it.getBoolean("pagado")
            )
        }

    private fun leerPagos(raiz: JSONObject): List<PagoEntity> =
        raiz.getJSONArray("pagos").objetos().map {
            PagoEntity(
                id = it.getLong("id"),
                prestamoId = it.getLong("prestamoId"),
                numeroCuota = it.getInt("numeroCuota"),
                fecha = it.getLong("fecha"),
                capital = it.getLong("capital"),
                interes = it.getLong("interes")
            )
        }

    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        private const val APP_ID = "MiPropioBanco"
        private const val VERSION = 1
    }
}
