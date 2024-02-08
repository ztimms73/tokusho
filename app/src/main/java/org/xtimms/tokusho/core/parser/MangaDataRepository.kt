package org.xtimms.tokusho.core.parser

import dagger.Reusable
import org.koitharu.kotatsu.parsers.model.Manga
import org.xtimms.tokusho.core.database.MangaDatabase
import org.xtimms.tokusho.core.database.entity.toManga
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class MangaDataRepository @Inject constructor(
    private val db: MangaDatabase,
    private val resolverProvider: Provider<MangaLinkResolver>,
) {

    suspend fun findMangaById(mangaId: Long): Manga? {
        return db.getMangaDao().find(mangaId)?.toManga()
    }

    suspend fun findMangaByPublicUrl(publicUrl: String): Manga? {
        return db.getMangaDao().findByPublicUrl(publicUrl)?.toManga()
    }

    suspend fun resolveIntent(intent: MangaIntent): Manga? = when {
        intent.manga != null -> intent.manga
        intent.mangaId != 0L -> findMangaById(intent.mangaId)
        intent.uri != null -> resolverProvider.get().resolve(intent.uri)
        else -> null
    }

}