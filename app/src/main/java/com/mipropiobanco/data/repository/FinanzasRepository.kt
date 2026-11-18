package com.mipropiobanco.data.repository

import androidx.room.withTransaction
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.data.local.PlantillaEntity
import com.mipropiobanco.domain.Ocurrencia
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.Recurrencia
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Datos que se usan para crear una plantilla fija junto con un movimiento. */
data class DatosRepeticion(val periodicidad: Periodicidad, val automatica: Boolean)

/** Ingresos, gastos, categorías, medios de pago y plantillas fijas. */
class FinanzasRepository(private val db: BancoDatabase) {

    private val categoriaDao = db.categoriaDao()
    private val medioDao = db.medioPagoDao()
    private val movimientoDao = db.movimientoDao()
    private val plantillaDao = db.plantillaDao()

    val categorias: Flow<List<CategoriaEntity>> = categoriaDao.observarTodas()
    val medios: Flow<List<MedioPagoEntity>> = medioDao.observarTodos()
    val movimientos: Flow<List<MovimientoEntity>> = movimientoDao.observarTodos()
    val plantillas: Flow<List<PlantillaEntity>> = plantillaDao.observarTodas()

    // ---------------- Categorías ----------------

    suspend fun guardarCategoria(id: Long?, nombre: String, tipo: TipoMovimiento, icono: String, presupuesto: Long) {
        require(nombre.isNotBlank()) { "Escribe el nombre de la categoría" }
        require(presupuesto >= 0L) { "El presupuesto no puede ser negativo" }
        val c = CategoriaEntity(
            id = id ?: 0L,
            nombre = nombre.trim(),
            tipo = tipo.name,
            icono = icono.trim().ifEmpty { "📦" },
            presupuestoMensual = if (tipo == TipoMovimiento.GASTO) presupuesto else 0L
        )
        if (id == null) categoriaDao.insertar(c) else categoriaDao.actualizar(c)
    }

    suspend fun eliminarCategoria(c: CategoriaEntity) = db.withTransaction {
        check(movimientoDao.contarPorCategoria(c.id) == 0 && plantillaDao.contarPorCategoria(c.id) == 0) {
            "«${c.nombre}» tiene movimientos o plantillas. Cámbiales la categoría antes de borrarla."
        }
        categoriaDao.eliminar(c)
    }

    // ---------------- Medios de pago ----------------

    suspend fun guardarMedio(id: Long?, nombre: String, icono: String) {
        require(nombre.isNotBlank()) { "Escribe el nombre del medio de pago" }
        val m = MedioPagoEntity(id = id ?: 0L, nombre = nombre.trim(), icono = icono.trim().ifEmpty { "💵" })
        if (id == null) medioDao.insertar(m) else medioDao.actualizar(m)
    }

    suspend fun eliminarMedio(m: MedioPagoEntity) = db.withTransaction {
        check(movimientoDao.contarPorMedio(m.id) == 0 && plantillaDao.contarPorMedio(m.id) == 0) {
            "«${m.nombre}» tiene movimientos o plantillas. Cámbialos de medio antes de borrarlo."
        }
        medioDao.eliminar(m)
    }

    // ---------------- Movimientos ----------------

