package com.mipropiobanco.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AporteEntity::class, PrestamoEntity::class, PagoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BancoDatabase : RoomDatabase() {
    abstract fun aporteDao(): AporteDao
    abstract fun prestamoDao(): PrestamoDao
    abstract fun pagoDao(): PagoDao

    companion object {
        fun crear(context: Context): BancoDatabase =
            Room.databaseBuilder(context, BancoDatabase::class.java, "mi_propio_banco.db").build()
    }
}
