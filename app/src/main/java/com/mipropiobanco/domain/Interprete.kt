package com.mipropiobanco.domain

import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.roundToLong

/** Movimiento propuesto por el asistente; el usuario lo revisa y confirma. */
data class Propuesta(
    val tipo: TipoMovimiento,
    val monto: Long?,
    val categoriaId: Long?,
    val medioId: Long?,
    val fecha: LocalDate,
    val nota: String,
    val foto: String? = null,
    val textoOriginal: String = ""
)

/**
 * "IA" local: entiende frases en español colombiano y recibos leídos con OCR.
 * Funciona sin internet y sin costo: reglas + diccionarios de palabras clave.
 */
object Interprete {

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    // ---------------- Diccionarios ----------------

    /** Palabras clave → nombre de categoría (según las categorías iniciales). */
    private val PALABRAS_CATEGORIA: Map<String, List<String>> = mapOf(
        "Mercado" to listOf("mercado", "supermercado", "d1", "ara", "exito", "olimpica", "carulla", "jumbo", "makro", "isimo", "tienda", "fruver", "verdura", "surtimax", "colsubsidio"),
        "Transporte" to listOf("bus", "buseta", "taxi", "uber", "didi", "indriver", "cabify", "metro", "transmilenio", "sitp", "mio", "pasaje", "colectivo", "moto taxi", "mototaxi", "tiquete de bus"),
        "Gasolina" to listOf("gasolina", "tanqueo", "tanquee", "tanquear", "acpm", "terpel", "primax", "texaco", "biomax", "combustible"),
        "Comida fuera" to listOf("almuerzo", "almorce", "desayuno", "desayune", "cena", "comida", "restaurante", "corrientazo", "hamburguesa", "pizza", "empanada", "cafe", "tinto", "domicilio", "rappi", "pollo", "helado", "panaderia", "frisby", "mcdonalds", "kfc", "crepes", "juan valdez", "oma"),
        "Servicios públicos" to listOf("servicios", "luz", "energia", "agua", "acueducto", "gas", "epm", "enel", "codensa", "vanti", "air-e", "afinia", "emcali", "aseo", "alcantarillado"),
        "Celular e internet" to listOf("celular", "plan", "recarga", "internet", "wifi", "claro", "movistar", "tigo", "wom", "etb", "datos", "minutos"),
        "Arriendo" to listOf("arriendo", "renta", "alquiler", "administracion", "inmobiliaria"),
        "Salud" to listOf("medico", "medicamento", "medicina", "drogueria", "farmacia", "eps", "cita", "odontologo", "dentista", "examen", "laboratorio", "pastillas", "cruz verde", "la rebaja", "farmatodo", "copago"),
        "Educación" to listOf("universidad", "semestre", "matricula", "curso", "libro", "colegio", "clase", "diplomado", "iu digital", "platzi", "udemy"),
        "Ropa" to listOf("ropa", "zapatos", "camisa", "pantalon", "tenis", "chaqueta", "jean", "medias", "falabella", "zara", "koaj", "arturo calle"),
        "Ocio" to listOf("cine", "fiesta", "cerveza", "trago", "rumba", "netflix", "spotify", "disney", "concierto", "paseo", "juego", "bar", "discoteca", "youtube premium", "hbo", "prime video"),
        "Pago de deudas" to listOf("cuota", "deuda", "abono", "prestamo", "credito", "tarjeta de credito", "sistecredito", "addi", "meddipay", "credifamilia", "pago de tarjeta", "obligacion"),
        "Viajes de trabajo" to listOf("hotel", "viaje", "vuelo", "avion", "hospedaje", "peaje", "airbnb", "terminal", "avianca", "latam", "wingo"),
        "Mascotas" to listOf("perro", "gato", "veterinario", "veterinaria", "concentrado", "mascota", "purina"),
        "Regalos" to listOf("regalo", "cumpleanos", "detalle", "obsequio"),
        "Salario" to listOf("salario", "sueldo", "nomina", "quincena", "prima", "cesantias"),
        "Independiente / honorarios" to listOf("honorarios", "freelance", "cliente", "proyecto", "consultoria", "cuenta de cobro"),
        "Ventas" to listOf("venta", "vendi", "vendido"),
        "Intereses y rendimientos" to listOf("intereses", "rendimientos", "cdt", "cajita", "dividendos"),
        "Viáticos y reembolsos" to listOf("viaticos", "reembolso", "reintegro", "devolucion"),
        "Regalos recibidos" to listOf("me regalaron", "regalo de")
    )