    /** Crea o edita un ingreso/gasto. Si [repetir] no es null, crea también la plantilla fija. */
    suspend fun guardarMovimiento(
        id: Long?,
        tipo: TipoMovimiento,
        monto: Long,
        categoriaId: Long,
        medioId: Long,
        fecha: Long,
        nota: String,
        repetir: DatosRepeticion?,
        plantillaIdExistente: Long? = null,
        foto: String? = null
    ) = db.withTransaction {
        require(monto > 0L) { "El monto debe ser mayor que cero" }
        require(categoriaId > 0L) { "Elige una categoría" }
        require(medioId > 0L) { "Elige el medio de pago" }
        var plantillaId: Long? = plantillaIdExistente
        if (id == null && repetir != null) {
            val nombre = nota.trim().ifEmpty {
                categoriaDao.obtenerTodas().firstOrNull { it.id == categoriaId }?.nombre ?: tipo.etiqueta
            }
            plantillaId = plantillaDao.insertar(
                PlantillaEntity(
                    nombre = nombre,
                    tipo = tipo.name,
                    monto = monto,
                    categoriaId = categoriaId,
                    medioId = medioId,
                    periodicidad = repetir.periodicidad.name,
                    fechaInicio = fecha,
                    automatica = repetir.automatica,
                    ultimaOcurrencia = 0
                )
            )
        }
        val m = MovimientoEntity(
            id = id ?: 0L,
            tipo = tipo.name,
            monto = monto,
            categoriaId = categoriaId,
            medioId = medioId,
            fecha = fecha,
            nota = nota.trim(),
            plantillaId = plantillaId,
            foto = foto
        )
        if (id == null) movimientoDao.insertar(m) else movimientoDao.actualizar(m)
    }

    /** Categoría de gasto para pagos de deudas (la que tenga "deuda" en el nombre). */
    suspend fun categoriaPagoDeudas(): CategoriaEntity? = categoriaDao.obtenerTodas().firstOrNull {
        it.tipo == TipoMovimiento.GASTO.name && com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("deuda")
    }

    /** Medio por defecto para pagos registrados desde Deudas: Transferencia, o el primero. */
    suspend fun medioPorDefecto(): MedioPagoEntity? {
        val todos = medioDao.obtenerTodos()
        return todos.firstOrNull { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("transferencia") }
            ?: todos.firstOrNull()
    }

    suspend fun fotoEnUso(ruta: String): Boolean = movimientoDao.obtenerTodos().any { it.foto == ruta }

    suspend fun eliminarMovimiento(m: MovimientoEntity) {
        movimientoDao.eliminar(m)
        // La foto se borra solo si ningún otro movimiento la usa (una nómina crea varios con la misma foto).
        m.foto?.let { ruta -> if (!fotoEnUso(ruta)) runCatching { java.io.File(ruta).delete() } }
    }

    private suspend fun categoriaPorNombre(nombre: String, tipo: TipoMovimiento, icono: String): Long {
        val existente = categoriaDao.obtenerTodas().firstOrNull {
            it.tipo == tipo.name && com.mipropiobanco.domain.Interprete.normalizar(it.nombre) ==
                com.mipropiobanco.domain.Interprete.normalizar(nombre)
        }
        return existente?.id ?: categoriaDao.insertar(CategoriaEntity(nombre = nombre, tipo = tipo.name, icono = icono))
    }

    /** Guarda cada concepto de la nómina: devengos como ingresos y deducciones como gastos. */
    suspend fun guardarNomina(
        items: List<com.mipropiobanco.domain.ItemNomina>,
        medioId: Long,
        fecha: Long,
        periodo: String,
        foto: String?
    ): Int = db.withTransaction {
        require(items.isNotEmpty()) { "No hay conceptos para guardar" }
        require(medioId > 0L) { "Elige el medio por el que te pagan" }
        items.forEach { item ->
            val tipo = if (item.esDeduccion) TipoMovimiento.GASTO else TipoMovimiento.INGRESO
            movimientoDao.insertar(
                MovimientoEntity(
                    tipo = tipo.name,
                    monto = item.monto,
                    categoriaId = categoriaPorNombre(item.categoria, tipo, item.icono),
                    medioId = medioId,
                    fecha = fecha,
                    nota = listOf(item.concepto, "nómina", periodo.ifBlank { null }).filterNotNull().joinToString(" · ").take(80),
                    foto = foto
                )
            )
        }
        items.size
    }

    // ---------------- Plantillas fijas ----------------

