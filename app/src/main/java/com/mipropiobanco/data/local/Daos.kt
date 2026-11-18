package com.mipropiobanco.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AporteDao {
    @Query("SELECT * FROM aportes ORDER BY fecha DESC, id DESC")
    fun observarTodos(): Flow<List<AporteEntity>>

    @Query("SELECT * FROM aportes ORDER BY fecha ASC, id ASC")
    suspend fun obtenerTodos(): List<AporteEntity>

    @Query("SELECT COALESCE(SUM(monto), 0) FROM aportes")
    suspend fun sumarMontos(): Long

    @Insert
    suspend fun insertar(aporte: AporteEntity): Long

    @Insert
    suspend fun insertarTodos(aportes: List<AporteEntity>)

    @Delete
    suspend fun eliminar(aporte: AporteEntity)

    @Query("DELETE FROM aportes")
    suspend fun borrarTodo()
}

@Dao
interface TipoPrestamoDao {
    @Query("SELECT * FROM tipos_prestamo ORDER BY id ASC")
    fun observarTodos(): Flow<List<TipoPrestamoEntity>>

    @Query("SELECT * FROM tipos_prestamo ORDER BY id ASC")
    suspend fun obtenerTodos(): List<TipoPrestamoEntity>

    @Insert
    suspend fun insertar(tipo: TipoPrestamoEntity): Long

    @Insert
    suspend fun insertarTodos(tipos: List<TipoPrestamoEntity>)

    @Update
    suspend fun actualizar(tipo: TipoPrestamoEntity)

    @Delete
    suspend fun eliminar(tipo: TipoPrestamoEntity)

    @Query("DELETE FROM tipos_prestamo")
    suspend fun borrarTodo()
}

@Dao
interface PrestamoDao {
    @Query("SELECT * FROM prestamos ORDER BY pagado ASC, fechaCreacion DESC, id DESC")
    fun observarTodos(): Flow<List<PrestamoEntity>>

    @Query("SELECT * FROM prestamos ORDER BY id ASC")
    suspend fun obtenerTodos(): List<PrestamoEntity>

    @Query("SELECT * FROM prestamos WHERE id = :id")
    suspend fun obtenerPorId(id: Long): PrestamoEntity?

    @Query("SELECT COALESCE(SUM(monto), 0) FROM prestamos")
    suspend fun sumarMontos(): Long

    @Insert
    suspend fun insertar(prestamo: PrestamoEntity): Long

    @Insert
    suspend fun insertarTodos(prestamos: List<PrestamoEntity>)

    @Update
    suspend fun actualizar(prestamo: PrestamoEntity)

    @Delete
    suspend fun eliminar(prestamo: PrestamoEntity)

    @Query("DELETE FROM prestamos")
    suspend fun borrarTodo()
}

@Dao
interface PagoDao {
    @Query("SELECT * FROM pagos ORDER BY fecha ASC, numeroCuota ASC")
    fun observarTodos(): Flow<List<PagoEntity>>

    @Query("SELECT * FROM pagos ORDER BY id ASC")
    suspend fun obtenerTodos(): List<PagoEntity>

    @Query("SELECT * FROM pagos WHERE prestamoId = :prestamoId ORDER BY numeroCuota ASC")
    suspend fun obtenerPorPrestamo(prestamoId: Long): List<PagoEntity>

    @Query("SELECT COALESCE(SUM(capital), 0) FROM pagos")
    suspend fun sumarCapital(): Long

    @Query("SELECT COALESCE(SUM(interes), 0) FROM pagos")
    suspend fun sumarInteres(): Long

    @Query("SELECT COALESCE(SUM(mora), 0) FROM pagos")
    suspend fun sumarMora(): Long

    @Insert
    suspend fun insertar(pago: PagoEntity): Long

    @Insert
    suspend fun insertarTodos(pagos: List<PagoEntity>)

    @Delete
    suspend fun eliminar(pago: PagoEntity)

    @Query("DELETE FROM pagos")
    suspend fun borrarTodo()
}

@Dao
interface DeudaDao {
    @Query("SELECT * FROM deudas ORDER BY id ASC")
    fun observarTodas(): Flow<List<DeudaEntity>>

    @Query("SELECT * FROM deudas ORDER BY id ASC")
    suspend fun obtenerTodas(): List<DeudaEntity>

    @Query("SELECT * FROM deudas WHERE id = :id")
    suspend fun obtenerPorId(id: Long): DeudaEntity?

    @Insert
    suspend fun insertar(deuda: DeudaEntity): Long

    @Insert
    suspend fun insertarTodas(deudas: List<DeudaEntity>)

    @Update
    suspend fun actualizar(deuda: DeudaEntity)

    @Delete
    suspend fun eliminar(deuda: DeudaEntity)

    @Query("DELETE FROM deudas")
    suspend fun borrarTodo()
}

@Dao
interface AbonoDeudaDao {
    @Query("SELECT * FROM abonos_deuda ORDER BY fecha ASC, id ASC")
    fun observarTodos(): Flow<List<AbonoDeudaEntity>>

