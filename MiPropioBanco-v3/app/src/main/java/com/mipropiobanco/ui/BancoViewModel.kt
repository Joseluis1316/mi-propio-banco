package com.mipropiobanco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mipropiobanco.MiPropioBancoApp
import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.TipoPrestamoEntity
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
