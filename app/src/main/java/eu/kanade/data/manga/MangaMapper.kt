package eu.kanade.data.manga

import eu.kanade.data.listOfStringsAndAdapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, UpdateStrategy) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, _, initialized, viewer, chapterFlags, coverLastModified, dateAdded, filteredScanlators, updateStrategy ->
        Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            dateAdded = dateAdded,
            viewerFlags = viewer,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            url = url,
            // SY -->
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status,
            // SY <--
            thumbnailUrl = thumbnailUrl,
            updateStrategy = updateStrategy,
            initialized = initialized,
            // SY -->
            filteredScanlators = filteredScanlators,
            // SY <--
        )
    }

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, UpdateStrategy, Long, Long, Long) -> LibraryManga =
    { _id, source, url, artist, author, description, genre, title, status, thumbnail_url, favorite, last_update, next_update, initialized, viewer, chapter_flags, cover_last_modified, date_added, filtered_scanlators, update_strategy, unread_count, read_count, category ->
        LibraryManga().apply {
            this.id = _id
            this.source = source
            this.url = url
            this.artist = artist
            this.author = author
            this.description = description
            this.genre = genre?.joinToString()
            this.title = title
            this.status = status.toInt()
            this.thumbnail_url = thumbnail_url
            this.favorite = favorite
            this.last_update = last_update ?: 0
            this.update_strategy = update_strategy
            this.initialized = initialized
            this.viewer_flags = viewer.toInt()
            this.chapter_flags = chapter_flags.toInt()
            this.cover_last_modified = cover_last_modified
            this.date_added = date_added
            this.filtered_scanlators = filtered_scanlators?.let(listOfStringsAndAdapter::encode)
            this.unreadCount = unread_count.toInt()
            this.readCount = read_count.toInt()
            this.category = category.toInt()
        }
    }
