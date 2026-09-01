package com.hikari.app.domain.update

/**
 * Semver-Vergleich für Release-Tags der Form "vX.Y.Z".
 *
 * Führendes "v" wird gestrippt, danach werden die drei numerischen Teile
 * paarweise verglichen. Bewusst simpel gehalten — die Releases folgen immer
 * dem Schema vMajor.Minor.Patch.
 */
object VersionCompare {

    /** true, wenn [latest] streng neuer ist als [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until 3) {
            if (l[i] != c[i]) return l[i] > c[i]
        }
        return false
    }

    private fun parse(version: String): IntArray {
        val stripped = version.trim().removePrefix("v").removePrefix("V")
        val parts = stripped.split(".")
        return IntArray(3) { i -> parts.getOrNull(i)?.toIntOrNull() ?: 0 }
    }
}
