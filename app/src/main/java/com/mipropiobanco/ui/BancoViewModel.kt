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
import com.mipropiobanco.util.Fotos
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import com.mipropiobanco.data.prefs.ModoTema
import com.mipropiobanco.data.sync.EstadoNube
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
        foto: String? = null
    ) = ejecutar(
        accion = { finanzas.guardarMovimiento(id, tipo, monto, categoriaId, medioId, fecha, nota, repetir, plantillaIdExistente, foto) },
        mensaje = {
            when {
                id != null -> "Movimiento actualizado"
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
    fun leerRecibo(origen: Uri, temporal: File?, alListo: (Propuesta) -> Unit) {
        if (_leyendoRecibo.value) return
        viewModelScope.launch {
            _leyendoRecibo.value = true
            try {
                val ruta = withContext(Dispatchers.IO) { Fotos.guardarReducida(app, origen) }
                val texto = leerTexto(ruta)
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
            return reconocedor.process(imagen).await().text
        } finally {
            reconocedor.close()
        }
    }

    /** Si el usuario cancela, se borra la foto que no se usó. */
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

    fun guardarMedio(id: Long?, nombre: String, icono: String) = ejecutar(
        accion = { finanzas.guardarMedio(id, nombre, icono) },
        mensaje = { "Medio de pago guardado" }
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

    fun abonarDeuda(deudaId: Long, monto: Long, fecha: Long) = ejecutar(
        accion = { repo.abonarDeuda(deudaId, monto, fecha) },
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
