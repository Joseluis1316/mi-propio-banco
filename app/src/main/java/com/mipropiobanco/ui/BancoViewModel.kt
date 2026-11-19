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
import kotlinx.coroutines.flow.map
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
            if (id == null) crearDeudaSiEsPrestamo(tipo, monto, categoriaId, fecha, nota) else null
        },
        mensaje = { acreedor ->
            when {
                acreedor != null -> "🤝 Ingreso guardado y deuda con $acreedor creada (ajústala en Deudas)"
                id != null -> "Movimiento actualizado"
                tipo == TipoMovimiento.TRASLADO -> "Traslado de ${Formato.cop(monto)} registrado"
                repetir != null -> "${tipo.etiqueta} guardado y plantilla fija creada"
                else -> "${tipo.etiqueta} de ${Formato.cop(monto)} guardado"
            }
        }
    )

    /**
     * Si el ingreso es de la categoría "Préstamos recibidos", esa plata hay que devolverla:
     * se crea la deuda con esa persona por el mismo monto. Devuelve a quién, o null.
     */
    private suspend fun crearDeudaSiEsPrestamo(tipo: TipoMovimiento, monto: Long, categoriaId: Long, fecha: Long, nota: String): String? {
        if (tipo != TipoMovimiento.INGRESO || monto <= 0L) return null
        val cat = finanzas.categorias.first().firstOrNull { it.id == categoriaId } ?: return null
        val n = Interprete.normalizar(cat.nombre)
        if (!(n.contains("prestamo") && n.contains("recib"))) return null
        val acreedor = nota
            .replace(Regex("(?i)^(transferencia (desde|de)|pago de|recibiste( un pago)? de|prestamo de|préstamo de)\\s*"), "")
            .trim().ifBlank { "Préstamo recibido" }.take(40)
        val dia = Formato.aLocalDate(fecha).dayOfMonth.coerceIn(1, 28)
        repo.guardarDeuda(null, acreedor, monto, monto, 0.0, dia)
        return acreedor
    }

    // ---------- Extracto: varios movimientos en una sola captura ----------

    private val _extracto = MutableStateFlow<List<com.mipropiobanco.domain.FilaExtracto>>(emptyList())
    /** Movimientos leídos de una captura del extracto, esperando que los confirmes. */
    val extracto: StateFlow<List<com.mipropiobanco.domain.FilaExtracto>> = _extracto.asStateFlow()

    fun descartarExtracto() { _extracto.value = emptyList() }

    /** ¿Ya está anotado (mismo monto y tipo, ±1 día)? */
    fun yaRegistrado(f: com.mipropiobanco.domain.FilaExtracto): Boolean {
        val tipos = if (f.salida) setOf(TipoMovimiento.GASTO.name, TipoMovimiento.TRASLADO.name) else setOf(TipoMovimiento.INGRESO.name)
        return movimientos.value.any {
            it.monto == f.monto && it.tipo in tipos &&
                kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(Formato.aLocalDate(it.fecha), f.fecha)) <= 1
        }
    }

    /** Guarda las filas elegidas. [acreedores]: nombre de quien prestó, para las filas de préstamo. */
    fun guardarExtracto(
        filas: List<com.mipropiobanco.domain.FilaExtracto>,
        destinos: List<com.mipropiobanco.domain.DestinoFila>,
        acreedores: List<String>
    ) = ejecutar(
        accion = {
            guardarPunto("guardar movimientos del extracto")
            val cats = finanzas.categorias.first()
            val medio = finanzas.medioPorDefecto() ?: throw IllegalStateException("Crea primero un medio de pago")
            var guardados = 0
            var deudas = 0
            filas.forEachIndexed { i, f ->
                val destino = destinos.getOrNull(i) ?: com.mipropiobanco.domain.DestinoFila.OMITIR
                val fecha = Formato.aMillis(f.fecha)
                val t = Interprete.normalizar(f.descripcion)
                when (destino) {
                    com.mipropiobanco.domain.DestinoFila.GASTO -> {
                        val cat = Interprete.detectarCategoria(t, TipoMovimiento.GASTO, cats)?.id ?: finanzas.categoriaPorClasificar()
                        finanzas.guardarMovimiento(null, TipoMovimiento.GASTO, f.monto, cat, medio.id, fecha, f.descripcion.take(80), null)
                        guardados++
                    }
                    com.mipropiobanco.domain.DestinoFila.INGRESO -> {
                        val cat = Interprete.detectarCategoria(t, TipoMovimiento.INGRESO, cats)?.id ?: finanzas.categoriaOtrosIngresos()
                        finanzas.guardarMovimiento(null, TipoMovimiento.INGRESO, f.monto, cat, medio.id, fecha, f.descripcion.take(80), null)
                        guardados++
                    }
                    com.mipropiobanco.domain.DestinoFila.PRESTAMO -> {
                        val quien = acreedores.getOrNull(i)?.trim().orEmpty().ifBlank { f.descripcion.take(40) }
                        finanzas.guardarMovimiento(
                            null, TipoMovimiento.INGRESO, f.monto, finanzas.categoriaPrestamosRecibidos(), medio.id, fecha,
                            "Préstamo de $quien", null
                        )
                        repo.guardarDeuda(null, quien, f.monto, f.monto, 0.0, f.fecha.dayOfMonth.coerceIn(1, 28))
                        guardados++
                        deudas++
                    }
                    com.mipropiobanco.domain.DestinoFila.OMITIR -> Unit
                }
            }
            _extracto.value = emptyList()
            guardados to deudas
        },
        mensaje = { (g, d) ->
            "📋 $g movimientos guardados" + if (d > 0) " · $d deudas creadas en Deudas" else ""
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
                // Captura de la lista de movimientos del banco: uno por uno.
                val filas = com.mipropiobanco.domain.InterpreteExtracto.interpretar(texto)
                if (filas.size >= 2) {
                    _extracto.value = filas
                    _mensajes.emit("📋 Encontré ${filas.size} movimientos en la imagen: revísalos uno por uno")
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
        // Notificaciones de Nequi / Daviplata: el movimiento es de esa billetera.
        val medioApp = when {
            m.id.startsWith("nequi_") -> medioCon("nequi")
            m.id.startsWith("daviplata_") -> medioCon("daviplata")
            else -> null
        }
        val medioTarjeta = medioApp ?: when (m.tarjeta) {
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

    val urlScript: StateFlow<String> = preferencias.urlScript
    fun cambiarUrlScript(valor: String) = preferencias.cambiarUrlScript(valor)

    private val _buscandoCorreos = kotlinx.coroutines.flow.MutableStateFlow(false)
    val buscandoCorreos: StateFlow<Boolean> = _buscandoCorreos

    /**
     * Le pide al script de Gmail que revise los correos ahora (sin esperar los 15 minutos).
     * todos = true vuelve a enviar los correos de los últimos 60 días.
     */
    fun buscarCorreosAhora(todos: Boolean = false) {
        val base = preferencias.urlScript.value
        if (base.isBlank()) {
            viewModelScope.launch { _mensajes.emit("Primero pega el enlace del script en Ajustes › Correos de Bancolombia") }
            return
        }
        if (_buscandoCorreos.value) return
        _buscandoCorreos.value = true
        viewModelScope.launch {
            try {
                val respuesta = withContext(Dispatchers.IO) {
                    val sep = if (base.contains("?")) "&" else "?"
                    val url = java.net.URL(base + sep + "uid=" + (nube.uid ?: "") + if (todos) "&todo=1" else "")
                    val con = url.openConnection() as java.net.HttpURLConnection
                    con.instanceFollowRedirects = true
                    con.connectTimeout = 20_000
                    con.readTimeout = 90_000
                    try {
                        val codigo = con.responseCode
                        val texto = (if (codigo < 400) con.inputStream else con.errorStream)
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        codigo to texto
                    } finally {
                        con.disconnect()
                    }
                }
                val (codigo, texto) = respuesta
                _mensajes.emit(
                    when {
                        codigo < 300 && texto.startsWith("OK") -> "🔄 Correos revisados: ${texto.removePrefix("OK").trim().ifBlank { "listo" }}"
                        codigo < 300 -> "El enlace no es el del script. Revisa que copiaste la URL de la implementación (termina en /exec)."
                        else -> "No se pudo revisar los correos (error $codigo)"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensajes.emit("Sin conexión con el script: ${e.message ?: "revisa tu internet"}")
            } finally {
                _buscandoCorreos.value = false
            }
        }
    }

    val gastosSolos: StateFlow<Boolean> = preferencias.gastosSolos
    fun cambiarGastosSolos(valor: Boolean) {
        preferencias.cambiarGastosSolos(valor)
        if (valor) {
            correosProcesados.clear()
            registrarCorreosConocidos(bandeja.value)
        }
    }

    val conectarBanco: StateFlow<Boolean> = preferencias.conectarBanco
    fun cambiarConectarBanco(valor: Boolean) = preferencias.cambiarConectarBanco(valor)

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
                var categoriaFinal = p.categoriaId
                var listo = conocido && (p.tipo == TipoMovimiento.TRASLADO && p.medioDestinoId != null ||
                    p.tipo != TipoMovimiento.TRASLADO && categoriaFinal != null)
                // Toda salida de plata va a gastos de una vez; la categoría se pone después.
                if (!listo && p.tipo == TipoMovimiento.GASTO && preferencias.gastosSolos.value) {
                    if (categoriaFinal == null) categoriaFinal = finanzas.categoriaPorClasificar()
                    listo = true
                }
                if (!listo) continue
                correosProcesados.add(c.id)
                try {
                    finanzas.guardarMovimiento(
                        null, p.tipo, p.monto ?: continue, categoriaFinal ?: 0L, medio,
                        Formato.aMillis(p.fecha), p.nota, null, null, null, p.medioDestinoId
                    )
                    nube.quitarDeBandeja(c.id)
                    existentes.add(
                        com.mipropiobanco.data.local.MovimientoEntity(
                            tipo = p.tipo.name, monto = p.monto ?: 0L, categoriaId = categoriaFinal ?: 0L,
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
        accion = { guardarPunto("limpieza de duplicados"); finanzas.aplicarLimpieza() },
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
            crearDeudaSiEsPrestamo(tipo, monto, categoriaId, fecha, nota)
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
        accion = { guardarPunto("borrar ${Formato.cop(m.monto)} ${m.nota.take(20)}".trim()); finanzas.eliminarMovimiento(m) },
        mensaje = { "Movimiento eliminado (↩️ Deshacer en Gastos)" }
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
        accion = { guardarPunto("ajuste de saldos"); finanzas.ajustarSaldos(saldosHoy, comoMovimiento) },
        mensaje = { "💰 Saldos actualizados (puedes deshacerlo en Gastos)" }
    )

    fun unirEnBancolombia() = ejecutar(
        accion = { guardarPunto("unir cuentas en Bancolombia"); finanzas.unirEnBancolombia() },
        mensaje = { u ->
            "🔗 ${u.movidos} movimientos pasados a Bancolombia" +
                (if (u.ajustesBorrados > 0) " · ${u.ajustesBorrados} ajustes anteriores borrados" else "") +
                ". Ahora pon tu saldo real en ✏️ Ajustar."
        }
    )

    val fechaInicio: StateFlow<Long> = preferencias.fechaInicio

    fun empezarDesde(inicio: Long) = ejecutar(
        accion = {
            // Ya no borra nada: solo decide desde qué fecha se aceptan los correos.
            preferencias.cambiarFechaInicio(inicio)
            correosProcesados.clear()
            registrarCorreosConocidos(bandeja.value)
        },
        mensaje = { "📅 Correos desde el ${Formato.fecha(inicio)}. Tus movimientos no se tocaron." }
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
        accion = {
            repo.registrarAporte(fecha, concepto, monto)
            if (preferencias.conectarBanco.value) {
                finanzas.anotarDeMiBanco(TipoMovimiento.GASTO, monto, fecha, "Aporte: $concepto", "Ahorro a Mi banco")
            }
        },
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
        accion = {
            val id = repo.crearPrestamo(concepto, monto, cuotas, periodicidad, tipo, tasaEA, tasaMoraEA, fechaDesembolso)
            if (preferencias.conectarBanco.value) {
                finanzas.anotarDeMiBanco(TipoMovimiento.INGRESO, monto, fechaDesembolso, "Autopréstamo: $concepto", "Préstamo de Mi banco")
            }
            id
        },
        mensaje = { "Autopréstamo aprobado: salieron ${Formato.cop(monto)} de la bóveda" },
        alTerminar = alCrear
    )

    fun pagarCuota(prestamoId: Long, fechaPago: Long) = ejecutar(
        accion = {
            val r = repo.registrarPagoCuota(prestamoId, fechaPago)
            if (preferencias.conectarBanco.value) {
                val total = r.pago.capital + r.pago.interes + r.pago.mora
                finanzas.anotarDeMiBanco(TipoMovimiento.GASTO, total, fechaPago, "Cuota ${r.pago.numeroCuota} a Mi banco", "Cuota a Mi banco")
            }
            r
        },
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

    // ---------- Deshacer (copia automática antes de cambios grandes) ----------

    private val archivoDeshacer get() = File(app.filesDir, "deshacer.json")
    private val archivoMotivo get() = File(app.filesDir, "deshacer.txt")

    private val _puntoDeshacer = kotlinx.coroutines.flow.MutableStateFlow(
        runCatching { if (archivoDeshacer.exists()) archivoMotivo.readText() else null }.getOrNull()
    )
    /** Qué se puede deshacer (texto), o null si no hay nada. */
    val puntoDeshacer: StateFlow<String?> = _puntoDeshacer

    /** Guarda una copia completa de los datos antes de un cambio que no se puede revertir a mano. */
    private suspend fun guardarPunto(motivo: String) {
        val json = respaldo.exportarJson()
        withContext(Dispatchers.IO) {
            archivoDeshacer.writeText(json)
            archivoMotivo.writeText(motivo)
        }
        _puntoDeshacer.value = motivo
    }

    fun deshacer() = ejecutar(
        accion = {
            val texto = withContext(Dispatchers.IO) { archivoDeshacer.takeIf { it.exists() }?.readText() }
                ?: throw IllegalStateException("No hay nada para deshacer")
            respaldo.importarJson(texto)
            withContext(Dispatchers.IO) { archivoDeshacer.delete(); archivoMotivo.delete() }
            _puntoDeshacer.value = null
        },
        mensaje = { "↩️ Listo, se recuperó cómo estaba antes" }
    )

    // ---------- Metas de ahorro ----------

    val metas: StateFlow<List<com.mipropiobanco.domain.Meta>> = preferencias.metasJson
        .map { com.mipropiobanco.domain.MetasJson.leer(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun guardarMetas(lista: List<com.mipropiobanco.domain.Meta>) {
        preferencias.guardarMetasJson(com.mipropiobanco.domain.MetasJson.escribir(lista))
        preferencias.marcarModificado()
    }

    fun guardarMeta(id: Long?, nombre: String, icono: String, objetivo: Long, fechaLimite: Long) {
        if (nombre.isBlank() || objetivo <= 0L) {
            viewModelScope.launch { _mensajes.emit("Escribe el nombre y cuánto quieres ahorrar") }
            return
        }
        val actual = metas.value
        val nueva = if (id == null) {
            actual + com.mipropiobanco.domain.Meta(
                id = System.currentTimeMillis(), nombre = nombre.trim(), icono = icono.ifBlank { "🎯" },
                objetivo = objetivo, ahorrado = 0L, fechaLimite = fechaLimite
            )
        } else {
            actual.map {
                if (it.id == id) it.copy(nombre = nombre.trim(), icono = icono.ifBlank { "🎯" }, objetivo = objetivo, fechaLimite = fechaLimite)
                else it
            }
        }
        guardarMetas(nueva)
        viewModelScope.launch { _mensajes.emit(if (id == null) "🎯 Meta creada" else "Meta actualizada") }
    }

    /** Suma (o resta, si es negativo) plata a una meta. Si [desdeCuenta], sale de Bancolombia como gasto de ahorro. */
    fun abonarMeta(id: Long, monto: Long, desdeCuenta: Boolean) {
        if (monto <= 0L) {
            viewModelScope.launch { _mensajes.emit("Escribe cuánto vas a abonar") }
            return
        }
        val meta = metas.value.firstOrNull { it.id == id } ?: return
        val nuevo = (meta.ahorrado + monto).coerceAtLeast(0L)
        guardarMetas(metas.value.map { if (it.id == id) it.copy(ahorrado = nuevo) else it })
        viewModelScope.launch {
            if (desdeCuenta && monto > 0L) {
                runCatching {
                    finanzas.anotarDeMiBanco(TipoMovimiento.GASTO, monto, Formato.hoyMillis(), "Ahorro: ${meta.nombre}", "Ahorro para metas")
                }
            }
            _mensajes.emit(
                if (meta.objetivo in 1..nuevo) "🎉 ¡Cumpliste la meta ${meta.nombre}!"
                else "${meta.icono} ${meta.nombre}: llevas ${Formato.cop(nuevo)} de ${Formato.cop(meta.objetivo)}"
            )
        }
    }

    fun eliminarMeta(id: Long) {
        guardarMetas(metas.value.filterNot { it.id == id })
    }

    // ---------- Reporte del mes en PDF ----------

    fun exportarReporte(uri: Uri, mes: java.time.YearMonth) = ejecutar(
        accion = {
            val movs = finanzas.movimientos.first()
            val cats = finanzas.categorias.first()
            val meds = finanzas.medios.first()
            val deudasActivas = repo.deudas.first().filter { !it.pagada }
            val banco = repo.resumen.first()
            withContext(Dispatchers.IO) {
                val salida = app.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo crear el archivo")
                salida.use {
                    com.mipropiobanco.util.ReportePdf.escribir(
                        it, mes, movs, cats, meds,
                        deudasActivas.sumOf { d -> d.saldo }, banco.capitalTotal, metas.value
                    )
                }
            }
        },
        mensaje = { "📄 Reporte de ${Formato.mesAnio(mes)} guardado" }
    )

    // ---------- Avisos y notificaciones ----------

    val avisos: StateFlow<Boolean> = preferencias.avisos
    fun cambiarAvisos(valor: Boolean) {
        preferencias.cambiarAvisos(valor)
        if (valor) runCatching { com.mipropiobanco.avisos.Recordatorios.programar(app) }
    }

    val leerNotificaciones: StateFlow<Boolean> = preferencias.leerNotificaciones
    fun cambiarLeerNotificaciones(valor: Boolean) = preferencias.cambiarLeerNotificaciones(valor)

    /** ¿El usuario ya le dio a la app permiso de leer notificaciones en Ajustes de Android? */
    fun tieneAccesoNotificaciones(): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    // ---------- Seguridad y privacidad ----------

    val bloqueo: StateFlow<Boolean> = preferencias.bloqueo
    fun cambiarBloqueo(valor: Boolean) = preferencias.cambiarBloqueo(valor)

    val ocultarSaldos: StateFlow<Boolean> = preferencias.ocultarSaldos
    fun cambiarOcultarSaldos(valor: Boolean) {
        preferencias.cambiarOcultarSaldos(valor)
        Formato.ocultar = valor
    }

    // ---------- Respaldos automáticos diarios (en el celular, se guardan los últimos 7) ----------

    private val carpetaRespaldos get() = File(app.filesDir, "respaldos").apply { mkdirs() }

    private val _respaldosAuto = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    /** Nombres de días con copia automática, del más reciente al más viejo (yyyy-MM-dd). */
    val respaldosAuto: StateFlow<List<String>> = _respaldosAuto

    private fun listarRespaldos() {
        _respaldosAuto.value = (carpetaRespaldos.listFiles()?.toList() ?: emptyList())
            .filter { it.name.startsWith("auto_") && it.name.endsWith(".json") }
            .map { it.name.removePrefix("auto_").removeSuffix(".json") }
            .sortedDescending()
    }

    private suspend fun respaldoDiario() {
        val hoy = java.time.LocalDate.now().toString()
        if (preferencias.ultimoRespaldoAuto() == hoy) { listarRespaldos(); return }
        if (respaldo.estaVacio()) { listarRespaldos(); return }
        val json = respaldo.exportarJson()
        withContext(Dispatchers.IO) {
            File(carpetaRespaldos, "auto_$hoy.json").writeText(json)
            (carpetaRespaldos.listFiles()?.toList() ?: emptyList())
                .filter { it.name.startsWith("auto_") }
                .sortedByDescending { it.name }
                .drop(7)
                .forEach { it.delete() }
        }
        preferencias.marcarRespaldoAuto(hoy)
        listarRespaldos()
    }

    fun restaurarRespaldoAuto(dia: String) = ejecutar(
        accion = {
            val archivo = File(carpetaRespaldos, "auto_$dia.json")
            val texto = withContext(Dispatchers.IO) { archivo.takeIf { it.exists() }?.readText() }
                ?: throw IllegalStateException("Esa copia ya no existe")
            guardarPunto("restaurar copia del $dia")
            respaldo.importarJson(texto)
        },
        mensaje = { "Se restauró la copia del $dia (puedes deshacerlo en Gastos)" }
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
        accion = { guardarPunto("importar respaldo"); respaldo.importarJson(leer(uri)) },
        mensaje = { it }
    )

    init {
        Formato.ocultar = preferencias.ocultarSaldos.value
        viewModelScope.launch { runCatching { finanzas.categoriaPrestamosRecibidos() } }
        viewModelScope.launch {
            try { respaldoDiario() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
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
