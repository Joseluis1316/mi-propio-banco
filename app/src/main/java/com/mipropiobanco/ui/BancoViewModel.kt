package com.mipropiobanco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mipropiobanco.MiPropioBancoApp
import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.TipoPrestamoEntity
import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.data.local.PlantillaEntity
import com.mipropiobanco.data.repository.DatosRepeticion
import com.mipropiobanco.domain.Ocurrencia
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.domain.Interprete
import com.mipropiobanco.domain.Propuesta
import com.mipropiobanco.domain.InterpreteNomina
import com.mipropiobanco.domain.ItemNomina
import com.mipropiobanco.domain.PropuestaNomina
import com.mipropiobanco.util.Fotos
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import com.mipropiobanco.data.prefs.ModoTema
import com.mipropiobanco.data.sync.EstadoNube
import com.mipropiobanco.data.sync.CorreoBandeja
import com.mipropiobanco.domain.InterpreteCorreo
import com.mipropiobanco.domain.MovimientoCorreo
import com.mipropiobanco.data.sync.SincronizacionNube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mipropiobanco.domain.BolaDeNieve
import com.mipropiobanco.domain.DeudaConAbonos
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.PlanBolaNieve
import com.mipropiobanco.domain.PrestamoConPagos
import com.mipropiobanco.domain.ResumenBanco
import com.mipropiobanco.util.Formato
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BancoViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as MiPropioBancoApp
    private val repo = app.repositorio
    private val respaldo = app.respaldo
    private val preferencias = app.preferencias
    private val nube = app.nube
    private val finanzas = app.finanzas

    // ---------- Ingresos, gastos y plantillas ----------

    val categorias: StateFlow<List<CategoriaEntity>> =
        finanzas.categorias.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val medios: StateFlow<List<MedioPagoEntity>> =
        finanzas.medios.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val movimientos: StateFlow<List<MovimientoEntity>> =
        finanzas.movimientos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plantillas: StateFlow<List<PlantillaEntity>> =
        finanzas.plantillas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guardarMovimiento(
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
    ) = ejecutar(
        accion = {
            finanzas.guardarMovimiento(id, tipo, monto, categoriaId, medioId, fecha, nota, repetir, plantillaIdExistente, foto, medioDestinoId)
        },
        mensaje = {
            when {
                id != null -> "Movimiento actualizado"
                tipo == TipoMovimiento.TRASLADO -> "Traslado de ${Formato.cop(monto)} registrado"
                repetir != null -> "${tipo.etiqueta} guardado y plantilla fija creada"
                else -> "${tipo.etiqueta} de ${Formato.cop(monto)} guardado"
            }
        }
    )

    // ---------- Asistente: recibos, voz y texto ----------

    private val _leyendoRecibo = MutableStateFlow(false)
    val leyendoRecibo: StateFlow<Boolean> = _leyendoRecibo.asStateFlow()

    /** Entiende una frase dicha o escrita y propone el movimiento. */
    fun interpretarFrase(frase: String): Propuesta =
        Interprete.interpretarFrase(frase, categorias.value, medios.value)

    /**
     * Guarda la foto reducida, lee el texto del recibo con IA en el celular (ML Kit)
     * y propone el gasto. [temporal] es el archivo de la cámara, que se borra al terminar.
     */
    fun leerRecibo(
        origen: Uri,
        temporal: File?,
        alListo: (Propuesta) -> Unit,
        alNomina: (PropuestaNomina) -> Unit = {}
    ) {
        if (_leyendoRecibo.value) return
        viewModelScope.launch {
            _leyendoRecibo.value = true
            try {
                val ruta = withContext(Dispatchers.IO) { Fotos.guardarReducida(app, origen) }
                val texto = leerTexto(ruta)
                // Si es un desprendible de nómina, se separa concepto por concepto.
                val nomina = InterpreteNomina.interpretar(texto, ruta)
                if (nomina != null) {
                    alNomina(nomina)
                    return@launch
                }
                val propuesta = Interprete.interpretarRecibo(texto, ruta, categorias.value, medios.value)
                if (propuesta.monto == null) _mensajes.emit("No encontré el total del recibo: escríbelo tú")
                alListo(propuesta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensajes.emit("No se pudo leer el recibo: ${e.message ?: "error desconocido"}")
            } finally {
                temporal?.let { runCatching { it.delete() } }
                _leyendoRecibo.value = false
            }
        }
    }

    private suspend fun leerTexto(ruta: String): String {
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val imagen = InputImage.fromFilePath(app, Uri.fromFile(File(ruta)))
            val resultado = reconocedor.process(imagen).await()
            return reconstruirFilas(resultado).ifBlank { resultado.text }
        } finally {
            reconocedor.close()
        }
    }

    /**
     * ML Kit entrega el texto por bloques (a veces columna por columna). Aquí se rearman
     * las filas de las tablas juntando las líneas que están a la misma altura.
     */
    private fun reconstruirFilas(resultado: com.google.mlkit.vision.text.Text): String {
        val lineas = resultado.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { l to it } }
        if (lineas.isEmpty()) return ""
        val ordenadas = lineas.sortedBy { it.second.centerY() }
        val filas = mutableListOf<MutableList<Pair<com.google.mlkit.vision.text.Text.Line, android.graphics.Rect>>>()
        for (item in ordenadas) {
            val fila = filas.lastOrNull()
            val alto = item.second.height().coerceAtLeast(1)
            if (fila != null) {
                val centroFila = fila.map { it.second.centerY() }.average()
                if (kotlin.math.abs(item.second.centerY() - centroFila) <= alto * 0.5) {
                    fila.add(item)
                    continue
                }
            }
            filas.add(mutableListOf(item))
        }
        return filas.joinToString("\n") { fila -> fila.sortedBy { it.second.left }.joinToString("  ") { it.first.text } }
    }

    fun guardarNomina(items: List<ItemNomina>, medioId: Long, fecha: Long, periodo: String, foto: String?) = ejecutar(
        accion = { finanzas.guardarNomina(items, medioId, fecha, periodo, foto) },
        mensaje = { n -> "Nómina guardada: $n conceptos registrados" }
    )

    /** Si el usuario cancela, se borra la foto que no se usó. */
    // ---------- Correos del banco (bandeja) ----------

    val uidNube: String? get() = nube.uid

    val bandeja: StateFlow<List<CorreoBandeja>> =
        nube.bandeja().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun descartarCorreo(id: String) = ejecutar(
        accion = { nube.quitarDeBandeja(id) },
        mensaje = { "Correo descartado" }
    )

    /** Arma el movimiento propuesto a partir del correo, usando lo aprendido de correos anteriores. */
    fun prepararCorreo(m: MovimientoCorreo, alListo: (Propuesta) -> Unit) {
        viewModelScope.launch { alListo(construirPropuesta(m).first) }
    }

    /** Devuelve la propuesta y si venía de una regla aprendida (para poder registrarla sola). */
    private suspend fun construirPropuesta(m: MovimientoCorreo): Pair<Propuesta, Boolean> {
        val regla = finanzas.buscarRegla(m.patron)
        val cats = finanzas.categorias.first()
        val meds = finanzas.medios.first()
        fun medioCon(palabra: String) = meds.firstOrNull { Interprete.normalizar(it.nombre).contains(palabra) }
        val medioBanco = medioCon("bancolombia") ?: medioCon("transferencia") ?: meds.firstOrNull()
        val medioTarjeta = when (m.tarjeta) {
            "credito" -> medioCon("credito")
            "debito" -> medioCon("bancolombia") ?: medioCon("debito")
            else -> null
        }
        var tipo = m.tipo
        var categoria: Long? = null
        var destino: Long? = null
        if (m.esRetiro) {
            // Retiro: el efectivo se da por gastado de una vez, sin preguntar.
            val p = Propuesta(
                tipo = TipoMovimiento.GASTO,
                monto = m.monto,
                categoriaId = finanzas.categoriaEfectivo(),
                medioId = (medioTarjeta ?: medioBanco)?.id,
                fecha = m.fecha,
                nota = m.resumen.take(80),
                textoOriginal = m.texto,
                patronRegla = null,
                idBandeja = m.id
            )
            return p to true
        }
        if (regla != null) {
            tipo = TipoMovimiento.desde(regla.tipo)
            categoria = regla.categoriaId.takeIf { it > 0L }
            destino = regla.medioDestinoId ?: destino
        } else if (tipo != TipoMovimiento.TRASLADO) {
            categoria = if (m.esNomina) {
                cats.firstOrNull { it.tipo == TipoMovimiento.INGRESO.name && Interprete.normalizar(it.nombre) == "salario" }?.id
            } else {
                Interprete.detectarCategoria(Interprete.normalizar(m.contraparte + " " + m.texto), tipo, cats)?.id
            }
        }
        val propuesta = Propuesta(
            tipo = tipo,
            monto = m.monto,
            categoriaId = categoria,
            medioId = (medioTarjeta ?: medioBanco)?.id,
            fecha = m.fecha,
            nota = m.resumen.take(80),
            textoOriginal = m.texto,
            medioDestinoId = destino,
            patronRegla = m.patron,
            idBandeja = m.id
        )
        return propuesta to (regla != null)
    }

    val autoCorreos: StateFlow<Boolean> = preferencias.autoCorreos
    fun cambiarAutoCorreos(valor: Boolean) = preferencias.cambiarAutoCorreos(valor)

    /**
     * Registra solos los correos que ya sabe clasificar (regla aprendida) y que no parecen duplicados.
     * Los demás quedan en la bandeja para que los revises.
     */
    private val candadoCorreos = kotlinx.coroutines.sync.Mutex()
    private val correosProcesados = mutableSetOf<String>()

    private fun registrarCorreosConocidos(correos: List<CorreoBandeja>) {
        if (correos.isEmpty()) return
        viewModelScope.launch {
          candadoCorreos.lock()
          try {
            var registrados = 0
            var descartados = 0
            val existentes = finanzas.movimientos.first().toMutableList()
            val textosVistos = mutableSetOf<String>()
            for (c in correos.sortedBy { it.fecha }) {
                if (c.id in correosProcesados) continue
                // El mismo correo repetido: se quita de la bandeja.
                if (!textosVistos.add(c.texto.trim())) {
                    correosProcesados.add(c.id)
                    runCatching { nube.quitarDeBandeja(c.id) }.onSuccess { descartados++ }
                    continue
                }
                val m = InterpreteCorreo.interpretar(
                    c.id, c.texto, Formato.aLocalDate(c.fecha.takeIf { it > 0L } ?: Formato.hoyMillis())
                ) ?: continue
                // Anterior a la fecha desde la que llevas las cuentas: no cuenta.
                val inicio = preferencias.fechaInicio.value
                if (inicio > 0L && Formato.aMillis(m.fecha) < inicio) {
                    correosProcesados.add(c.id)
                    runCatching { nube.quitarDeBandeja(c.id) }.onSuccess { descartados++ }
                    continue
                }
                // Ya registrado (mismo tipo y monto, mismo día o el siguiente): se descarta solo.
                if (InterpreteCorreo.buscarDuplicado(m, existentes, dias = 1, exacto = true) != null) {
                    correosProcesados.add(c.id)
                    runCatching { nube.quitarDeBandeja(c.id) }.onSuccess { descartados++ }
                    continue
                }
                if (!preferencias.autoCorreos.value) continue
                // Parecido a algo ya anotado (mismo monto, días cercanos): que lo revises tú.
                if (InterpreteCorreo.buscarDuplicado(m, existentes) != null) continue
                val (p, conocido) = construirPropuesta(m)
                val medio = p.medioId ?: continue
                val listo = conocido && (p.tipo == TipoMovimiento.TRASLADO && p.medioDestinoId != null ||
                    p.tipo != TipoMovimiento.TRASLADO && p.categoriaId != null)
                if (!listo) continue
                correosProcesados.add(c.id)
                try {
                    finanzas.guardarMovimiento(
                        null, p.tipo, p.monto ?: continue, p.categoriaId ?: 0L, medio,
                        Formato.aMillis(p.fecha), p.nota, null, null, null, p.medioDestinoId
                    )
                    nube.quitarDeBandeja(c.id)
                    existentes.add(
                        com.mipropiobanco.data.local.MovimientoEntity(
                            tipo = p.tipo.name, monto = p.monto ?: 0L, categoriaId = p.categoriaId ?: 0L,
                            medioId = medio, fecha = Formato.aMillis(p.fecha), nota = p.nota,
                            medioDestinoId = p.medioDestinoId
                        )
                    )
                    registrados++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            if (registrados > 0 || descartados > 0) {
                _mensajes.emit(
                    listOfNotNull(
                        "📥 $registrados registrados solos".takeIf { registrados > 0 },
                        "$descartados duplicados descartados".takeIf { descartados > 0 }
                    ).joinToString(" · ")
                )
            }
          } finally {
            candadoCorreos.unlock()
          }
        }
    }

    /** Cuenta qué limpiaría: movimientos repetidos y traslados a efectivo. */
    fun revisarLimpieza(alListo: (com.mipropiobanco.data.repository.FinanzasRepository.Limpieza) -> Unit) {
        viewModelScope.launch { alListo(finanzas.revisarLimpieza()) }
    }

    fun aplicarLimpieza() = ejecutar(
        accion = { finanzas.aplicarLimpieza() },
        mensaje = { (borrados, convertidos) ->
            "🧹 $borrados duplicados borrados · $convertidos retiros pasados a gasto"
        }
    )

    /** Guarda el movimiento del correo, recuerda cómo se clasificó y lo saca de la bandeja. */
    fun guardarDesdeCorreo(
        propuesta: Propuesta,
        tipo: TipoMovimiento,
        monto: Long,
        categoriaId: Long,
        medioId: Long,
        fecha: Long,
        nota: String,
        deudaId: Long?,
        medioDestinoId: Long?
    ) = ejecutar(
        accion = {
            finanzas.guardarMovimiento(null, tipo, monto, categoriaId, medioId, fecha, nota, null, null, null, medioDestinoId)
            propuesta.patronRegla?.let { finanzas.recordarRegla(it, tipo, categoriaId, medioDestinoId) }
            propuesta.idBandeja?.let { nube.quitarDeBandeja(it) }
            if (deudaId != null) repo.abonarDeuda(deudaId, monto, fecha)
        },
        mensaje = { "${tipo.etiqueta} de ${Formato.cop(monto)} registrado desde tu correo" }
    )

    fun descartarFoto(ruta: String?) {
        ruta ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val enUso = finanzas.fotoEnUso(ruta)
            if (!enUso) runCatching { File(ruta).delete() }
        }
    }

    fun eliminarMovimiento(m: MovimientoEntity) = ejecutar(
        accion = { finanzas.eliminarMovimiento(m) },
        mensaje = { "Movimiento eliminado" }
    )

    fun guardarCategoria(id: Long?, nombre: String, tipo: TipoMovimiento, icono: String, presupuesto: Long) = ejecutar(
        accion = { finanzas.guardarCategoria(id, nombre, tipo, icono, presupuesto) },
        mensaje = { "Categoría guardada" }
    )

    fun eliminarCategoria(c: CategoriaEntity) = ejecutar(
        accion = { finanzas.eliminarCategoria(c) },
        mensaje = { "Categoría eliminada" }
    )

    fun guardarMedio(id: Long?, nombre: String, icono: String, saldoActual: Long? = null) = ejecutar(
        accion = { finanzas.guardarMedio(id, nombre, icono, saldoActual) },
        mensaje = { "Medio de pago guardado" }
    )

    fun ajustarSaldos(saldosHoy: Map<Long, Long>, comoMovimiento: Boolean) = ejecutar(
        accion = { finanzas.ajustarSaldos(saldosHoy, comoMovimiento) },
        mensaje = { "💰 Saldos actualizados" }
    )

    fun unirEnBancolombia() = ejecutar(
        accion = { finanzas.unirEnBancolombia() },
        mensaje = { u ->
            "🔗 ${u.movidos} movimientos pasados a Bancolombia" +
                (if (u.ajustesBorrados > 0) " · ${u.ajustesBorrados} ajustes anteriores borrados" else "") +
                ". Ahora pon tu saldo real en ✏️ Ajustar."
        }
    )

    val fechaInicio: StateFlow<Long> = preferencias.fechaInicio

    fun empezarDesde(inicio: Long) = ejecutar(
        accion = {
            val n = finanzas.empezarDesde(inicio)
            preferencias.cambiarFechaInicio(inicio)
            n
        },
        mensaje = { n -> "📅 Cuentas desde el ${Formato.fecha(inicio)} · $n movimientos anteriores borrados" }
    )

    fun eliminarMedio(m: MedioPagoEntity) = ejecutar(
        accion = { finanzas.eliminarMedio(m) },
        mensaje = { "Medio de pago eliminado" }
    )

    fun guardarPlantilla(
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
    ) = ejecutar(
        accion = {
            finanzas.guardarPlantilla(id, nombre, tipo, monto, categoriaId, medioId, periodicidad, fechaInicio, automatica, activa)
            finanzas.procesarAutomaticas()
        },
        mensaje = { n -> if (n > 0) "Plantilla guardada · $n movimientos registrados" else "Plantilla guardada" }
    )

    fun eliminarPlantilla(p: PlantillaEntity) = ejecutar(
        accion = { finanzas.eliminarPlantilla(p) },
        mensaje = { "Plantilla eliminada (los movimientos ya registrados se conservan)" }
    )

    fun registrarOcurrencia(o: Ocurrencia, monto: Long) = ejecutar(
        accion = { finanzas.registrarOcurrencia(o, monto) },
        mensaje = { "«${o.plantilla.nombre}» registrado" }
    )

    fun omitirOcurrencia(o: Ocurrencia) = ejecutar(
        accion = { finanzas.omitirOcurrencia(o) },
        mensaje = { "«${o.plantilla.nombre}» omitido esta vez" }
    )

    // ---------- Cuenta y nube ----------

    val correo: StateFlow<String?> = nube.correo
    val estadoNube: StateFlow<EstadoNube> = nube.estado
    val modoLocal: StateFlow<Boolean> = preferencias.modoLocal

    private val _procesandoCuenta = MutableStateFlow(false)
    val procesandoCuenta: StateFlow<Boolean> = _procesandoCuenta.asStateFlow()

    private fun accionCuenta(exito: String, accion: suspend () -> Unit) {
        if (_procesandoCuenta.value) return
        viewModelScope.launch {
            _procesandoCuenta.value = true
            try {
                accion()
                _mensajes.emit(exito)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensajes.emit(SincronizacionNube.mensajeError(e))
            } finally {
                _procesandoCuenta.value = false
            }
        }
    }

    fun iniciarSesion(correo: String, clave: String) =
        accionCuenta("Sesión iniciada: tus datos se guardan en la nube") { nube.iniciarSesion(correo, clave) }

    fun registrar(correo: String, clave: String) =
        accionCuenta("Cuenta creada: tus datos se guardan en la nube") { nube.registrar(correo, clave) }

    fun recuperarClave(correo: String) =
        accionCuenta("Te enviamos un correo para cambiar la contraseña") { nube.recuperarClave(correo) }

    fun cerrarSesion() =
        accionCuenta("Sesión cerrada. Tus datos quedaron guardados en la nube") { nube.cerrarSesion() }

    fun sincronizarAhora() = accionCuenta("Sincronización terminada") { nube.subirSiHaySesion() }

    fun continuarSinCuenta() = preferencias.cambiarModoLocal(true)

    fun irAIniciarSesion() = preferencias.cambiarModoLocal(false)

    val resumen: StateFlow<ResumenBanco> =
        repo.resumen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenBanco())

    val aportes: StateFlow<List<AporteEntity>> =
        repo.aportes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tipos: StateFlow<List<TipoPrestamoEntity>> =
        repo.tipos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prestamos: StateFlow<List<PrestamoConPagos>> =
        repo.prestamosConPagos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deudas: StateFlow<List<DeudaConAbonos>> =
        repo.deudas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presupuestoDeudas: StateFlow<Long> = preferencias.presupuestoDeudas

    val planDeudas: StateFlow<PlanBolaNieve> =
        combine(repo.deudas, preferencias.presupuestoDeudas) { d, p -> BolaDeNieve.planificar(d, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BolaDeNieve.planificar(emptyList(), 0L))

    val modoTema: StateFlow<ModoTema> = preferencias.modoTema

    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val mensajes: SharedFlow<String> = _mensajes.asSharedFlow()

    private fun <T> ejecutar(
        accion: suspend () -> T,
        mensaje: (T) -> String,
        alTerminar: (T) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resultado = accion()
                _mensajes.emit(mensaje(resultado))
                alTerminar(resultado)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensajes.emit(e.message ?: "Ocurrió un error inesperado")
            }
        }
    }

    // ---------- Aportes ----------

    fun registrarAporte(fecha: Long, concepto: String, monto: Long) = ejecutar(
        accion = { repo.registrarAporte(fecha, concepto, monto) },
        mensaje = { "Aporte de ${Formato.cop(monto)} guardado en la bóveda" }
    )

    fun eliminarAporte(aporte: AporteEntity) = ejecutar(
        accion = { repo.eliminarAporte(aporte) },
        mensaje = { "Aporte eliminado" }
    )

    // ---------- Tipos de préstamo ----------

    fun guardarTipo(id: Long?, nombre: String, tasaEA: Double, tasaMoraEA: Double) = ejecutar(
        accion = { repo.guardarTipo(id, nombre, tasaEA, tasaMoraEA) },
        mensaje = { "Tipo de préstamo guardado" }
    )

    fun eliminarTipo(tipo: TipoPrestamoEntity) = ejecutar(
        accion = { repo.eliminarTipo(tipo) },
        mensaje = { "Tipo «${tipo.nombre}» eliminado" }
    )

    // ---------- Autopréstamos ----------

    fun crearPrestamo(
        concepto: String,
        monto: Long,
        cuotas: Int,
        periodicidad: Periodicidad,
        tipo: String,
        tasaEA: Double,
        tasaMoraEA: Double,
        fechaDesembolso: Long,
        alCrear: (Long) -> Unit
    ) = ejecutar(
        accion = { repo.crearPrestamo(concepto, monto, cuotas, periodicidad, tipo, tasaEA, tasaMoraEA, fechaDesembolso) },
        mensaje = { "Autopréstamo aprobado: salieron ${Formato.cop(monto)} de la bóveda" },
        alTerminar = alCrear
    )

    fun pagarCuota(prestamoId: Long, fechaPago: Long) = ejecutar(
        accion = { repo.registrarPagoCuota(prestamoId, fechaPago) },
        mensaje = { r ->
            val mora = if (r.pago.mora > 0L) " + mora ${Formato.cop(r.pago.mora)} (${r.pago.diasAtraso} días)" else ""
            if (r.prestamoLiquidado) {
                "¡Préstamo pagado por completo! Cuota ${r.pago.numeroCuota} registrada$mora"
            } else {
                "Cuota ${r.pago.numeroCuota}: ${Formato.cop(r.pago.capital)} capital + " +
                    "${Formato.cop(r.pago.interes)} interés$mora a la bóveda"
            }
        }
    )

    fun deshacerUltimoPago(prestamoId: Long) = ejecutar(
        accion = { repo.deshacerUltimoPago(prestamoId) },
        mensaje = { "Último pago deshecho" }
    )

    fun anularPrestamo(prestamoId: Long, alAnular: () -> Unit) = ejecutar(
        accion = { repo.anularPrestamo(prestamoId) },
        mensaje = { "Préstamo anulado: el dinero regresó a la bóveda" },
        alTerminar = { alAnular() }
    )

    // ---------- Deudas ----------

    fun cambiarPresupuestoDeudas(valor: Long) = preferencias.cambiarPresupuestoDeudas(valor)

    fun guardarDeuda(
        id: Long?,
        acreedor: String,
        saldo: Long,
        pagoMinimo: Long,
        tasaEA: Double,
        diaPago: Int,
        alGuardar: () -> Unit = {}
    ) = ejecutar(
        accion = { repo.guardarDeuda(id, acreedor, saldo, pagoMinimo, tasaEA, diaPago) },
        mensaje = { if (id == null) "Deuda con $acreedor agregada" else "Deuda actualizada" },
        alTerminar = { alGuardar() }
    )

    fun eliminarDeuda(deudaId: Long, alEliminar: () -> Unit) = ejecutar(
        accion = { repo.eliminarDeuda(deudaId) },
        mensaje = { "Deuda eliminada" },
        alTerminar = { alEliminar() }
    )

    /**
     * Registra un pago a una deuda. Si [comoGasto] es true, también lo anota en Gastos
     * (categoría "Pago de deudas") para que cuadre el presupuesto del mes.
     */
    fun abonarDeuda(deudaId: Long, monto: Long, fecha: Long, comoGasto: Boolean = false) = ejecutar(
        accion = {
            val antes = deudas.value.firstOrNull { it.deuda.id == deudaId }
            val restante = repo.abonarDeuda(deudaId, monto, fecha)
            if (comoGasto) {
                val categoria = finanzas.categoriaPagoDeudas()
                val medio = finanzas.medioPorDefecto()
                if (categoria != null && medio != null) {
                    val aplicado = antes?.let { minOf(monto, it.saldo) } ?: monto
                    finanzas.guardarMovimiento(
                        id = null, tipo = TipoMovimiento.GASTO, monto = aplicado, categoriaId = categoria.id,
                        medioId = medio.id, fecha = fecha, nota = "Pago a ${antes?.deuda?.acreedor ?: "deuda"}", repetir = null
                    )
                }
            }
            restante
        },
        mensaje = { saldo ->
            if (saldo == 0L) "¡Deuda liquidada! Su pago ahora se suma a la siguiente (bola de nieve)"
            else "Pago registrado. Saldo pendiente: ${Formato.cop(saldo)}"
        }
    )

    fun eliminarAbono(abono: AbonoDeudaEntity) = ejecutar(
        accion = { repo.eliminarAbono(abono) },
        mensaje = { "Pago eliminado" }
    )

    // ---------- Ajustes y respaldo ----------

    fun cambiarModoTema(modo: ModoTema) = preferencias.cambiarModoTema(modo)

    fun exportarJson(uri: Uri) = ejecutar(
        accion = { escribir(uri, respaldo.exportarJson()) },
        mensaje = { "Respaldo JSON exportado" }
    )

    fun exportarCsv(uri: Uri) = ejecutar(
        accion = { escribir(uri, respaldo.exportarCsv()) },
        mensaje = { "Movimientos exportados en CSV" }
    )

    fun importarJson(uri: Uri) = ejecutar(
        accion = { respaldo.importarJson(leer(uri)) },
        mensaje = { it }
    )

    init {
        // Correos del banco ya conocidos: se registran solos en cuanto llegan.
        viewModelScope.launch { bandeja.collect { registrarCorreosConocidos(it) } }
        // Al abrir la app se registran solas las plantillas automáticas que ya vencieron.
        viewModelScope.launch {
            try {
                val n = finanzas.procesarAutomaticas()
                if (n > 0) _mensajes.emit("Se registraron $n movimientos fijos automáticos")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun escribir(uri: Uri, texto: String) = withContext(Dispatchers.IO) {
        val salida = app.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("No se pudo crear el archivo")
        salida.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun leer(uri: Uri): String = withContext(Dispatchers.IO) {
        val entrada = app.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        entrada.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
