package com.prismora.player.notification

object PlaybackNotificationCommands {
    @Volatile var previous: (() -> Unit)? = null
    @Volatile var togglePlayPause: (() -> Unit)? = null
    @Volatile var next: (() -> Unit)? = null
    @Volatile var toggleShuffle: (() -> Unit)? = null

    fun clear() {
        previous = null
        togglePlayPause = null
        next = null
        toggleShuffle = null
    }
}
