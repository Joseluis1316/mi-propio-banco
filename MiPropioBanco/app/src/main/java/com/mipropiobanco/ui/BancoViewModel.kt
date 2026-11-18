package com.mipropiobanco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mipropiobanco.MiPropioBancoApp
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.prefs.ModoTema
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BancoViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as MiPropioBancoApp
    private val repo = app.repositorio
    private val respaldo = app.respaldo
    private val preferencias = app.preferencias

    val resumen: StateFlow<ResumenBanco> =
        repo.resumen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenBanco())

    val aportes: StateFlow<List<AporteEntity>> =
        repo.aportes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prestamos: StateFlow<List<PrestamoConPagos>> =
        repo.prestamosConPagos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun registrarAporte(fecha: Long, concepto: String, monto: Long) = ejecutar(
        accion = { repo.registrarAporte(fecha, concepto, monto) },
        mensaje = { "Aporte de ${Formato.cop(monto)} guardado en la bóveda" }
    )

    fun eliminarAporte(aporte: AporteEntity) = ejecutar(
        accion = { repo.eliminarAporte(aporte) },
        mensaje = { "Aporte eliminado" }
    )

    fun crearPrestamo(
        concepto: String,
        monto: Long,
        plazoMeses: Int,
        tasaMensual: Double,
        alCrear: (Long) -> Unit
    ) = ejecutar(
        accion = { repo.crearPrestamo(concepto, monto, plazoMeses, tasaMensual) },
        mensaje = { "Autopréstamo aprobado: salieron ${Formato.cop(monto)} de la bóveda" },
        alTerminar = alCrear
    )

    fun pagarCuota(prestamoId: Long) = ejecutar(
        accion = { repo.registrarPagoCuota(prestamoId) },
        mensaje = { r ->
            if (r.prestamoLiquidado) {
                "¡Préstamo pagado por completo! Cuota ${r.pago.numeroCuota} registrada"
            } else {
                "Cuota ${r.pago.numeroCuota}: ${Formato.cop(r.pago.capital)} de capital y " +
                    "${Formato.cop(r.pago.interes)} de interés volvieron a la bóveda"
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
