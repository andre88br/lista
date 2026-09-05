package br.com.andre88.lista.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProdutoEntity::class, ItemEntity::class, ScanEventoEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun produtoDao(): ProdutoDao
    abstract fun itemDao(): ItemDao
    abstract fun scanEventoDao(): ScanEventoDao

    companion object {

        /**
         * v1 -> v2: prepara o banco para a sincronizacao.
         *
         * O historico ganha id de texto (UUID), porque o id e quem garante que
         * reenviar um evento nao conta duas vezes; o produto ganha `atualizadoEm`,
         * usado para decidir qual nome vence entre os dois celulares. O historico
         * antigo entra como ja sincronizado: quem cria ou entra numa casa envia o
         * estado atual, nao a vida inteira do app.
         */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE produto ADD COLUMN atualizadoEm INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("UPDATE produto SET atualizadoEm = criadoEm")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scan_evento_novo (
                        id TEXT NOT NULL PRIMARY KEY,
                        codigoBarras TEXT NOT NULL,
                        modo TEXT NOT NULL,
                        deltaEstoque INTEGER NOT NULL,
                        deltaLista INTEGER NOT NULL,
                        deltaCarrinho INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        desfeito INTEGER NOT NULL DEFAULT 0,
                        sincronizado INTEGER NOT NULL DEFAULT 0,
                        autorId TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO scan_evento_novo
                        (id, codigoBarras, modo, deltaEstoque, deltaLista, deltaCarrinho, timestamp, desfeito, sincronizado, autorId)
                    SELECT
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' ||
                              hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6))),
                        codigoBarras, modo, deltaEstoque, deltaLista, deltaCarrinho, timestamp, desfeito, 1, NULL
                    FROM scan_evento
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE scan_evento")
                db.execSQL("ALTER TABLE scan_evento_novo RENAME TO scan_evento")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_evento_timestamp ON scan_evento (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_evento_codigoBarras ON scan_evento (codigoBarras)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_evento_sincronizado ON scan_evento (sincronizado)")
            }
        }

        /**
         * v2 -> v3: guarda quem fez cada leitura, para as listas mostrarem de
         * quem foi sem precisar consultar o servidor a cada exibicao.
         */
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_evento ADD COLUMN autorNome TEXT")
                db.execSQL("ALTER TABLE item ADD COLUMN ultimoAutorNome TEXT")
            }
        }

        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lista.db")
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3)
                .build()
    }
}
