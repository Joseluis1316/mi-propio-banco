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
        AbonoDeudaEntity::class,
        CategoriaEntity::class,
        MedioPagoEntity::class,
        MovimientoEntity::class,
        PlantillaEntity::class,
        ReglaCuentaEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class BancoDatabase : RoomDatabase() {
    abstract fun aporteDao(): AporteDao
    abstract fun tipoPrestamoDao(): TipoPrestamoDao
    abstract fun prestamoDao(): PrestamoDao
    abstract fun pagoDao(): PagoDao
    abstract fun deudaDao(): DeudaDao
    abstract fun abonoDeudaDao(): AbonoDeudaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun medioPagoDao(): MedioPagoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun plantillaDao(): PlantillaDao
    abstract fun reglaCuentaDao(): ReglaCuentaDao

    companion object {
        fun crear(context: Context): BancoDatabase =
            Room.databaseBuilder(context, BancoDatabase::class.java, "mi_propio_banco.db")
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6, MIGRACION_6_7, MIGRACION_7_8)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        sembrarTipos(db)
                        sembrarFinanzas(db, conSaldo = true)
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

        /** Plantillas de categorías (se pueden editar, borrar o crear nuevas). */
        val CATEGORIAS_INICIALES = listOf(
            CategoriaEntity(nombre = "Arriendo", tipo = "GASTO", icono = "🏠"),
            CategoriaEntity(nombre = "Mercado", tipo = "GASTO", icono = "🛒"),
            CategoriaEntity(nombre = "Servicios públicos", tipo = "GASTO", icono = "💡"),
            CategoriaEntity(nombre = "Celular e internet", tipo = "GASTO", icono = "📱"),
            CategoriaEntity(nombre = "Transporte", tipo = "GASTO", icono = "🚌"),
            CategoriaEntity(nombre = "Gasolina", tipo = "GASTO", icono = "⛽"),
            CategoriaEntity(nombre = "Comida fuera", tipo = "GASTO", icono = "🍔"),
            CategoriaEntity(nombre = "Salud", tipo = "GASTO", icono = "🏥"),
            CategoriaEntity(nombre = "Educación", tipo = "GASTO", icono = "📚"),
            CategoriaEntity(nombre = "Ropa", tipo = "GASTO", icono = "👕"),
            CategoriaEntity(nombre = "Ocio", tipo = "GASTO", icono = "🎉"),
            CategoriaEntity(nombre = "Pago de deudas", tipo = "GASTO", icono = "💳"),
            CategoriaEntity(nombre = "Viajes de trabajo", tipo = "GASTO", icono = "🧳"),
            CategoriaEntity(nombre = "Mascotas", tipo = "GASTO", icono = "🐾"),
            CategoriaEntity(nombre = "Regalos", tipo = "GASTO", icono = "🎁"),
            CategoriaEntity(nombre = "Otros gastos", tipo = "GASTO", icono = "📦"),
            CategoriaEntity(nombre = "Salario", tipo = "INGRESO", icono = "💼"),
            CategoriaEntity(nombre = "Independiente / honorarios", tipo = "INGRESO", icono = "🧾"),
            CategoriaEntity(nombre = "Arriendos recibidos", tipo = "INGRESO", icono = "🏘️"),
            CategoriaEntity(nombre = "Ventas", tipo = "INGRESO", icono = "💰"),
            CategoriaEntity(nombre = "Intereses y rendimientos", tipo = "INGRESO", icono = "📈"),
            CategoriaEntity(nombre = "Viáticos y reembolsos", tipo = "INGRESO", icono = "↩️"),
            CategoriaEntity(nombre = "Regalos recibidos", tipo = "INGRESO", icono = "🎀"),
            CategoriaEntity(nombre = "Otros ingresos", tipo = "INGRESO", icono = "➕")
        )

        val MEDIOS_INICIALES = listOf(
            MedioPagoEntity(nombre = "Efectivo", icono = "💵"),
            MedioPagoEntity(nombre = "Transferencia", icono = "🏦"),
            MedioPagoEntity(nombre = "Nequi", icono = "📲"),
            MedioPagoEntity(nombre = "Daviplata", icono = "📲"),
            MedioPagoEntity(nombre = "Bre-B / llave", icono = "⚡"),
            MedioPagoEntity(nombre = "Tarjeta débito", icono = "💳"),
            MedioPagoEntity(nombre = "Tarjeta crédito", icono = "💳")
        )

        /** [conSaldo] = la tabla medios_pago ya tiene la columna saldoInicial (instalación nueva). */
        private fun sembrarFinanzas(db: SupportSQLiteDatabase, conSaldo: Boolean = false) {
            CATEGORIAS_INICIALES.forEach { c ->
                db.execSQL(
                    "INSERT INTO categorias (nombre, tipo, icono, presupuestoMensual) VALUES (?, ?, ?, 0)",
                    arrayOf<Any>(c.nombre, c.tipo, c.icono)
                )
            }
            MEDIOS_INICIALES.forEach { m ->
                if (conSaldo) {
                    db.execSQL("INSERT INTO medios_pago (nombre, icono, saldoInicial) VALUES (?, ?, 0)", arrayOf<Any>(m.nombre, m.icono))
                } else {
                    db.execSQL("INSERT INTO medios_pago (nombre, icono) VALUES (?, ?)", arrayOf<Any>(m.nombre, m.icono))
                }
            }
        }

        /** Versión 6 → 7: Mi banco también le presta a otras personas (no se borra nada). */
        /** Versión 7 → 8: a qué quincena va cada ingreso (no se borra nada). */
        val MIGRACION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movimientos ADD COLUMN quincena TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prestamos ADD COLUMN prestatario TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Versión 5 → 6: fecha exacta del próximo pago de cada deuda (no se borra nada). */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deudas ADD COLUMN proximoPago INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 4 → 5: traslados entre cuentas, saldo por medio y reglas aprendidas de los correos. */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movimientos ADD COLUMN medioDestinoId INTEGER")
                db.execSQL("ALTER TABLE medios_pago ADD COLUMN saldoInicial INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reglas_cuenta (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "patron TEXT NOT NULL, tipo TEXT NOT NULL, categoriaId INTEGER NOT NULL, medioDestinoId INTEGER)"
                )
            }
        }

        /** Versión 3 → 4: foto del recibo en cada movimiento. */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movimientos ADD COLUMN foto TEXT")
            }
        }

        /** Versión 2 → 3: ingresos, gastos, categorías, medios de pago y plantillas fijas. */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categorias (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nombre TEXT NOT NULL, tipo TEXT NOT NULL, icono TEXT NOT NULL, presupuestoMensual INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS medios_pago (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nombre TEXT NOT NULL, icono TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS movimientos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "tipo TEXT NOT NULL, monto INTEGER NOT NULL, categoriaId INTEGER NOT NULL, medioId INTEGER NOT NULL, " +
                        "fecha INTEGER NOT NULL, nota TEXT NOT NULL, plantillaId INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movimientos_fecha ON movimientos (fecha)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plantillas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nombre TEXT NOT NULL, tipo TEXT NOT NULL, monto INTEGER NOT NULL, categoriaId INTEGER NOT NULL, " +
                        "medioId INTEGER NOT NULL, periodicidad TEXT NOT NULL, fechaInicio INTEGER NOT NULL, " +
                        "automatica INTEGER NOT NULL, activa INTEGER NOT NULL, ultimaOcurrencia INTEGER NOT NULL)"
                )
                sembrarFinanzas(db)
            }
        }

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
