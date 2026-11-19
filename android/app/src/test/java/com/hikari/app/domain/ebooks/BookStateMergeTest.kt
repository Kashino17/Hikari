package com.hikari.app.domain.ebooks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookStateMergeTest {
    @Serializable
    private data class Case(val name: String, val a: BookState, val b: BookState, val expected: BookState)

    private val cases: List<Case> = Json { ignoreUnknownKeys = true }.decodeFromString(
        javaClass.classLoader!!.getResource("ebooks/merge-cases.json")!!.readText(),
    )

    @Test fun gemeinsameFaelleWieAufDemServer() {
        assertTrue(cases.size >= 6)
        for (c in cases) assertEquals(c.expected, mergeState(c.a, c.b), c.name)
    }
}