    suspend fun guardarPlantilla(
        id: Long?,
        nombre: String,
        tipo: TipoMovimiento,
        monto: Long,
        categoriaId: Long,
        medioId: Long,
        periodicidad: Periodicidad,
        fechaInicio: Long,
        automatica: Boolean,
        activa: Boolean
    ) = db.withTransaction {
        require(nombre.isNotBlank()) { "Escribe el nombre de la plantilla" }
        require(monto > 0L) { "El monto debe ser mayor que cero" }
        require(categoriaId > 0L) { "Elige una categoría" }
        require(medioId > 0L) { "Elige el medio de pago" }
        if (id == null) {
            plantillaDao.insertar(
                PlantillaEntity(
                    nombre = nombre.trim(), tipo = tipo.name, monto = monto, categoriaId = categoriaId,
                    medioId = medioId, periodicidad = periodicidad.name, fechaInicio = fechaInicio,
                    automatica = automatica, activa = activa
                )
            )
        } else {
            val actual = plantillaDao.obtenerPorId(id) ?: throw IllegalStateException("La plantilla no existe")
            // Si cambia el calendario de la plantilla, se empieza de nuevo desde la fecha de inicio.
            val cambioCalendario = actual.fechaInicio != fechaInicio || actual.periodicidad != periodicidad.name
            plantillaDao.actualizar(
                actual.copy(
                    nombre = nombre.trim(), tipo = tipo.name, monto = monto, categoriaId = categoriaId,
                    medioId = medioId, periodicidad = periodicidad.name, fechaInicio = fechaInicio,
                    automatica = automatica, activa = activa,
                    ultimaOcurrencia = if (cambioCalendario) -1 else actual.ultimaOcurrencia
                )
            )
        }
    }

    suspend fun eliminarPlantilla(p: PlantillaEntity) = plantillaDao.eliminar(p)

    /** Registra una repetición como movimiento real (con el monto que indiques). */
    suspend fun registrarOcurrencia(o: Ocurrencia, monto: Long) = db.withTransaction {
        require(monto > 0L) { "El monto debe ser mayor que cero" }
        val p = plantillaDao.obtenerPorId(o.plantilla.id) ?: throw IllegalStateException("La plantilla no existe")
        check(o.indice > p.ultimaOcurrencia) { "Esa repetición ya fue registrada" }
        movimientoDao.insertar(
            MovimientoEntity(
                tipo = p.tipo, monto = monto, categoriaId = p.categoriaId, medioId = p.medioId,
                fecha = Formato.aMillis(o.fecha), nota = p.nombre, plantillaId = p.id
            )
        )
        plantillaDao.actualizar(p.copy(ultimaOcurrencia = o.indice))
    }

    suspend fun omitirOcurrencia(o: Ocurrencia) = db.withTransaction {
        val p = plantillaDao.obtenerPorId(o.plantilla.id) ?: throw IllegalStateException("La plantilla no existe")
        if (o.indice > p.ultimaOcurrencia) plantillaDao.actualizar(p.copy(ultimaOcurrencia = o.indice))
    }

    /** Registra solas las repeticiones vencidas de las plantillas automáticas. Devuelve cuántas. */
    suspend fun procesarAutomaticas(hoy: LocalDate = LocalDate.now()): Int = db.withTransaction {
        var total = 0
        plantillaDao.obtenerTodas().filter { it.activa && it.automatica }.forEach { p ->
            val pendientes = Recurrencia.pendientes(p, hoy)
            pendientes.forEach { o ->
                movimientoDao.insertar(
                    MovimientoEntity(
                        tipo = p.tipo, monto = p.monto, categoriaId = p.categoriaId, medioId = p.medioId,
                        fecha = Formato.aMillis(o.fecha), nota = p.nombre, plantillaId = p.id
                    )
                )
                total++
            }
            if (pendientes.isNotEmpty()) plantillaDao.actualizar(p.copy(ultimaOcurrencia = pendientes.last().indice))
        }
        total
    }
}