    @Query("SELECT * FROM abonos_deuda ORDER BY id ASC")
    suspend fun obtenerTodos(): List<AbonoDeudaEntity>

    @Query("SELECT * FROM abonos_deuda WHERE deudaId = :deudaId ORDER BY fecha ASC, id ASC")
    suspend fun obtenerPorDeuda(deudaId: Long): List<AbonoDeudaEntity>

    @Insert
    suspend fun insertar(abono: AbonoDeudaEntity): Long

    @Insert
    suspend fun insertarTodos(abonos: List<AbonoDeudaEntity>)

    @Delete
    suspend fun eliminar(abono: AbonoDeudaEntity)

    @Query("DELETE FROM abonos_deuda")
    suspend fun borrarTodo()
}

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias ORDER BY tipo DESC, nombre ASC")
    fun observarTodas(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias ORDER BY id ASC")
    suspend fun obtenerTodas(): List<CategoriaEntity>

    @Insert
    suspend fun insertar(categoria: CategoriaEntity): Long

    @Insert
    suspend fun insertarTodas(categorias: List<CategoriaEntity>)

    @Update
    suspend fun actualizar(categoria: CategoriaEntity)

    @Delete
    suspend fun eliminar(categoria: CategoriaEntity)

    @Query("DELETE FROM categorias")
    suspend fun borrarTodo()
}

@Dao
interface MedioPagoDao {
    @Query("SELECT * FROM medios_pago ORDER BY id ASC")
    fun observarTodos(): Flow<List<MedioPagoEntity>>

    @Query("SELECT * FROM medios_pago ORDER BY id ASC")
    suspend fun obtenerTodos(): List<MedioPagoEntity>

    @Insert
    suspend fun insertar(medio: MedioPagoEntity): Long

    @Insert
    suspend fun insertarTodos(medios: List<MedioPagoEntity>)

    @Update
    suspend fun actualizar(medio: MedioPagoEntity)

    @Delete
    suspend fun eliminar(medio: MedioPagoEntity)

    @Query("DELETE FROM medios_pago")
    suspend fun borrarTodo()
}

@Dao
interface MovimientoDao {
    @Query("SELECT * FROM movimientos ORDER BY fecha DESC, id DESC")
    fun observarTodos(): Flow<List<MovimientoEntity>>

    @Query("SELECT * FROM movimientos ORDER BY id ASC")
    suspend fun obtenerTodos(): List<MovimientoEntity>

    @Query("SELECT COUNT(*) FROM movimientos WHERE categoriaId = :id")
    suspend fun contarPorCategoria(id: Long): Int

    @Query("SELECT COUNT(*) FROM movimientos WHERE medioId = :id")
    suspend fun contarPorMedio(id: Long): Int

    @Insert
    suspend fun insertar(movimiento: MovimientoEntity): Long

    @Insert
    suspend fun insertarTodos(movimientos: List<MovimientoEntity>)

    @Update
    suspend fun actualizar(movimiento: MovimientoEntity)

    @Delete
    suspend fun eliminar(movimiento: MovimientoEntity)

    @Query("DELETE FROM movimientos")
    suspend fun borrarTodo()
}

@Dao
interface PlantillaDao {
    @Query("SELECT * FROM plantillas ORDER BY activa DESC, nombre ASC")
    fun observarTodas(): Flow<List<PlantillaEntity>>

    @Query("SELECT * FROM plantillas ORDER BY id ASC")
    suspend fun obtenerTodas(): List<PlantillaEntity>

    @Query("SELECT * FROM plantillas WHERE id = :id")
    suspend fun obtenerPorId(id: Long): PlantillaEntity?

    @Query("SELECT COUNT(*) FROM plantillas WHERE categoriaId = :id")
    suspend fun contarPorCategoria(id: Long): Int

    @Query("SELECT COUNT(*) FROM plantillas WHERE medioId = :id")
    suspend fun contarPorMedio(id: Long): Int

    @Insert
    suspend fun insertar(plantilla: PlantillaEntity): Long

    @Insert
    suspend fun insertarTodas(plantillas: List<PlantillaEntity>)

    @Update
    suspend fun actualizar(plantilla: PlantillaEntity)

    @Delete
    suspend fun eliminar(plantilla: PlantillaEntity)

    @Query("DELETE FROM plantillas")
    suspend fun borrarTodo()
}

@Dao
interface ReglaCuentaDao {
    @Query("SELECT * FROM reglas_cuenta ORDER BY id ASC")
    suspend fun obtenerTodas(): List<ReglaCuentaEntity>

    @Query("SELECT * FROM reglas_cuenta WHERE patron = :patron LIMIT 1")
    suspend fun buscar(patron: String): ReglaCuentaEntity?

    @Insert
    suspend fun insertar(regla: ReglaCuentaEntity): Long

    @Insert
    suspend fun insertarTodas(reglas: List<ReglaCuentaEntity>)

    @Update
    suspend fun actualizar(regla: ReglaCuentaEntity)

    @Query("DELETE FROM reglas_cuenta")
    suspend fun borrarTodo()
}
