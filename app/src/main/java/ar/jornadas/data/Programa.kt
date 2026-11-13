package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val version: Int, val descargadaEn: Long)

@Entity(tableName = "favoritas", primaryKeys = ["version", "charlaId"])
data class FavoritaEdicion(val version: Int, val charlaId: String)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    fun observarHayEdicion(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarEdicion(edicion: Edicion)

    @Query("DELETE FROM charlas")
    suspend fun borrarTodas()

    @Query("DELETE FROM charlas WHERE id NOT IN (:ids)")
    suspend fun borrarAusentes(ids: List<String>)

    @Query("UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora, sala = :sala, ponente = :ponente, resumen = :resumen, favorita = :favorita WHERE id = :id")
    suspend fun actualizarDatosConFavorita(id: String, titulo: String, tema: String, dia: Int, hora: String, sala: String, ponente: String, resumen: String, favorita: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarNueva(charla: Charla)

    @Query("SELECT charlaId FROM favoritas WHERE version = :version")
    suspend fun obtenerFavoritasEnVersion(version: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarFavoritaEdicion(fav: FavoritaEdicion)

    @Query("DELETE FROM favoritas WHERE version = :version AND charlaId = :charlaId")
    suspend fun borrarFavoritaEdicion(version: Int, charlaId: String)

    @Query("UPDATE charlas SET favorita = :favorita WHERE id = :id")
    suspend fun fijarFavorita(id: String, favorita: Boolean)

    @Transaction
    suspend fun fijarFavoritaEnVersion(id: String, favorita: Boolean, version: Int) {
        fijarFavorita(id, favorita)
        if (favorita) {
            guardarFavoritaEdicion(FavoritaEdicion(version = version, charlaId = id))
        } else {
            borrarFavoritaEdicion(version = version, charlaId = id)
        }
    }

    @Transaction
    suspend fun aplicarInstantanea(fichas: List<Charla>, version: Int = 1, momento: Long = System.currentTimeMillis()) {
        if (fichas.isEmpty()) {
            borrarTodas()
        } else {
            borrarAusentes(fichas.map { it.id })
        }
        val favsEnVersion = obtenerFavoritasEnVersion(version).toSet()
        fichas.forEach { f ->
            val esFav = f.id in favsEnVersion
            val charlaConFav = f.copy(favorita = esFav)
            if (actualizarDatosConFavorita(f.id, f.titulo, f.tema, f.dia, f.hora, f.sala, f.ponente, f.resumen, esFav) == 0) {
                insertarNueva(charlaConFav)
            }
        }
        registrarEdicion(Edicion(version = version, descargadaEn = momento))
    }

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)
}

val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT OR IGNORE INTO edicion (id, descargadaEn) SELECT 0, ${System.currentTimeMillis()} WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}

val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `favoritas` (`version` INTEGER NOT NULL, `charlaId` TEXT NOT NULL, PRIMARY KEY(`version`, `charlaId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion_new` (`version` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`version`))")
        db.execSQL("INSERT OR IGNORE INTO edicion_new (version, descargadaEn) SELECT 1, descargadaEn FROM edicion")
        db.execSQL("DROP TABLE edicion")
        db.execSQL("ALTER TABLE edicion_new RENAME TO edicion")
        db.execSQL("INSERT OR IGNORE INTO favoritas (version, charlaId) SELECT 1, id FROM charlas WHERE favorita = 1")
    }
}

@Database(entities = [Charla::class, Edicion::class, FavoritaEdicion::class], version = 3, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
