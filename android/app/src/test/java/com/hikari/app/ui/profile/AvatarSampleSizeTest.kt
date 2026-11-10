package com.hikari.app.ui.profile

import kotlin.test.assertEquals
import org.junit.Test

class AvatarSampleSizeTest {
    @Test fun kleineBilder_bleibenWieSieSind() {
        assertEquals(1, ProfileViewModel.avatarSampleSize(800, 600))
        assertEquals(1, ProfileViewModel.avatarSampleSize(1024, 1024))
        assertEquals(1, ProfileViewModel.avatarSampleSize(2047, 2047))
    }

    @Test fun grosseFotos_werdenInZweierschrittenVerkleinert() {
        assertEquals(2, ProfileViewModel.avatarSampleSize(2048, 2048))
        // 12-Megapixel-Handyfoto: 4000×3000 → 2000×1500, die kurze Kante bleibt über 1024.
        assertEquals(2, ProfileViewModel.avatarSampleSize(4000, 3000))
        assertEquals(8, ProfileViewModel.avatarSampleSize(8192, 8192))
    }

    @Test fun schmaleBilder_richtenSichNachDerKurzenKante() {
        assertEquals(1, ProfileViewModel.avatarSampleSize(6000, 1200))
    }
}
