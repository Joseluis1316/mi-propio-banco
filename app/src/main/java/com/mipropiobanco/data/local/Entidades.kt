package com.mipropiobanco.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ingreso de capital nuevo a la bóveda. Montos en pesos enteros. */
@Entity(tableName = "aportes")
data class AporteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val concepto: String,
    val monto: Long
)

/**
 * Tipo de préstamo con sus tasas efectivas anuales editables
 * (por defecto, las tasas de usura certificadas por la Superfinanciera).
 */
@Entity(tableName = "tipos_prestamo")
data class TipoPrestamoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tasaEA: Double,
    val tasaMoraEA: Double
)

/**
 * Autopréstamo con cuota fija (sistema francés).
 * - plazo: número de cuotas, en la unidad de [periodicidad] (DIARIA, SEMANAL, QUINCENAL, MENSUAL, ANUAL).
 * - tasaPeriodo: tasa por periodo en %, derivada de tasaEA.
 * - fechaCreacion: fecha de desembolso; la cuota n vence n periodos después.
 */
@Entity(tableName = "prestamos")
data class PrestamoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val concepto: String,
    val monto: Long,
    @ColumnInfo(name = "plazoMeses") val plazo: Int,
    @ColumnInfo(name = "tasaMensual") val tasaPeriodo: Double,
    val cuota: Long,
    val fechaCreacion: Long,
    val pagado: Boolean = false,
    val periodicidad: String = "MENSUAL",
    val tipo: String = "Personalizado",
    val tasaEA: Double = 0.0,
    val tasaMoraEA: Double = 0.0
)

/** Pago de una cuota: el capital regresa a la bóveda; interés y mora se suman como ganancia. */
@Entity(
    tableName = "pagos",
    foreignKeys = [
        ForeignKey(
            entity = PrestamoEntity::class,
            parentColumns = ["id"],
            childColumns = ["prestamoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("prestamoId")]
)
data class PagoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prestamoId: Long,
    val numeroCuota: Int,
    val fecha: Long,
    val capital: Long,
    val interes: Long,
    val mora: Long = 0L,
    val diasAtraso: Int = 0
)

/** Deuda con terceros (tarjeta, préstamo bancario, persona). Se gestiona con el método bola de nieve. */
@Entity(tableName = "deudas")
data class DeudaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val acreedor: String,
    val saldoInicial: Long,
    val pagoMinimo: Long,
    val tasaEA: Double,
    val diaPago: Int,
    val fechaRegistro: Long
)

/** Pago hecho a una deuda con terceros. */
@Entity(
    tableName = "abonos_deuda",
    foreignKeys = [
        ForeignKey(
            entity = DeudaEntity::class,
            parentColumns = ["id"],
            childColumns = ["deudaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deudaId")]
)
data class AbonoDeudaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deudaId: Long,
    val fecha: Long,
    val monto: Long
)

/** Categoría de ingreso o gasto. tipo = "INGRESO" o "GASTO". presupuestoMensual = 0 si no tiene límite. */
@Entity(tableName = "categorias")
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tipo: String,
    val icono: String,
    val presupuestoMensual: Long = 0L
)

/** Medio de pago o cuenta: efectivo, transferencia, Nequi, tarjeta… */
@Entity(tableName = "medios_pago")
data class MedioPagoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val icono: String,
    /** Saldo de partida: saldo actual = saldoInicial + entradas − salidas. */
    val saldoInicial: Long = 0L
)

/** Ingreso o gasto registrado. plantillaId indica si vino de una plantilla fija. */
@Entity(tableName = "movimientos", indices = [Index("fecha")])
data class MovimientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val monto: Long,
    val categoriaId: Long,
    val medioId: Long,
    val fecha: Long,
    val nota: String = "",
    val plantillaId: Long? = null,
    /** Ruta local de la foto del recibo (se guarda solo en este celular). */
    val foto: String? = null,
    /** Solo en traslados: a qué cuenta/medio llegó el dinero (medioId es de dónde salió). */
    val medioDestinoId: Long? = null
)

/**
 * Regla aprendida de los correos: cuando aparece esta cuenta o contraparte,
 * se clasifica igual que la última vez (ej. *3012316181 = traslado a mi Nequi).
 */
@Entity(tableName = "reglas_cuenta")
data class ReglaCuentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patron: String,
    val tipo: String,
    val categoriaId: Long,
    val medioDestinoId: Long?
)

/**
 * Plantilla fija (ingreso o gasto que se repite). La ocurrencia n cae en
 * periodicidad.vencimiento(fechaInicio, n), con n = 0 en la fecha de inicio.
 * ultimaOcurrencia = índice de la última registrada u omitida (-1 = ninguna).
 */
@Entity(tableName = "plantillas")
data class PlantillaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tipo: String,
    val monto: Long,
    val categoriaId: Long,
    val medioId: Long,
    val periodicidad: String,
    val fechaInicio: Long,
    val automatica: Boolean,
    val activa: Boolean = true,
    val ultimaOcurrencia: Int = -1
)