    /** Palabras clave → nombre del medio de pago (según los medios iniciales). */
    private val PALABRAS_MEDIO: Map<String, List<String>> = mapOf(
        "Efectivo" to listOf("efectivo", "cash", "en billete", "en plata"),
        "Nequi" to listOf("nequi"),
        "Daviplata" to listOf("daviplata"),
        "Bre-B / llave" to listOf("bre-b", "bre b", "breb", "llave"),
        "Tarjeta crédito" to listOf("tarjeta de credito", "credito", "visa", "mastercard", "con la tarjeta"),
        "Tarjeta débito" to listOf("debito", "tarjeta debito", "tarjeta de debito"),
        "Transferencia" to listOf("transferencia", "transferi", "consigne", "consignacion", "consignaron", "pse", "bancolombia", "davivienda", "bbva", "banco de bogota", "nu ", "lulo", "pibank")
    )

    private val PALABRAS_INGRESO = listOf(
        "me pagaron", "me pago", "recibi", "ingreso", "ingresaron", "me consignaron", "me transfirieron",
        "me depositaron", "me entro", "me llego", "cobre", "gane", "me dieron", "me regalaron", "salario", "sueldo",
        "nomina", "vendi", "venta de", "me devolvieron", "reembolso"
    )
    private val PALABRAS_GASTO = listOf("gaste", "pague", "compre", "gasto", "pago de", "me cobraron", "me costo", "salio")

    // ---------------- Montos ----------------

    private val REGEX_MONTO = Regex(
        "(?:\\$\\s*)?(\\d+(?:[.,]\\d+)*)\\s*(millones|millon|palos|palo|mil|lucas|luca|k)?\\b"
    )

    /** "45.000" → 45000; "1,5" → 1.5; "2.300.000" → 2300000. */
    private fun numero(texto: String): Double? {
        val t = texto.trim()
        if (Regex("^\\d{1,3}([.,]\\d{3})+$").matches(t)) return t.replace(".", "").replace(",", "").toDoubleOrNull()
        if (Regex("^\\d{1,3}([.,]\\d{3})+[.,]\\d{1,2}$").matches(t)) {
            return t.substring(0, t.length - 3).replace(".", "").replace(",", "").toDoubleOrNull()
        }
        return t.replace(",", ".").toDoubleOrNull()
    }

    private fun multiplicador(unidad: String?): Double = when (unidad) {
        "millones", "millon", "palos", "palo" -> 1_000_000.0
        "mil", "lucas", "luca", "k" -> 1_000.0
        else -> 1.0
    }

    /** Extrae el primer monto de una frase: "2 millones 300 mil" → 2.300.000; "medio millón" → 500.000. */
    fun extraerMonto(textoNormalizado: String): Long? {
        var t = textoNormalizado
            .replace("medio millon", "500000")
            .replace("un millon", "1 millon")
            .replace("una luca", "1 luca")
            .replace("un palo", "1 palo")
            .replace(Regex("\\bmil quinientos\\b"), "1500")
        // Evita confundir fechas ("el 15 de noviembre") con montos.
        t = t.replace(Regex("\\b(el|dia)\\s+\\d{1,2}(\\s+de\\s+[a-z]+)?"), " ")
        val coincidencias = REGEX_MONTO.findAll(t).mapNotNull { m ->
            val n = numero(m.groupValues[1]) ?: return@mapNotNull null
            val u = m.groupValues[2].ifEmpty { null }
            Triple(m.range, n * multiplicador(u), multiplicador(u))
        }.toList()
        if (coincidencias.isEmpty()) return null
        var total = coincidencias[0].second
        var unidadPrevia = coincidencias[0].third
        var finPrevio = coincidencias[0].first.last
        for (i in 1 until coincidencias.size) {
            val (rango, valor, unidad) = coincidencias[i]
            val entre = t.substring(finPrevio + 1, rango.first).trim()
            // "2 millones 300 mil", "2 millones y 300 mil"
            if (unidad < unidadPrevia && (entre.isEmpty() || entre == "y" || entre == "con")) {
                total += valor
                unidadPrevia = unidad
                finPrevio = rango.last
            } else break
        }
        val resultado = total.roundToLong()
        return if (resultado > 0L) resultado else null
    }

