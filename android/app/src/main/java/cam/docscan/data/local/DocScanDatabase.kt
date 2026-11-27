package cam.docscan.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import cam.docscan.data.BatchEntity
import cam.docscan.data.PageEntity
import cam.docscan.data.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "pages")
data class RoomPageEntity(
    @PrimaryKey val id: String,
    val batchId: String?,
    val name: String,
    val source: String,
    val imagePath: String,
    val createdAt: Long,
    val ocrText: String? = null,
    val ocrLang: String? = null,
    val ocrLangConfidence: Float? = null,
    val docType: String? = null,
    val docTypeConfidence: Float? = null,
    val docTypesAlt: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val summarySource: String? = null,
    val nlpVersion: String? = null,
    val metadataWrittenAt: Long? = null,
    val sortIndex: Int = 0,
)

@Entity(tableName = "batches")
data class RoomBatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val summarySource: String? = null,
)

class StringListConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

@Dao
interface DocScanDao {
    @Query("SELECT * FROM pages ORDER BY createdAt DESC, sortIndex ASC")
    suspend fun getAllPages(): List<RoomPageEntity>

    @Query("SELECT * FROM pages ORDER BY createdAt DESC, sortIndex ASC")
    fun observeAllPages(): Flow<List<RoomPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: RoomPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(batch: RoomBatchEntity)

    @Query(
        """
        SELECT * FROM pages
        WHERE name LIKE '%' || :q || '%'
           OR IFNULL(ocrText, '') LIKE '%' || :q || '%'
           OR tags LIKE '%' || :q || '%'
           OR IFNULL(docType, '') LIKE '%' || :q || '%'
           OR IFNULL(summary, '') LIKE '%' || :q || '%'
        ORDER BY createdAt DESC
        """,
    )
    suspend fun searchPages(q: String): List<RoomPageEntity>

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)

    @Query("DELETE FROM pages")
    suspend fun deleteAllPages()

    @Query("DELETE FROM batches")
    suspend fun deleteAllBatches()
}

@Database(
    entities = [RoomPageEntity::class, RoomBatchEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(StringListConverters::class)
abstract class DocScanDatabase : RoomDatabase() {
    abstract fun docScanDao(): DocScanDao

    companion object {
        @Volatile
        private var instance: DocScanDatabase? = null

        fun getInstance(context: Context): DocScanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocScanDatabase::class.java,
                    "docscan.db",
                ).build().also { instance = it }
            }
    }
}

class RoomPageRepository(private val dao: DocScanDao) : PageRepository {
    override suspend fun all(): List<PageEntity> = dao.getAllPages().map { it.toDomain() }

    override fun observeAll(): Flow<List<PageEntity>> =
        dao.observeAllPages().map { rows -> rows.map { it.toDomain() } }

    override suspend fun insert(page: PageEntity) {
        dao.upsertPage(page.toRoom())
    }

    override suspend fun upsertBatch(batch: BatchEntity) {
        dao.upsertBatch(
            RoomBatchEntity(
                id = batch.id,
                name = batch.name,
                createdAt = batch.createdAt,
                tags = batch.tags,
                summary = batch.summary,
                summarySource = batch.summarySource,
            ),
        )
    }

    override suspend fun search(query: String): List<PageEntity> {
        val q = query.trim()
        return if (q.isEmpty()) all() else dao.searchPages(q).map { it.toDomain() }
    }

    override suspend fun deletePage(id: String) {
        dao.deletePage(id)
    }

    override suspend fun clearAll() {
        dao.deleteAllPages()
        dao.deleteAllBatches()
    }

    private fun RoomPageEntity.toDomain() = PageEntity(
        id = id,
        batchId = batchId,
        name = name,
        source = source,
        imagePath = imagePath,
        createdAt = createdAt,
        ocrText = ocrText,
        ocrLang = ocrLang,
        ocrLangConfidence = ocrLangConfidence,
        docType = docType,
        docTypeConfidence = docTypeConfidence,
        docTypesAlt = docTypesAlt,
        tags = tags,
        summary = summary,
        summarySource = summarySource,
        nlpVersion = nlpVersion,
        metadataWrittenAt = metadataWrittenAt,
        sortIndex = sortIndex,
    )

    private fun PageEntity.toRoom() = RoomPageEntity(
        id = id,
        batchId = batchId,
        name = name,
        source = source,
        imagePath = imagePath,
        createdAt = createdAt,
        ocrText = ocrText,
        ocrLang = ocrLang,
        ocrLangConfidence = ocrLangConfidence,
        docType = docType,
        docTypeConfidence = docTypeConfidence,
        docTypesAlt = docTypesAlt,
        tags = tags,
        summary = summary,
        summarySource = summarySource,
        nlpVersion = nlpVersion,
        metadataWrittenAt = metadataWrittenAt,
        sortIndex = sortIndex,
    )
}
