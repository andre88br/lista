package br.com.andre88.lista.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProdutoEntity::class, ItemEntity::class, ScanEventoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun produtoDao(): ProdutoDao
    abstract fun itemDao(): ItemDao
    abstract fun scanEventoDao(): ScanEventoDao

    companion object {
        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lista.db")
                .build()
    }
}
