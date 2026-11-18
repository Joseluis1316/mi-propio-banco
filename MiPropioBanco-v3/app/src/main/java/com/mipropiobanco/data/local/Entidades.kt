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
