package com.prismora.player.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handled = when (intent.action) {
            PlaybackNotificationController.ACTION_PREVIOUS -> PlaybackNotificationCommands.previous?.let { it(); true } ?: false
            PlaybackNotificationController.ACTION_TOGGLE -> PlaybackNotificationCommands.togglePlayPause?.let { it(); true } ?: false
            PlaybackNotificationController.ACTION_NEXT -> PlaybackNotificationCommands.next?.let { it(); true } ?: false
            PlaybackNotificationController.ACTION_SHUFFLE -> PlaybackNotificationCommands.toggleShuffle?.let { it(); true } ?: false
            else -> false
        }
        if (!handled) {
            context.getSystemService(NotificationManager::class.java)?.cancel(PlaybackNotificationController.NOTIFICATION_ID)
        }
    }
}
