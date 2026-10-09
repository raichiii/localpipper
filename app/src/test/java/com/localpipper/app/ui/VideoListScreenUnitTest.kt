package com.localpipper.app.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VideoListScreenUnitTest {

    @Before
    fun setUp() {
        // Set default locale to Japan for consistent date formatting
        Locale.setDefault(Locale.JAPAN)
    }

    @Test
    fun `formatDuration should format milliseconds correctly`() {
        // Test cases: (input ms, expected output)
        val testCases = listOf(
            0L to "0:00",
            500L to "0:00",   // 0.5 seconds -> 0 seconds
            1000L to "0:01",
            1500L to "0:01",  // 1.5 seconds -> 1 second
            60000L to "1:00",
            61000L to "1:01",
            3600000L to "1:00:00",
            3661000L to "1:01:01", // 1 hour, 1 minute, 1 second
            7265000L to "2:01:05"  // 2 hours, 1 minute, 5 seconds
        )

        for ((input, expected) in testCases) {
            val result = formatDuration(input)
            assertEquals("Failed for input $input ms", expected, result)
        }
    }

    @Test
    fun `formatDate should format epoch seconds correctly`() {
        // Epoch seconds for 2023-01-01 00:00:00 UTC
        // In Japan timezone (UTC+9), this is 2023-01-01 09:00:00, but the date part is still 2023-01-01
        val epochSec = 1672531200L // 2023-01-01 00:00:00 UTC
        val expected = "2023/01/01"
        val result = formatDate(epochSec)
        assertEquals(expected, result)

        // Test another date: 2024-02-29 12:00:00 UTC (leap year)
        val epochSec2 = 1709188800L // 2024-02-29 12:00:00 UTC
        val expected2 = "2024/02/29"
        val result2 = formatDate(epochSec2)
        assertEquals(expected2, result2)
    }
}