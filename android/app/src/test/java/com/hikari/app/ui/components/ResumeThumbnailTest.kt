package com.hikari.app.ui.components

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ResumeThumbnailTest {

    @Test
    fun ohneProgressBleibtDasNormaleThumbnail() {
        assertEquals(
            "/covers/v1.jpg",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", null, 1200),
        )
    }

    @Test
    fun progressNullOhneThumbnailBleibtNull() {
        assertNull(resumeAwareThumbnail("v1", null, null, 1200))
    }

    @Test
    fun nullSekundenIstNochKeinResume() {
        assertEquals(
            "/covers/v1.jpg",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 0f, 1200),
        )
    }

    @Test
    fun angefangenesVideoLiefertFrameAnDerStoppPosition() {
        assertEquals(
            "/videos/v1/frame?at=300",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 300f, 1200),
        )
    }

    @Test
    fun sekundenWerdenAufGanzeZahlGekuerzt() {
        // Der Server rundet ohnehin auf 5er-Buckets — Nachkommastellen fliegen raus.
        assertEquals(
            "/videos/v1/frame?at=300",
            resumeAwareThumbnail("v1", null, 300.9f, 1200),
        )
    }

    @Test
    fun fastFertigGeschautGiltAlsFertig() {
        // >= 95 % geschaut → wieder das normale Thumbnail, kein Frame am Abspann.
        assertEquals(
            "/covers/v1.jpg",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 1150f, 1200),
        )
        assertEquals(
            "/covers/v1.jpg",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 1200f, 1200),
        )
    }

    @Test
    fun knappVorDerGrenzeIstNochResume() {
        assertEquals(
            "/videos/v1/frame?at=1139",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 1139.9f, 1200),
        )
    }

    @Test
    fun ohneDauerKeinFrame() {
        // duration_seconds <= 0: keine sinnvolle Position ableitbar.
        assertEquals(
            "/covers/v1.jpg",
            resumeAwareThumbnail("v1", "/covers/v1.jpg", 300f, 0),
        )
    }
}
