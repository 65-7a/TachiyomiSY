package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.util.BackupUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.SetMangaUpdateInterval
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
) {
    private val updateManga: UpdateManga = Injekt.get()
    private val chapterRepository: ChapterRepository = Injekt.get()
    private val setMangaUpdateInterval: SetMangaUpdateInterval = Injekt.get()

    private var zonedDateTime = ZonedDateTime.now()
    private var currentRange = setMangaUpdateInterval.getCurrentFetchRange(zonedDateTime)

    private var backupManager = BackupManager(context)

    private var restoreAmount = 0
    private var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    private val errors = mutableListOf<Pair<Date, String>>()

    suspend fun syncFromBackup(uri: Uri, sync: Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        if (!performRestore(uri, sync)) {
            return false
        }

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        if (sync) {
            notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name, contentTitle = context.getString(R.string.library_sync_complete))
        } else {
            notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
        }
        return true
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }

    private suspend fun performRestore(uri: Uri, sync: Boolean): Boolean {
        val backup = BackupUtil.decodeBackup(context, uri)

        restoreAmount = backup.backupManga.size + 1 /* SY --> */ + 1 /* SY <-- */ // +1 for categories, +1 for saved searches

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // SY -->
        if (backup.backupSavedSearches.isNotEmpty()) {
            restoreSavedSearches(backup.backupSavedSearches)
        }
        // SY <--

        // Store source mapping for error messages
        val backupMaps = backup.backupBrokenSources.map { BackupSource(it.name, it.sourceId) } + backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }
        zonedDateTime = ZonedDateTime.now()
        currentRange = setMangaUpdateInterval.getCurrentFetchRange(zonedDateTime)

        return coroutineScope {
            // Restore individual manga, sort by merged source so that merged source manga go last and merged references get the proper ids
            backup.backupManga /* SY --> */.sortedBy { it.source == MERGED_SOURCE_ID } /* SY <-- */.forEach {
                if (!isActive) {
                    return@coroutineScope false
                }

                restoreManga(it, backup.backupCategories, sync)
            }

            // TODO: optionally trigger online library + tracker update
            true
        }
    }

    private suspend fun restoreCategories(backupCategories: List<BackupCategory>) {
        backupManager.restoreCategories(backupCategories)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories), context.getString(R.string.restoring_backup))
    }

    // SY -->
    private suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        backupManager.restoreSavedSearches(backupSavedSearches)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.saved_searches), context.getString(R.string.restoring_backup))
    }
    // SY <--

    private suspend fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>, sync: Boolean) {
        var manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories.map { it.toInt() }
        val history =
            backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead, it.readDuration) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()
        // SY -->
        val mergedMangaReferences = backupManga.mergedMangaReferences
        val flatMetadata = backupManga.flatMetadata
        val customManga = backupManga.getCustomMangaInfo()
        // SY <--

        // SY -->
        manga = EXHMigrations.migrateBackupEntry(manga)
        // SY <--

        try {
            val dbManga = backupManager.getMangaFromDatabase(manga.url, manga.source)
            val restoredManga = if (dbManga == null) {
                // Manga not in database
                restoreExistingManga(manga, chapters, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
            } else {
                // Manga in database
                // Copy information from manga already in database
                val updatedManga = backupManager.restoreExistingManga(manga, dbManga)
                // Fetch rest of manga information
                restoreNewManga(updatedManga, chapters, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
            }
            val updatedChapters = chapterRepository.getChapterByMangaId(restoredManga.id)
            updateManga.awaitUpdateFetchInterval(restoredManga, updatedChapters, zonedDateTime, currentRange)
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        if (sync) {
            showRestoreProgress(restoreProgress, restoreAmount, manga.title, context.getString(R.string.syncing_library))
        } else {
            showRestoreProgress(restoreProgress, restoreAmount, manga.title, context.getString(R.string.restoring_backup))
        }
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreExistingManga(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
    ): Manga {
        val fetchedManga = backupManager.restoreNewManga(manga)
        backupManager.restoreChapters(fetchedManga, chapters)
        restoreExtras(fetchedManga, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        return fetchedManga
    }

    private suspend fun restoreNewManga(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
    ): Manga {
        backupManager.restoreChapters(backupManga, chapters)
        restoreExtras(backupManga, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        return backupManga
    }

    private suspend fun restoreExtras(
        manga: Manga,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
    ) {
        backupManager.restoreCategories(manga, categories, backupCategories)
        backupManager.restoreHistory(history)
        backupManager.restoreTracking(manga, tracks)
        // SY -->
        backupManager.restoreMergedMangaReferencesForManga(manga.id, mergedMangaReferences)
        flatMetadata?.let { backupManager.restoreFlatMetadata(manga.id, it) }
        backupManager.restoreEditedInfo(customManga?.copy(id = manga.id))
        // SY <--
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of manga
     * @param title title of restored manga
     */
    private fun showRestoreProgress(progress: Int, amount: Int, title: String, contentTitle: String) {
        notifier.showRestoreProgress(title, contentTitle, progress, amount)
    }
}
