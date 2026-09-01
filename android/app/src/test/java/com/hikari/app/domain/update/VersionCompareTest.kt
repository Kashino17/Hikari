package com.hikari.app.domain.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {

    @Test
    fun `neuere Patch-Version wird erkannt`() {
        assertTrue(VersionCompare.isNewer("0.78.2", "0.78.1"))
    }

    @Test
    fun `neuere Minor-Version wird erkannt`() {
        assertTrue(VersionCompare.isNewer("0.79.0", "0.78.9"))
    }

    @Test
    fun `neuere Major-Version wird erkannt`() {
        assertTrue(VersionCompare.isNewer("1.0.0", "0.99.9"))
    }

    @Test
    fun `gleiche Version ist kein Update`() {
        assertFalse(VersionCompare.isNewer("0.78.1", "0.78.1"))
    }

    @Test
    fun `aelltere Version ist kein Update`() {
        assertFalse(VersionCompare.isNewer("0.77.9", "0.78.1"))
    }

    @Test
    fun `fuehrendes v wird gestrippt`() {
        assertTrue(VersionCompare.isNewer("v0.79.0", "0.78.1"))
    }

    @Test
    fun `zweistellige Teile werden numerisch verglichen`() {
        assertTrue(VersionCompare.isNewer("0.80.0", "0.79.9"))
        assertFalse(VersionCompare.isNewer("0.9.0", "0.10.0"))
    }
}