    // ---------------- Categoría, medio, tipo y fecha ----------------

    /** Palabras que aparecen en muchos nombres y no sirven para distinguir. */
    private val GENERICAS = setOf(
        "tarjeta", "cuenta", "banco", "otros", "otras", "gastos", "ingresos", "pago", "pagos", "recibidos", "llave"
    )

    private fun contiene(texto: String, palabra: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(palabra) + "([^a-z0-9]|$)").containsMatchIn(texto)

    fun detectarTipo(t: String): TipoMovimiento? = when {
        PALABRAS_INGRESO.any { contiene(t, it) } -> TipoMovimiento.INGRESO
        PALABRAS_GASTO.any { contiene(t, it) } -> TipoMovimiento.GASTO
        else -> null
    }

    fun detectarCategoria(t: String, tipo: TipoMovimiento, categorias: List<CategoriaEntity>): CategoriaEntity? {
        val delTipo = categorias.filter { it.tipo == tipo.name }
        // 1) Nombre completo de una categoría (incluidas las que creó el usuario).
        delTipo.sortedByDescending { it.nombre.length }.firstOrNull { contiene(t, normalizar(it.nombre)) }?.let { return it }
        // 2) Una palabra distintiva del nombre.
        delTipo.firstOrNull { c ->
            normalizar(c.nombre).split(" ", "/").filter { it.length >= 4 && it !in GENERICAS }.any { contiene(t, it) }
        }?.let { return it }
        // 3) Palabras clave.
        for ((nombre, palabras) in PALABRAS_CATEGORIA) {
            val c = delTipo.firstOrNull { normalizar(it.nombre) == normalizar(nombre) } ?: continue
            if (palabras.any { contiene(t, it) }) return c
        }
        return null
    }

