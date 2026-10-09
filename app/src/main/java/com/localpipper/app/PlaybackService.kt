package com.localpipper.app

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.localpipper.app.PlaybackPositionRepository
import androidx.media3.common.MediaItem

/**
 * バックグラウンド再生を担う MediaSessionService。
 *
 * ExoPlayer を保持し、MediaSession 経由で再生制御・通知を管理する。
 * アプリがバックグラウンドに移行しても音声再生を継続する。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * アプリがタスク一覧から削除されたとき、再生位置を保存し、必要に応じてサービスを停止する。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        player?.let {
            val currentPosition = it.currentPosition
            val currentMediaItem = it.currentMediaItem
            if (currentPosition >= 0 && currentMediaItem != null) {
                val mediaId = currentMediaItem.mediaId
                if (mediaId.isNotEmpty()) {
                    val repository = PlaybackPositionRepository.getInstance(applicationContext)
                    repository.savePosition(mediaId, currentPosition)
                }
            }
        }

        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
