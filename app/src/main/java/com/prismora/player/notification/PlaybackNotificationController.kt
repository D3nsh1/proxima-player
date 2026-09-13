package com.prismora.player.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.prismora.player.MainActivity
import com.prismora.player.R
import com.prismora.player.model.PlaybackPhase
import com.prismora.player.model.Track

class PlaybackNotificationController(
    context: Context,
    previousAction: () -> Unit,
    playAction: () -> Unit,
    pauseAction: () -> Unit,
    togglePlayPauseAction: () -> Unit,
    nextAction: () -> Unit,
    toggleShuffleAction: () -> Unit,
    seekAction: (Long) -> Unit
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val mediaSession = MediaSession(appContext, "PrismoraPlayback")

    private var lastArtworkKey: String? = null
    private var lastArtwork: Bitmap? = null
    private var lastNotificationUpdateMs = 0L

    init {
        ensureChannel()
        PlaybackNotificationCommands.previous = previousAction
        PlaybackNotificationCommands.togglePlayPause = togglePlayPauseAction
        PlaybackNotificationCommands.next = nextAction
        PlaybackNotificationCommands.toggleShuffle = toggleShuffleAction

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = playAction()
            override fun onPause() = pauseAction()
            override fun onSkipToPrevious() = previousAction()
            override fun onSkipToNext() = nextAction()
            override fun onSeekTo(pos: Long) = seekAction(pos.coerceAtLeast(0L))
            override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                if (action == ACTION_SHUFFLE) toggleShuffleAction()
            }
        })
        mediaSession.isActive = true
    }

    fun update(
        track: Track?,
        coverBytes: ByteArray?,
        phase: PlaybackPhase,
        positionMs: Long,
        durationMs: Long,
        shuffleEnabled: Boolean
    ) {
        if (track == null || phase == PlaybackPhase.IDLE || phase == PlaybackPhase.ERROR) {
            cancel()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val force = now - lastNotificationUpdateMs >= 750L || phase != PlaybackPhase.PLAYING
        if (!force) return
        lastNotificationUpdateMs = now

        val isPlaying = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.LOADING
        val safeDuration = durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val artwork = artworkFor(track, coverBytes)

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration)
                .apply {
                    artwork?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                    }
                }
                .build()
        )

        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SEEK_TO
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_SHUFFLE,
                        if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                        R.drawable.ic_notification_shuffle
                    ).build()
                )
                .setState(state, safePosition, if (isPlaying) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )

        val contentIntent = PendingIntent.getActivity(
            appContext,
            100,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousAction = Notification.Action.Builder(
            R.drawable.ic_notification_previous,
            "Previous",
            actionIntent(ACTION_PREVIOUS, 101)
        ).build()
        val toggleAction = Notification.Action.Builder(
            if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            if (isPlaying) "Pause" else "Play",
            actionIntent(ACTION_TOGGLE, 102)
        ).build()
        val nextAction = Notification.Action.Builder(
            R.drawable.ic_notification_next,
            "Next",
            actionIntent(ACTION_NEXT, 103)
        ).build()
        val shuffleAction = Notification.Action.Builder(
            R.drawable.ic_notification_shuffle,
            if (shuffleEnabled) "Shuffle on" else "Shuffle off",
            actionIntent(ACTION_SHUFFLE, 104)
        ).build()

        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album.takeIf { it.isNotBlank() })
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(previousAction)
            .addAction(toggleAction)
            .addAction(nextAction)
            .addAction(shuffleAction)

        artwork?.let { builder.setLargeIcon(it) }
        if (safeDuration in 1..Int.MAX_VALUE.toLong()) {
            builder.setProgress(safeDuration.toInt(), safePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), false)
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    fun release() {
        cancel()
        PlaybackNotificationCommands.clear()
        mediaSession.isActive = false
        mediaSession.release()
        lastArtwork = null
        lastArtworkKey = null
    }

    private fun artworkFor(track: Track, bytes: ByteArray?): Bitmap? {
        val source = bytes ?: track.localArt
        val key = "${track.id}:${source?.contentHashCode() ?: 0}"
        if (key == lastArtworkKey) return lastArtwork
        lastArtwork = source?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        lastArtworkKey = key
        return lastArtwork
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        requestCode,
        Intent(appContext, PlaybackActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Prismora playback controls"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_PREVIOUS = "com.prismora.player.action.PREVIOUS"
        const val ACTION_TOGGLE = "com.prismora.player.action.TOGGLE"
        const val ACTION_NEXT = "com.prismora.player.action.NEXT"
        const val ACTION_SHUFFLE = "com.prismora.player.action.SHUFFLE"

        private const val CHANNEL_ID = "prismora_playback"
        const val NOTIFICATION_ID = 7310
    }
}