    fun detectarMedio(t: String, medios: List<MedioPagoEntity>): MedioPagoEntity? {
        medios.sortedByDescending { it.nombre.length }.firstOrNull { contiene(t, normalizar(it.nombre)) }?.let { return it }
        for ((nombre, palabras) in PALABRAS_MEDIO) {
            val m = medios.firstOrNull { normalizar(it.nombre) == normalizar(nombre) } ?: continue
            if (palabras.any { contiene(t, it.trim()) }) return m
        }
        medios.firstOrNull { m ->
            normalizar(m.nombre).split(" ", "/").filter { it.length >= 4 && it !in GENERICAS }.any { contiene(t, it) }
        }?.let { return it }
        return null
    }

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
        "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    fun detectarFecha(t: String, hoy: LocalDate): LocalDate {
        if (contiene(t, "antier") || contiene(t, "anteayer") || contiene(t, "antes de ayer")) return hoy.minusDays(2)
        if (contiene(t, "ayer")) return hoy.minusDays(1)
        Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(t)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mes = m.groupValues[2].toInt()
            val a = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: hoy.year
            runCatching { return LocalDate.of(a, mes, d) }
        }
        Regex("\\b(\\d{1,2})\\s+(?:de\\s+)?(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)[a-z.]*\\s+(?:de\\s+|del\\s+)?(\\d{4})").find(t)?.let { m ->
            val mes = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
                .indexOf(m.groupValues[2]) + 1
            runCatching { return LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt()) }
        }
        Regex("\\bel\\s+(\\d{1,2})(?:\\s+de\\s+([a-z]+))?").find(t)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mesNombre = m.groupValues[2]
            val mes = MESES.indexOf(mesNombre).takeIf { it >= 0 }?.plus(1)
            return runCatching {
                if (mes != null) {
                    val f = LocalDate.of(hoy.year, mes, d)
                    if (f.isAfter(hoy)) f.minusYears(1) else f
                } else {
                    val f = hoy.withDayOfMonth(d)
                    if (f.isAfter(hoy)) f.minusMonths(1) else f
                }
            }.getOrDefault(hoy)
        }
        return hoy
    }

    // ---------------- Frases (voz o texto) ----------------

    fun interpretarFrase(
        frase: String,
        categorias: List<CategoriaEntity>,
        medios: List<MedioPagoEntity>,
        hoy: LocalDate = LocalDate.now()
    ): Propuesta {
        val t = normalizar(frase)
        val tipoDetectado = detectarTipo(t)
        // Si no hay verbo claro, decide por la categoría que mejor encaje.
        val tipo = tipoDetectado ?: when {
            detectarCategoria(t, TipoMovimiento.GASTO, categorias) != null -> TipoMovimiento.GASTO
            detectarCategoria(t, TipoMovimiento.INGRESO, categorias) != null -> TipoMovimiento.INGRESO
            else -> TipoMovimiento.GASTO
        }
        return Propuesta(
            tipo = tipo,
            monto = extraerMonto(t),
            categoriaId = detectarCategoria(t, tipo, categorias)?.id,
            medioId = detectarMedio(t, medios)?.id,
            fecha = detectarFecha(t, hoy),
            nota = frase.trim().replaceFirstChar { it.uppercase() }.take(80),
            textoOriginal = frase
        )
    }

    // ---------------- Recibos (texto leído con OCR) ----------------

    private val REGEX_DINERO = Regex("(\\$)?\\s?(?<![\\d*])(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?|\\d{4,9})(?![\\d-])")
    private val PALABRAS_TOTAL = listOf(
        "total a pagar", "valor total", "total pagado", "vr total", "vlr total", "neto a pagar",
        "valor de la transferencia", "valor transferido", "valor enviado", "valor pagado", "valor del pago",
        "monto", "enviaste", "pagaste", "transferiste", "total", "valor"
    )
    /** Líneas que suelen tener números que no son el valor pagado. */
    private val LINEAS_NO_MONTO = listOf(
        "nit", "tel", "cel", "comprobante", "cuenta", "producto", "referencia", "ref", "aprobacion",
        "autorizacion", "documento", "cedula", "c.c", "factura no", "no.", "nro", "numero", "codigo", "llave"
    )

    private fun montosEn(linea: String, soloConSigno: Boolean = false): List<Long> =
        REGEX_DINERO.findAll(linea)
            .filter { !soloConSigno || it.groupValues[1].isNotEmpty() }
            .mapNotNull { numero(it.groupValues[2])?.roundToLong() }
            .filter { it in 100L..50_000_000L }.toList()

    fun extraerTotalRecibo(textoOcr: String): Long? {
        val lineas = textoOcr.lines().map { normalizar(it).trim() }.filter { it.isNotEmpty() }
        for (palabra in PALABRAS_TOTAL) {
            for ((i, l) in lineas.withIndex()) {
                if (contiene(l, palabra) && !l.contains("subtotal") && !l.contains("sub total") && !l.contains("total items") &&
                    LINEAS_NO_MONTO.none { contiene(l, it) }
                ) {
                    montosEn(l).maxOrNull()?.let { return it }
                    lineas.getOrNull(i + 1)?.let { sig -> montosEn(sig).maxOrNull()?.let { return it } }
                }
            }
        }
        // Sin palabra clave: primero los valores con signo $, luego el más alto (sin NIT, cuentas ni referencias).
        val utiles = lineas.filterNot { l -> LINEAS_NO_MONTO.any { contiene(l, it) } || l.contains("*") }
        return utiles.flatMap { montosEn(it, soloConSigno = true) }.maxOrNull()
            ?: utiles.flatMap { montosEn(it) }.maxOrNull()
    }

    fun interpretarRecibo(
        textoOcr: String,
        foto: String,
        categorias: List<CategoriaEntity>,
        medios: List<MedioPagoEntity>,
        hoy: LocalDate = LocalDate.now()
    ): Propuesta {
        val t = normalizar(textoOcr)
        val lineas = textoOcr.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun valorDespuesDe(vararg claves: String): String? {
            // Solo etiquetas cortas ("Comercio", "Nombre o razón social"), no frases largas.
            val i = lineas.indexOfFirst { l ->
                val n = normalizar(l)
                n.substringBefore(":").length <= 30 && claves.any { contiene(n, it) }
            }
            if (i < 0) return null
            // El valor puede estar en la misma línea ("Comercio: X") o en la siguiente.
            val mismaLinea = lineas[i].substringAfter(":", "").trim()
            if (mismaLinea.count { it.isLetter() } >= 3) return mismaLinea
            return lineas.drop(i + 1).take(2).firstOrNull { l -> l.count { it.isLetter() } >= 3 && l.count { it.isDigit() } <= 2 }
        }
        val destinatario = valorDespuesDe("producto destino", "destinatario", "beneficiario", "enviado a", "para")
        val comercioCampo = valorDespuesDe("comercio", "razon social", "convenio", "empresa")
        val descripcion = valorDespuesDe("descripcion del pago", "descripcion", "concepto")
        val comercio = when {
            comercioCampo != null -> comercioCampo
            descripcion != null -> descripcion
            destinatario != null -> "Transferencia a $destinatario"
            else -> lineas.firstOrNull { l ->
                val n = normalizar(l)
                l.length >= 3 && l.count { it.isLetter() } >= 3 && !n.contains("nit") && !n.contains("factura") &&
                    !n.contains("exitos") && !n.contains("comprobante") && !n.contains("aprobada")
            }.orEmpty()
        }
        // El nombre de una persona no debe decidir la categoría ("Luz Janeth" no es el recibo de la luz).
        val textoCategoria = if (destinatario != null) t.replace(normalizar(destinatario), " ") else t
        val fecha = detectarFecha(t, hoy).let { if (it.isAfter(hoy) || it.isBefore(hoy.minusYears(1))) hoy else it }
        return Propuesta(
            tipo = TipoMovimiento.GASTO,
            monto = extraerTotalRecibo(textoOcr),
            categoriaId = detectarCategoria(textoCategoria, TipoMovimiento.GASTO, categorias)?.id,
            medioId = detectarMedio(t, medios)?.id,
            fecha = fecha,
            nota = comercio.take(60),
            foto = foto,
            textoOriginal = textoOcr
        )
    }
}

