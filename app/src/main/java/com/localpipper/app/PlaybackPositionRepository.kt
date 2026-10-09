package com.localpipper.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Repository for saving and restoring playback positions using SharedPreferences.
 */
class PlaybackPositionRepository internal constructor(private val prefs: SharedPreferences) {

    companion object {
        @Volatile
        private var INSTANCE: PlaybackPositionRepository? = null
        private const val PREFIX = "video_position_"
        private const val KEY_LAST_VIDEO_URI = "last_video_uri"

        fun getInstance(context: Context): PlaybackPositionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) instance else {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    val repository = PlaybackPositionRepository(prefs)
                    INSTANCE = repository
                    repository
                }
            }
        }
    }

    /**
     * Saves the playback position for the given video URI.
     */
    fun savePosition(videoUri: String, positionMs: Long) {
        val key = PREFIX + videoUri.replace("/", "_").replace(":", "_")
        Log.d("LocalPipperPos", "Saving position for key=$key, positionMs=$positionMs")
        prefs.edit().putLong(key, positionMs).commit()
    }

    /**
     * Retrieves the saved playback position for the given video URI.
     * Returns -1L if no position is saved.
     */
    fun getPosition(videoUri: String): Long {
        val key = PREFIX + videoUri.replace("/", "_").replace(":", "_")
        return prefs.getLong(key, -1L)
    }

    /**
     * Saves the last video URI.
     */
    fun saveLastVideoUri(uri: String) {
        prefs.edit().putString(KEY_LAST_VIDEO_URI, uri).commit()
    }

    /**
     * Retrieves the last video URI.
     * Returns null if no URI is saved.
     */
    fun getLastVideoUri(): String? {
        return prefs.getString(KEY_LAST_VIDEO_URI, null)
    }
}