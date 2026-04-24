package com.hikari.app.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

object PreloadCoordinator {
    fun setQueue(player: ExoPlayer, upcoming: List<MediaItem>) {
        if (upcoming.isEmpty()) {
            player.clearMediaItems()
            return
        }
        player.setMediaItems(upcoming, true)
        player.prepare()
    }
}
