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

    /** [saldoActual]: lo que hay hoy en esa cuenta; se calcula el saldo inicial que lo produce. */
    suspend fun guardarMedio(id: Long?, nombre: String, icono: String, saldoActual: Long? = null) {
        require(nombre.isNotBlank()) { "Escribe el nombre del medio de pago" }
        val actual = id?.let { mid -> medioDao.obtenerTodos().firstOrNull { it.id == mid } }
        val base = MedioPagoEntity(
            id = id ?: 0L, nombre = nombre.trim(), icono = icono.trim().ifEmpty { "💵" },
            saldoInicial = actual?.saldoInicial ?: 0L
        )
        val conSaldo = if (saldoActual != null) {
            val neto = if (id == null) 0L else {
                val todos = movimientoDao.obtenerTodos()
                com.mipropiobanco.domain.calcularSaldos(listOf(base.copy(saldoInicial = 0L)), todos)[id] ?: 0L
            }
            base.copy(saldoInicial = saldoActual - neto)
        } else base
        if (id == null) medioDao.insertar(conSaldo) else medioDao.actualizar(conSaldo)
    }

    /**
     * Pone cuánto hay hoy en cada cuenta/efectivo.
     * comoMovimiento = true: la diferencia se registra hoy como gasto ("Otros gastos") si sobra plata
     * registrada, o como ingreso ("Otros ingresos") si falta. false: se ajusta el punto de partida.
     */
    suspend fun ajustarSaldos(saldosHoy: Map<Long, Long>, comoMovimiento: Boolean) {
        val medios = medioDao.obtenerTodos()
        val movs = movimientoDao.obtenerTodos()
        val actuales = com.mipropiobanco.domain.calcularSaldos(medios, movs)
        val netos = com.mipropiobanco.domain.calcularSaldos(medios.map { it.copy(saldoInicial = 0L) }, movs)
        val hoy = Formato.hoyMillis()
        db.withTransaction {
            saldosHoy.forEach { (id, real) ->
                val medio = medios.firstOrNull { it.id == id } ?: return@forEach
                val diferencia = (actuales[id] ?: 0L) - real
                if (diferencia == 0L) return@forEach
                // Si falta plata registrada, es dinero que ya tenías antes: se ajusta el punto de partida,
                // nunca se inventa un ingreso en el mes.
                if (comoMovimiento && diferencia > 0L) {
                    val gasto = diferencia > 0L
                    val tipo = if (gasto) TipoMovimiento.GASTO else TipoMovimiento.INGRESO
                    movimientoDao.insertar(
                        MovimientoEntity(
                            tipo = tipo.name,
                            monto = kotlin.math.abs(diferencia),
                            categoriaId = if (gasto) categoriaPorNombre("Otros gastos", tipo, "📦")
                            else categoriaPorNombre("Otros ingresos", tipo, "➕"),
                            medioId = id,
                            fecha = hoy,
                            nota = if (gasto) "Ajuste: gastos que no se registraron" else "Ajuste: dinero que no se registró"
                        )
                    )
                } else {
                    medioDao.actualizar(medio.copy(saldoInicial = real - (netos[id] ?: 0L)))
                }
            }
        }
    }

    /** Resultado de unir las cuentas: movimientos pasados a Bancolombia y ajustes de saldo borrados. */
    data class Union(val movidos: Int, val ajustesBorrados: Int, val cuentas: List<String>)

    /**
     * Transferencia y Tarjeta débito son la misma cuenta de Bancolombia: todo pasa a un solo medio
     * "Bancolombia". Se borran los ajustes de saldo hechos antes (quedaron mal por tener la plata repartida).
     */
    suspend fun unirEnBancolombia(): Union {
        fun n(t: String) = com.mipropiobanco.domain.Interprete.normalizar(t)
        val medios = medioDao.obtenerTodos()
        val destinoId = medios.firstOrNull { n(it.nombre).contains("bancolombia") }?.id
            ?: medioDao.insertar(MedioPagoEntity(nombre = "Bancolombia", icono = "🏦"))
        val origenes = medios.filter {
            it.id != destinoId && (n(it.nombre).contains("transferencia") ||
                (n(it.nombre).contains("debito") && !n(it.nombre).contains("credito")))
        }
        val ids = origenes.map { it.id }.toSet()
        var movidos = 0
        var ajustes = 0
        db.withTransaction {
            movimientoDao.obtenerTodos().forEach { m ->
                if (m.nota.startsWith("Ajuste:")) {
                    movimientoDao.eliminar(m); ajustes++; return@forEach
                }
                val origen = if (m.medioId in ids) destinoId else m.medioId
                val destino = m.medioDestinoId?.let { if (it in ids) destinoId else it }
                if (origen == m.medioId && destino == m.medioDestinoId) return@forEach
                movidos++
                if (m.tipo == TipoMovimiento.TRASLADO.name && origen == destino) {
                    movimientoDao.eliminar(m) // traslado entre la misma cuenta: no existe
                } else {
                    movimientoDao.actualizar(m.copy(medioId = origen, medioDestinoId = destino))
                }
            }
            plantillaDao.obtenerTodas().filter { it.medioId in ids }
                .forEach { plantillaDao.actualizar(it.copy(medioId = destinoId)) }
            db.reglaCuentaDao().obtenerTodas().filter { it.medioDestinoId in ids }
                .forEach { db.reglaCuentaDao().actualizar(it.copy(medioDestinoId = destinoId)) }
            val destino = medioDao.obtenerTodos().first { it.id == destinoId }
            medioDao.actualizar(destino.copy(saldoInicial = destino.saldoInicial + origenes.sumOf { it.saldoInicial }))
            origenes.forEach { medioDao.actualizar(it.copy(saldoInicial = 0L)) }
        }
        return Union(movidos, ajustes, origenes.map { it.nombre })
    }

    /**
     * Lleva las cuentas solo desde [inicio]: borra los movimientos anteriores y los ajustes de saldo,
     * y fija el punto de partida para que lo que tienes hoy en cada cuenta no cambie.
     * Devuelve cuántos movimientos se borraron.
     */
    suspend fun empezarDesde(inicio: Long): Int {
        val medios = medioDao.obtenerTodos()
        val movs = movimientoDao.obtenerTodos()
        val hoy = com.mipropiobanco.domain.calcularSaldos(medios, movs)
        val borrar = movs.filter { it.fecha < inicio || it.nota.startsWith("Ajuste:") }
        val ids = borrar.map { it.id }.toSet()
        val quedan = movs.filter { it.id !in ids }
        val netos = com.mipropiobanco.domain.calcularSaldos(medios.map { it.copy(saldoInicial = 0L) }, quedan)
        val antesDeInicio = Formato.aLocalDate(inicio).minusDays(1)
        db.withTransaction {
            borrar.forEach { movimientoDao.eliminar(it) }
            medios.forEach { m -> medioDao.actualizar(m.copy(saldoInicial = (hoy[m.id] ?: 0L) - (netos[m.id] ?: 0L))) }
            // Los fijos que caían antes de la fecha ya no quedan pendientes.
            plantillaDao.obtenerTodas().forEach { p ->
                val viejas = Recurrencia.pendientes(p, antesDeInicio)
                if (viejas.isNotEmpty()) plantillaDao.actualizar(p.copy(ultimaOcurrencia = viejas.last().indice))
            }
        }
        borrar.mapNotNull { it.foto }.distinct().forEach { ruta ->
            if (!fotoEnUso(ruta)) runCatching { java.io.File(ruta).delete() }
        }
        return borrar.size
    }

    // ---------------- Reglas aprendidas de los correos ----------------

    suspend fun buscarRegla(patron: String): com.mipropiobanco.data.local.ReglaCuentaEntity? =
        db.reglaCuentaDao().buscar(patron)

    suspend fun recordarRegla(patron: String, tipo: TipoMovimiento, categoriaId: Long, medioDestinoId: Long?) {
        val dao = db.reglaCuentaDao()
        val existente = dao.buscar(patron)
        val regla = com.mipropiobanco.data.local.ReglaCuentaEntity(
            id = existente?.id ?: 0L, patron = patron, tipo = tipo.name,
            categoriaId = categoriaId, medioDestinoId = medioDestinoId
        )
        if (existente == null) dao.insertar(regla) else dao.actualizar(regla)
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
        foto: String? = null,
        medioDestinoId: Long? = null
    ) = db.withTransaction {
        require(monto > 0L) { "El monto debe ser mayor que cero" }
        require(medioId > 0L) { "Elige el medio de pago" }
        if (tipo == TipoMovimiento.TRASLADO) {
            require(medioDestinoId != null && medioDestinoId != medioId) { "Elige a qué cuenta llega el dinero" }
        } else {
            require(categoriaId > 0L) { "Elige una categoría" }
        }
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
            foto = foto,
            medioDestinoId = if (tipo == TipoMovimiento.TRASLADO) medioDestinoId else null
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
        fun n(t: String) = com.mipropiobanco.domain.Interprete.normalizar(t)
        return todos.firstOrNull { n(it.nombre).contains("bancolombia") }
            ?: todos.firstOrNull { n(it.nombre).contains("transferencia") }
            ?: todos.firstOrNull()
    }

    /** Anota en Gastos/Ingresos un movimiento que viene de Mi banco (aporte, autopréstamo, cuota). */
    suspend fun anotarDeMiBanco(tipo: TipoMovimiento, monto: Long, fecha: Long, nota: String, categoria: String) {
        if (monto <= 0L) return
        val medio = medioPorDefecto() ?: return
        movimientoDao.insertar(
            MovimientoEntity(
                tipo = tipo.name, monto = monto,
                categoriaId = categoriaPorNombre(categoria, tipo, "🏦"),
                medioId = medio.id, fecha = fecha, nota = nota.take(80)
            )
        )
    }

    suspend fun fotoEnUso(ruta: String): Boolean = movimientoDao.obtenerTodos().any { it.foto == ruta }

    suspend fun eliminarMovimiento(m: MovimientoEntity) {
        movimientoDao.eliminar(m)
        // La foto se borra solo si ningún otro movimiento la usa (una nómina crea varios con la misma foto).
        m.foto?.let { ruta -> if (!fotoEnUso(ruta)) runCatching { java.io.File(ruta).delete() } }
    }

    /** "Ya tenía esa plata antes de empezar a anotar": sube el punto de partida de la cuenta. */
    suspend fun sumarSaldoInicial(medioId: Long, monto: Long) {
        val m = medioDao.obtenerTodos().firstOrNull { it.id == medioId } ?: return
        medioDao.actualizar(m.copy(saldoInicial = m.saldoInicial + monto))
    }

    /** Saldo actual de una cuenta. */
    suspend fun saldoDe(medioId: Long): Long =
        com.mipropiobanco.domain.calcularSaldos(medioDao.obtenerTodos(), movimientoDao.obtenerTodos())[medioId] ?: 0L

    /** Ingreso que en realidad es plata prestada por otra persona (se vuelve deuda). */
    suspend fun categoriaPrestamosRecibidos(): Long = categoriaPorNombre("Préstamos recibidos", TipoMovimiento.INGRESO, "🤝")

    suspend fun categoriaSalario(): Long = categoriaPorNombre("Salario", TipoMovimiento.INGRESO, "💼")

    suspend fun categoriaOtrosIngresos(): Long = categoriaPorNombre("Otros ingresos", TipoMovimiento.INGRESO, "➕")

    /** Categoría para los gastos que llegan por correo y aún no clasificas. */
    suspend fun categoriaPorClasificar(): Long = categoriaPorNombre("Por clasificar", TipoMovimiento.GASTO, "🏷️")

    /** Categoría de gasto para el dinero que sacas en efectivo (se da por gastado al retirarlo). */
    suspend fun categoriaEfectivo(): Long = categoriaPorNombre("Efectivo (retiros)", TipoMovimiento.GASTO, "💵")

    /** Lo que encontraría la limpieza: duplicados exactos y traslados a efectivo. */
    data class Limpieza(val duplicados: List<MovimientoEntity>, val retiros: List<MovimientoEntity>)

    suspend fun revisarLimpieza(): Limpieza {
        val todos = movimientoDao.obtenerTodos()
        val efectivo = medioDao.obtenerTodos()
            .filter { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("efectivo") }
            .map { it.id }.toSet()
        // Duplicado = mismo tipo, monto, categoría, medio, día y nota. Se conserva el primero.
        val duplicados = todos
            .groupBy {
                listOf(
                    it.tipo, it.monto, it.categoriaId, it.medioId, it.medioDestinoId,
                    Formato.aLocalDate(it.fecha),
                    com.mipropiobanco.domain.Interprete.normalizar(it.nota).trim()
                )
            }
            .values.flatMap { grupo -> grupo.sortedBy { it.id }.drop(1) }
        val ids = duplicados.map { it.id }.toSet()
        val retiros = todos.filter {
            it.id !in ids && it.tipo == TipoMovimiento.TRASLADO.name && it.medioDestinoId in efectivo
        }
        return Limpieza(duplicados, retiros)
    }

    /** Borra los duplicados y convierte los traslados a efectivo en gastos. Devuelve (borrados, convertidos). */
    suspend fun aplicarLimpieza(): Pair<Int, Int> {
        val l = revisarLimpieza()
        val categoria = if (l.retiros.isNotEmpty()) categoriaEfectivo() else 0L
        db.withTransaction {
            l.duplicados.forEach { movimientoDao.eliminar(it) }
            l.retiros.forEach {
                movimientoDao.actualizar(
                    it.copy(tipo = TipoMovimiento.GASTO.name, categoriaId = categoria, medioDestinoId = null)
                )
            }
        }
        l.duplicados.mapNotNull { it.foto }.distinct().forEach { ruta ->
            if (!fotoEnUso(ruta)) runCatching { java.io.File(ruta).delete() }
        }
        return l.duplicados.size to l.retiros.size
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
