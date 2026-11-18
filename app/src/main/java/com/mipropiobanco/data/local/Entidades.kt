package com.mipropiobanco.data.local

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

/** Autopréstamo con cuota fija (sistema francés). tasaMensual en porcentaje, ej. 1.5 = 1,5 %. */
@Entity(tableName = "prestamos")
data class PrestamoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val concepto: String,
    val monto: Long,
    val plazoMeses: Int,
    val tasaMensual: Double,
    val cuota: Long,
    val fechaCreacion: Long,
    val pagado: Boolean = false
)

/** Pago de una cuota: el capital regresa a la bóveda y el interés se suma como ganancia. */
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
    val interes: Long
)