// ---------------- Desprendible de nómina ----------------

/** Un concepto del desprendible: devengo (ingreso) o deducción (gasto). */
data class ItemNomina(
    val concepto: String,
    val monto: Long,
    val esDeduccion: Boolean,
    val categoria: String,
    val icono: String
)

data class PropuestaNomina(
    val items: List<ItemNomina>,
    val neto: Long?,
    val fecha: LocalDate,
    val periodo: String,
    val foto: String?
) {
    val devengado: Long get() = items.filter { !it.esDeduccion }.sumOf { it.monto }
    val deducido: Long get() = items.filter { it.esDeduccion }.sumOf { it.monto }
    val netoCalculado: Long get() = devengado - deducido
    val cuadra: Boolean get() = neto != null && neto == netoCalculado
}

object InterpreteNomina {
    private data class Regla(val palabras: List<String>, val deduccion: Boolean, val categoria: String, val icono: String)

    /** El orden importa: la primera regla que coincida decide. */
    private val REGLAS = listOf(
        Regla(listOf("salud", "eps", "pension", "solidaridad", "fsp", "seguridad social"), true, "Salud y pensión", "🏥"),
        Regla(listOf("retencion", "retefuente", "rete fuente"), true, "Retención en la fuente", "🧾"),
        Regla(listOf("libranza", "prestamo", "credito", "embargo", "cooperativa", "fondo de empleados", "anticipo"), true, "Pago de deudas", "💳"),
        Regla(listOf("descuento", "deduccion", "aporte voluntario", "sindicato", "poliza", "seguro", "medicina prepagada", "afc"), true, "Otras deducciones de nómina", "✂️"),
        Regla(listOf("hora extra", "horas extra", "extras", "recargo", "dominical", "festivo", "hed", "hen"), false, "Horas extras y recargos", "⏰"),
        Regla(listOf("auxilio", "transporte", "conectividad", "alimentacion"), false, "Auxilio de transporte", "🚌"),
        Regla(listOf("bonificacion", "bono", "comision", "prima", "vacaciones", "incapacidad", "licencia", "viatico", "cesantias", "intereses"), false, "Bonificaciones y comisiones", "🎁"),
        Regla(listOf("salario", "sueldo", "basico", "jornal"), false, "Salario", "💼")
    )

