package org.xtimms.tokusho.data.repository

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.parsers.model.Manga
import org.xtimms.tokusho.core.database.TokushoDatabase
import org.xtimms.tokusho.core.database.entity.HistoryEntity
import org.xtimms.tokusho.core.database.entity.toManga
import org.xtimms.tokusho.core.database.entity.toMangaHistory
import org.xtimms.tokusho.core.database.entity.toMangaTags
import org.xtimms.tokusho.core.model.MangaHistory
import org.xtimms.tokusho.core.model.MangaWithHistory
import org.xtimms.tokusho.core.model.findById
import org.xtimms.tokusho.core.model.isNsfw
import org.xtimms.tokusho.core.parser.MangaDataRepository
import org.xtimms.tokusho.utils.lang.mapItems
import javax.inject.Inject

const val PROGRESS_NONE = -1f

@Reusable
class HistoryRepository @Inject constructor(
    private val db: TokushoDatabase,
    private val mangaRepository: MangaDataRepository,
) {

    suspend fun getList(offset: Int, limit: Int): List<Manga> {
        val entities = db.getHistoryDao().findAll(offset, limit)
        return entities.map { it.manga.toManga(it.tags.toMangaTags()) }
    }

    suspend fun getLastOrNull(): Manga? {
        val entity = db.getHistoryDao().findAll(0, 1).firstOrNull() ?: return null
        return entity.manga.toManga(entity.tags.toMangaTags())
    }

    fun observeAll(): Flow<List<Manga>> {
        return db.getHistoryDao().observeAll().mapItems {
            it.manga.toManga(it.tags.toMangaTags())
        }
    }

    fun observeAll(limit: Int): Flow<List<Manga>> {
        return db.getHistoryDao().observeAll(limit).mapItems {
            it.manga.toManga(it.tags.toMangaTags())
        }
    }

    fun observeAllWithHistory(): Flow<List<MangaWithHistory>> {
        return db.getHistoryDao().observeAll().mapItems {
            MangaWithHistory(
                it.manga.toManga(it.tags.toMangaTags()),
                it.history.toMangaHistory(),
            )
        }
    }

    suspend fun getOne(manga: Manga): MangaHistory? {
        return db.getHistoryDao().find(manga.id)?.recoverIfNeeded(manga)?.toMangaHistory()
    }

    suspend fun delete(manga: Manga) {
        db.getHistoryDao().delete(manga.id)
    }

    fun observeOne(id: Long): Flow<MangaHistory?> {
        return db.getHistoryDao().observe(id).map {
            it?.toMangaHistory()
        }
    }

    suspend fun addOrUpdate(manga: Manga, chapterId: Long, page: Int, scroll: Int, percent: Float) {
        if (shouldSkip(manga)) {
            return
        }
        db.withTransaction {
            mangaRepository.storeManga(manga)
            db.getHistoryDao().upsert(
                HistoryEntity(
                    mangaId = manga.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    chapterId = chapterId,
                    page = page,
                    scroll = scroll.toFloat(), // we migrate to int, but decide to not update database
                    percent = percent,
                    deletedAt = 0L,
                ),
            )
        }
    }

    fun shouldSkip(manga: Manga): Boolean {
        return ((manga.source.isNsfw() || manga.isNsfw))
    }

    private suspend fun HistoryEntity.recoverIfNeeded(manga: Manga): HistoryEntity {
        val chapters = manga.chapters
        if (chapters.isNullOrEmpty() || chapters.findById(chapterId) != null) {
            return this
        }
        val newChapterId = chapters.getOrNull(
            (chapters.size * percent).toInt(),
        )?.id ?: return this
        val newEntity = copy(chapterId = newChapterId)
        db.getHistoryDao().update(newEntity)
        return newEntity
    }
}