package com.hikari.app.domain.download

import org.junit.Test
import kotlin.test.assertEquals

class SmartBudgetPolicyTest {
    private fun item(id: String, bytes: Long, at: Long) = SmartItem(id, bytes, at, isSong = false)

    @Test fun nothing_is_removed_while_there_is_room() {
        val items = listOf(item("a", 40, 1), item("b", 40, 2))
        assertEquals(emptyList(), SmartBudgetPolicy.toEvict(items, budgetBytes = 100, wanted = emptySet(), playing = null))
    }

    @Test fun oldest_unneeded_downloads_go_first_and_only_as_many_as_needed() {
        val items = listOf(item("old", 50, 1), item("mid", 50, 2), item("new", 50, 3))
        val evicted = SmartBudgetPolicy.toEvict(items, budgetBytes = 120, wanted = emptySet(), playing = null)
        assertEquals(listOf("old"), evicted.map { it.id })
    }

    @Test fun downloads_still_needed_or_playing_are_never_removed() {
        val items = listOf(item("next-episode", 50, 1), item("playing", 50, 2), item("watched", 50, 3))
        val evicted = SmartBudgetPolicy.toEvict(
            items,
            budgetBytes = 60,
            wanted = setOf("next-episode"),
            playing = "playing",
        )
        assertEquals(listOf("watched"), evicted.map { it.id })
    }
}