    private val FILAS_IGNORADAS = listOf(
        "total", "neto", "fecha", "cedula", "ficha", "nombre", "cargo", "departamento", "codigo", "concepto",
        "periodo", "ingreso", "nit", "banco", "cuenta", "firma"
    )

    fun esNomina(textoOcr: String): Boolean {
        val t = Interprete.normalizar(textoOcr)
        val encabezado = listOf("nomina", "recibo de pago", "desprendible", "colilla", "comprobante de pago de nomina")
            .any { t.contains(it) }
        val conceptos = listOf("salud", "pension", "devengado", "asignaciones", "deducciones", "deduciones", "salario basico")
            .count { t.contains(it) }
        return encabezado && conceptos >= 2
    }

    private val REGEX_VALOR = Regex("(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?)(?!\\d)")

    private fun valor(texto: String): Long? {
        val t = texto.trim()
        val sinCentavos = if (Regex(".*[.,]\\d{2}$").matches(t) && t.length > 3) t.substring(0, t.length - 3) else t
        return sinCentavos.replace(".", "").replace(",", "").toLongOrNull()
    }

    fun interpretar(textoOcr: String, foto: String?, hoy: LocalDate = LocalDate.now()): PropuestaNomina? {
        if (!esNomina(textoOcr)) return null
        val filas = textoOcr.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<ItemNomina>()
        var neto: Long? = null
        for (fila in filas) {
            val n = Interprete.normalizar(fila)
            val montos = REGEX_VALOR.findAll(fila).mapNotNull { valor(it.groupValues[1]) }.filter { it >= 1_000L }.toList()
            if (n.contains("neto")) {
                montos.lastOrNull()?.let { neto = it }
                continue
            }
            if (montos.isEmpty() || FILAS_IGNORADAS.any { n.startsWith(it) || n.contains("$it ") && n.indexOf(it) < 3 }) continue
            if (n.contains("total")) continue
            val regla = REGLAS.firstOrNull { r -> r.palabras.any { n.contains(it) } } ?: continue
            // El valor del concepto es el último número de la fila (columna Asignaciones o Deducciones).
            val concepto = fila.replace(Regex("^\\d{3,6}\\s+"), "")
                .replace(REGEX_VALOR, "").replace(Regex("\\s\\d+[.,]\\d{1,2}(\\s|$)"), " ")
                .replace(Regex("\\s+"), " ").trim()
                .lowercase().replaceFirstChar { it.uppercase() }
            items.add(ItemNomina(concepto.ifEmpty { regla.categoria }, montos.last(), regla.deduccion, regla.categoria, regla.icono))
        }
        if (items.isEmpty()) return null
        val t = Interprete.normalizar(textoOcr)
        val fecha = Regex("fecha\\s*:?\\s*(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})").find(t)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        } ?: Interprete.detectarFecha(t, hoy)
        val periodo = Regex("(\\d{1,2}/\\d{1,2}/\\d{4})\\s*al\\s*(\\d{1,2}/\\d{1,2}/\\d{4})").find(t)
            ?.let { "Del ${it.groupValues[1]} al ${it.groupValues[2]}" }.orEmpty()
        return PropuestaNomina(items, neto, if (fecha.isAfter(hoy)) hoy else fecha, periodo, foto)
    }
}
