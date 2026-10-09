package com.localpipper.app

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PlaybackPositionRepositoryTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: PlaybackPositionRepository

    @Before
    fun setUp() {
        mockPrefs = mockk()
        mockEditor = mockk()
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.apply() } returns Unit
        // We will stub putLong and getLong in each test
    }

    @After
    fun tearDown() {
        // Clear any static state if any (not needed here)
    }

    @Test
    fun `savePosition should call putLong with correct key and value`() {
        val videoUri = "content://media/external/video/media/123"
        val positionMs = 5000L
        val expectedKey = "video_position_content_media_external_video_media_123"

        // Stub the putLong for this specific key and value to return the editor (since putLong returns the editor for chaining)
        every { mockEditor.putLong(expectedKey, positionMs) } returns mockEditor

        repository = PlaybackPositionRepository(mockPrefs)
        repository.savePosition(videoUri, positionMs)

        verify {
            mockEditor.putLong(expectedKey, positionMs)
            Unit
        }
        verify {
            mockEditor.apply()
            Unit
        }
    }

    @Test
    fun `getPosition should return saved position`() {
        val videoUri = "content://media/external/video/media/456"
        val positionMs = 10000L
        val key = "video_position_content_media_external_video_media_456"

        // Stub the getLong for this specific key to return the positionMs
        every { mockPrefs.getLong(key, -1L) } returns positionMs

        repository = PlaybackPositionRepository(mockPrefs)
        val result = repository.getPosition(videoUri)

        assertEquals(positionMs, result)
    }

    @Test
    fun `getPosition should return -1L when no position saved`() {
        val videoUri = "content://media/external/video/media/789"
        val key = "video_position_content_media_external_video_media_789"

        // Stub the getLong for this specific key to return -1L
        every { mockPrefs.getLong(key, -1L) } returns -1L

        repository = PlaybackPositionRepository(mockPrefs)
        val result = repository.getPosition(videoUri)

        assertEquals(-1L, result)
    }
}