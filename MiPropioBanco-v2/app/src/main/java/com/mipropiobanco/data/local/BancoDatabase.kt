package com.mipropiobanco.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.math.pow

@Database(
    entities = [
        AporteEntity::class,
        TipoPrestamoEntity::class,
        PrestamoEntity::class,
        PagoEntity::class,
        DeudaEntity::class,
        AbonoDeudaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BancoDatabase : RoomDatabase() {
    abstract fun aporteDao(): AporteDao
    abstract fun tipoPrestamoDao(): TipoPrestamoDao
    abstract fun prestamoDao(): PrestamoDao
    abstract fun pagoDao(): PagoDao
    abstract fun deudaDao(): DeudaDao
    abstract fun abonoDeudaDao(): AbonoDeudaDao

    companion object {
        fun crear(context: Context): BancoDatabase =
            Room.databaseBuilder(context, BancoDatabase::class.java, "mi_propio_banco.db")
                .addMigrations(MIGRACION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        sembrarTipos(db)
                    }
                })
                .build()

        /**
         * Tasas de usura certificadas por la Superintendencia Financiera para noviembre de 2026
         * (Resolución 1707 de 2026). Cambian cada mes: edítalas en Ajustes › Tipos de préstamo.
         * La mora usa la misma tasa porque en Colombia el interés moratorio no puede superar la usura.
         */
        val TIPOS_INICIALES = listOf(
            TipoPrestamoEntity(nombre = "Consumo y ordinario", tasaEA = 28.34, tasaMoraEA = 28.34),
            TipoPrestamoEntity(nombre = "Consumo de bajo monto", tasaEA = 71.82, tasaMoraEA = 71.82),
            TipoPrestamoEntity(nombre = "Productivo de mayor monto", tasaEA = 42.35, tasaMoraEA = 42.35),
            TipoPrestamoEntity(nombre = "Productivo urbano", tasaEA = 59.87, tasaMoraEA = 59.87),
            TipoPrestamoEntity(nombre = "Productivo rural", tasaEA = 33.87, tasaMoraEA = 33.87),
            TipoPrestamoEntity(nombre = "Popular productivo urbano", tasaEA = 88.44, tasaMoraEA = 88.44),
            TipoPrestamoEntity(nombre = "Popular productivo rural", tasaEA = 66.90, tasaMoraEA = 66.90),
            TipoPrestamoEntity(nombre = "Personal suave (1,5% mes)", tasaEA = 19.56, tasaMoraEA = 28.34)
        )

        private fun sembrarTipos(db: SupportSQLiteDatabase) {
            TIPOS_INICIALES.forEach { t ->
                db.execSQL(
                    "INSERT INTO tipos_prestamo (nombre, tasaEA, tasaMoraEA) VALUES (?, ?, ?)",
                    arrayOf<Any>(t.nombre, t.tasaEA, t.tasaMoraEA)
                )
            }
        }

        /** Versión 1 → 2: tipos de préstamo, periodicidad, tasas E.A., mora y módulo de deudas. Conserva todos los datos. */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prestamos ADD COLUMN periodicidad TEXT NOT NULL DEFAULT 'MENSUAL'")
                db.execSQL("ALTER TABLE prestamos ADD COLUMN tipo TEXT NOT NULL DEFAULT 'Personalizado'")
                db.execSQL("ALTER TABLE prestamos ADD COLUMN tasaEA REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE prestamos ADD COLUMN tasaMoraEA REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pagos ADD COLUMN mora INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pagos ADD COLUMN diasAtraso INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tipos_prestamo (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nombre TEXT NOT NULL, tasaEA REAL NOT NULL, tasaMoraEA REAL NOT NULL)"
                )
                sembrarTipos(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deudas (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, acreedor TEXT NOT NULL, " +
                        "saldoInicial INTEGER NOT NULL, pagoMinimo INTEGER NOT NULL, tasaEA REAL NOT NULL, " +
                        "diaPago INTEGER NOT NULL, fechaRegistro INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS abonos_deuda (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, deudaId INTEGER NOT NULL, " +
                        "fecha INTEGER NOT NULL, monto INTEGER NOT NULL, " +
                        "FOREIGN KEY(deudaId) REFERENCES deudas(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_abonos_deuda_deudaId ON abonos_deuda (deudaId)")

                // Los préstamos antiguos eran mensuales: se calcula su tasa E.A. equivalente.
                val existentes = mutableListOf<Pair<Long, Double>>()
                db.query("SELECT id, tasaMensual FROM prestamos").use { c ->
                    while (c.moveToNext()) existentes.add(c.getLong(0) to c.getDouble(1))
                }
                existentes.forEach { (id, mensual) ->
                    val ea = ((1.0 + mensual / 100.0).pow(12.0) - 1.0) * 100.0
                    db.execSQL(
                        "UPDATE prestamos SET tasaEA = ?, tasaMoraEA = ? WHERE id = ?",
                        arrayOf<Any>(ea, ea, id)
                    )
                }
            }
        }
    }
}
