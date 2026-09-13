package com.prismora.player.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.prismora.player.metadata.MetadataService
import com.prismora.player.model.Track
import java.io.File

class MusicLibrary(private val context: Context) {
    private data class CachedTrack(
        val id: String,
        val uri: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val mime: String?,
        val extension: String,
        val folderPath: String,
        val modifiedMs: Long,
        val trackNumber: Int
    )

    private val metadata = MetadataService(context)
    private val supported = setOf("flac", "wav", "wave", "mp3", "m4a", "aac", "alac", "dsf")
    private val gson = Gson()
    private val cacheFile = File(File(context.filesDir, "miku_cache").apply { mkdirs() }, "library-v2.json")

    fun loadCached(): List<Track> {
        if (!cacheFile.isFile) return emptyList()
        val type = object : TypeToken<List<CachedTrack>>() {}.type
        val cached = runCatching { gson.fromJson<List<CachedTrack>>(cacheFile.readText(), type) }.getOrNull() ?: return emptyList()
        return cached.mapNotNull { item ->
            runCatching {
                Track(
                    id = item.id,
                    uri = Uri.parse(item.uri),
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationMs = item.durationMs,
                    mime = item.mime,
                    extension = item.extension,
                    folderPath = item.folderPath,
                    modifiedMs = item.modifiedMs,
                    trackNumber = item.trackNumber
                )
            }.getOrNull()
        }
    }

    fun saveCached(tracks: List<Track>) {
        val compact = tracks.map { track ->
            CachedTrack(
                id = track.id,
                uri = track.uri.toString(),
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                mime = track.mime,
                extension = track.extension,
                folderPath = track.folderPath,
                modifiedMs = track.modifiedMs,
                trackNumber = track.trackNumber
            )
        }
        runCatching { cacheFile.writeText(gson.toJson(compact)) }
    }

    fun scanAll(onUpdate: (List<Track>) -> Unit = {}): List<Track> {
        val result = LinkedHashMap<String, Track>()
        var lastUpdate = 0
        fun add(track: Track) {
            val sig = "${track.title}|${track.artist}|${track.durationMs}".lowercase()
            if (!result.containsKey(sig)) {
                result[sig] = track
                if (result.size - lastUpdate >= 20) {
                    onUpdate(result.values.toList())
                    lastUpdate = result.size
                }
            }
        }
        scanMediaStore().forEach(::add)
        onUpdate(result.values.toList())
        lastUpdate = result.size
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
            .forEach { tree ->
                scanTreeInternal(tree, ::add)
            }
        val final = result.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        onUpdate(final)
        return final
    }

    private fun scanTreeInternal(tree: Uri, onTrack: (Track) -> Unit) {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return
        val rootPath = root.name?.takeIf { it.isNotBlank() } ?: "Selected folder"
        walkInternal(root, rootPath, onTrack)
    }

    private fun walkInternal(file: DocumentFile, parentPath: String, onTrack: (Track) -> Unit) {
        if (file.isDirectory) {
            val childPath = listOf(parentPath, file.name.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString("/")
            file.listFiles().forEach { walkInternal(it, childPath, onTrack) }
            return
        }
        val name = file.name ?: return
        val ext = name.substringAfterLast('.', "").lowercase()
        if (!file.isFile || ext !in supported) return
        val modifiedMs = file.lastModified().coerceAtLeast(0L)
        val local = runCatching { metadata.readLocalCached(file.uri, modifiedMs) }.getOrNull()
        onTrack(Track(
            id = file.uri.toString(),
            uri = file.uri,
            title = local?.title?.takeUnless { it == "Unknown Track" } ?: name.substringBeforeLast('.'),
            artist = local?.artist ?: "Unknown Artist",
            album = local?.album ?: "Unknown Album",
            durationMs = local?.durationMs ?: 0L,
            mime = file.type,
            extension = ext,
            folderPath = parentPath.cleanFolderPath(),
            modifiedMs = modifiedMs,
            trackNumber = local?.trackNumber ?: 0
        ))
    }

    fun scanTree(tree: Uri, onUpdate: (List<Track>) -> Unit = {}): List<Track> {
        val tracks = mutableListOf<Track>()
        var lastUpdate = 0
        scanTreeInternal(tree) { track ->
            tracks += track
            if (tracks.size - lastUpdate >= 10) {
                onUpdate(tracks.toList())
                lastUpdate = tracks.size
            }
        }
        onUpdate(tracks.toList())
        return tracks
    }

    private fun scanMediaStore(): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            folderColumn
        )
        runCatching {
            context.contentResolver.query(
                collection,
                columns,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val display = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackNum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val folder = cursor.getColumnIndexOrThrow(folderColumn)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(id))
                    val name = cursor.getString(display).orEmpty()
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext.isNotBlank() && ext !in supported) continue
                    tracks += Track(
                        id = uri.toString(),
                        uri = uri,
                        title = cursor.getString(title)?.takeUnless { it == "<unknown>" }
                            ?: name.substringBeforeLast('.'),
                        artist = cursor.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist",
                        album = cursor.getString(album)?.takeUnless { it == "<unknown>" } ?: "Unknown Album",
                        durationMs = cursor.getLong(duration),
                        mime = cursor.getString(mime),
                        extension = ext,
                        folderPath = mediaFolder(cursor.getString(folder).orEmpty(), name),
                        modifiedMs = cursor.getLong(modified) * 1_000L,
                        trackNumber = cursor.getInt(trackNum) % 1000
                    )
                }
            }
        }
        return tracks
    }


    private fun mediaFolder(raw: String, displayName: String): String {
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            raw
        } else {
            raw.substringBeforeLast('/', "")
                .removePrefix("/storage/emulated/0/")
                .removePrefix("/sdcard/")
        }
        return path.cleanFolderPath().ifBlank {
            displayName.substringBeforeLast('/', "Music").ifBlank { "Music" }
        }
    }

    private fun String.cleanFolderPath(): String = replace('\\', '/').trim('/')
}
