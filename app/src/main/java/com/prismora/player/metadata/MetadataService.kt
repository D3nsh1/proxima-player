package com.prismora.player.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.prismora.player.model.LyricsResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

class MetadataService(private val context: Context) {
    data class LocalMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val trackNumber: Int,
        val artwork: ByteArray?
    )

    private data class CachedLocalMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val trackNumber: Int,
        val hasArtwork: Boolean
    )

    private val gson = Gson()
    private val root = File(context.filesDir, "miku_cache").apply { mkdirs() }
    private val localDir = File(root, "local_metadata").apply { mkdirs() }
    private val artworkDir = File(root, "artwork").apply { mkdirs() }
    private val lyricsDir = File(root, "lyrics").apply { mkdirs() }

    fun readLocal(uri: Uri): LocalMetadata {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown Track"
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val trackNumber = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.toIntOrNull() ?: 0
            LocalMetadata(title, artist, album, duration, trackNumber, r.embeddedPicture)
        } finally {
            r.release()
        }
    }

    fun readLocalCached(uri: Uri, modifiedMs: Long = 0L): LocalMetadata {
        val id = key("$uri|$modifiedMs")
        val metaFile = File(localDir, "$id.json")
        val artFile = File(localDir, "$id.art")
        if (metaFile.isFile) {
            val cached = runCatching { gson.fromJson(metaFile.readText(), CachedLocalMetadata::class.java) }.getOrNull()
            if (cached != null) {
                val art = if (cached.hasArtwork && artFile.isFile) runCatching { artFile.readBytes() }.getOrNull() else null
                return LocalMetadata(cached.title, cached.artist, cached.album, cached.durationMs, cached.trackNumber, art)
            }
        }

        val fresh = readLocal(uri)
        runCatching {
            metaFile.writeText(
                gson.toJson(
                    CachedLocalMetadata(
                        title = fresh.title,
                        artist = fresh.artist,
                        album = fresh.album,
                        durationMs = fresh.durationMs,
                        trackNumber = fresh.trackNumber,
                        hasArtwork = fresh.artwork?.isNotEmpty() == true
                    )
                )
            )
            if (fresh.artwork != null && fresh.artwork.isNotEmpty()) artFile.writeBytes(fresh.artwork)
        }
        return fresh
    }

    fun lyrics(title: String, artist: String, album: String, durationSec: Int): LyricsResult? {
        val cache = File(lyricsDir, "lyrics-${key("$artist|$title|$album")}.json")
        if (cache.isFile) return runCatching { parseLyrics(cache.readText(), durationSec) }.getOrNull()
        return try {
            val q = listOf("track_name" to title, "artist_name" to artist, "album_name" to album)
                .joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }
            val url = URL("https://lrclib.net/api/search?$q")
            val c = url.openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "MikuGlassPlayer/3.2 (Android)")
            c.connectTimeout = 7000
            c.readTimeout = 7000
            if (c.responseCode !in 200..299) return null
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            cache.writeText(txt)
            parseLyrics(txt, durationSec)
        } catch (_: Exception) {
            null
        }
    }

    fun artwork(title: String, artist: String): ByteArray? {
        val cache = File(artworkDir, "cover-${key("$artist|$title")}.jpg")
        if (cache.isFile) return runCatching { cache.readBytes() }.getOrNull()
        return try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=5")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 7000
            c.readTimeout = 7000
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val results = JsonParser.parseString(txt).asJsonObject.getAsJsonArray("results")
            val remote = results.firstOrNull()?.asJsonObject?.get("artworkUrl100")?.asString
                ?.replace("100x100", "600x600") ?: return null
            val bytes = URL(remote).openConnection().apply {
                connectTimeout = 7000
                readTimeout = 7000
            }.getInputStream().use { it.readBytes() }
            if (bytes.isNotEmpty()) cache.writeBytes(bytes)
            bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLyrics(json: String, durationSec: Int): LyricsResult? {
        val arr = JsonParser.parseString(json).asJsonArray
        val best = arr.minByOrNull { e ->
            val d = e.asJsonObject.get("duration")?.takeIf { !it.isJsonNull }?.asInt ?: durationSec
            kotlin.math.abs(d - durationSec)
        } ?: return null
        val o = best.asJsonObject
        return LyricsResult(
            o.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString,
            o.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    private fun key(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.lowercase().toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
