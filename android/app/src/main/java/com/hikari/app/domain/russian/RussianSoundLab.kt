package com.hikari.app.domain.russian

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/** Ein Wort eines Minimalpaars; [ru] trägt die Betonung als "+" vor dem Vokal. */
@Serializable
data class RuLabWord(val ru: String, val de: String) {
    /** Betonung als Akzent (за+мок → за́мок) zum Anzeigen. */
    val display: String get() = RussianPhonetics.display(ru)
    val plain: String get() = RussianPhonetics.plain(ru)
    /** Dateiname des Clips (siehe tools/russian_audio/generate_sounds.py). */
    val clip: String get() = "lab_" + sha1(ru).take(10)
}

@Serializable
data class RuLabPair(val id: String, val a: RuLabWord, val b: RuLabWord)

/** Eine Lautgruppe (z. B. »ы oder и?«) mit Merktipp und Minimalpaaren. */
@Serializable
data class RuLabGroup(val id: String, val title: String, val tip: String, val pairs: List<RuLabPair>) {
    /** Betonungspaare haben gleiche Buchstaben — Spracherkennung kann sie nicht trennen. */
    val isStress: Boolean get() = id == "stress"
}

@Serializable
data class RuSoundLabData(val version: Int = 1, val groups: List<RuLabGroup> = emptyList())

private fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

@Singleton
class RussianSoundLabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val groups: List<RuLabGroup> by lazy {
        runCatching {
            context.assets.open("russian/sounds.json").bufferedReader().use {
                ruJson.decodeFromString(RuSoundLabData.serializer(), it.readText()).groups
            }
        }.getOrDefault(emptyList())
    }
}
