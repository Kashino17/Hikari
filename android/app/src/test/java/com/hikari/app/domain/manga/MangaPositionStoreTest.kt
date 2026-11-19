package com.hikari.app.domain.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaPositionStoreTest {
    private class Mem : MangaPositionStore.Storage {
        var raw: String? = null
        override fun read() = raw
        override fun write(json: String) { raw = json }
    }

    @Test fun `gelesene Stelle wird gemerkt und ist noch nicht abgeglichen`() {
        val s = MangaPositionStore(Mem())
        s.put("s", "s:1", 6, at = 100)
        assertEquals(MangaPositionStore.Pos("s:1", 6, 100, synced = false), s.get("s"))
        assertEquals(listOf("s"), s.pending().keys.toList())
    }

    @Test fun `überlebt einen Neustart (neue Instanz, gleicher Speicher)`() {
        val mem = Mem()
        MangaPositionStore(mem).put("s", "s:1", 6, at = 100)
        assertEquals(6, MangaPositionStore(mem).get("s")?.page)
    }

    @Test fun `abgeglichen nur, wenn sich seitdem nichts geändert hat`() {
        val s = MangaPositionStore(Mem())
        s.put("s", "s:1", 6, at = 100)
        s.put("s", "s:1", 7, at = 200)
        s.markSynced("s", at = 100) // Antwort auf die ältere Speicherung
        assertEquals(false, s.get("s")?.synced)
        s.markSynced("s", at = 200)
        assertEquals(true, s.get("s")?.synced)
        assertEquals(emptyMap<String, MangaPositionStore.Pos>(), s.pending())
    }

    @Test fun `Stand vom Mac ersetzt nur einen älteren auf dem Handy`() {
        val s = MangaPositionStore(Mem())
        s.put("s", "s:1", 6, at = 200)
        s.mergeServer("s", "s:1", 2, at = 100) // älter → bleibt 6
        assertEquals(6, s.get("s")?.page)
        s.mergeServer("s", "s:2", 3, at = 300) // neuer (z. B. am Fold weitergelesen) → übernehmen
        assertEquals(MangaPositionStore.Pos("s:2", 3, 300, synced = true), s.get("s"))
        assertNull(s.get("andere"))
    }

    @Test fun `kaputter Speicher wird wie leer behandelt`() {
        val mem = Mem().apply { raw = "{kaputt" }
        assertNull(MangaPositionStore(mem).get("s"))
    }
}
