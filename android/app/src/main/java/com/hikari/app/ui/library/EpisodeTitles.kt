package com.hikari.app.ui.library

/**
 * Anzeige von Folgentiteln in Listen.
 *
 * Der Server speichert Folgen ohne eigenen Namen einheitlich als „Folge N".
 * Eine Liste, die die Nummer ohnehin voranstellt, zeigte sonst „3. Folge 3".
 */
object EpisodeTitles {
    private val PLAIN = Regex("""^(?:folge|episode)\s+\d{1,4}$""", RegexOption.IGNORE_CASE)

    fun isPlain(title: String): Boolean = PLAIN.matches(title.trim())

    /** „3. Der Anfang" — oder schlicht „Folge 3", wenn es keinen echten Titel gibt. */
    fun listTitle(episode: Int?, title: String): String = when {
        isPlain(title) -> if (episode != null) "Folge $episode" else title
        episode != null -> "$episode. $title"
        else -> title
    }
}
