package com.hikari.app.domain.russian

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Anja ohne Mac (Bauplan 18.11.2026): Ist der Mac nicht erreichbar, spricht das Handy direkt mit
 * Google. Dafür liegt hier der Gemini-Schlüssel — NIE im APK (Repo und Releases sind öffentlich),
 * sondern verschlüsselt mit einem Schlüssel aus dem Android-Schlüsselspeicher, der das Gerät nicht
 * verlässt:
 *  - »mac«: unser Schlüssel, vom Mac übernommen, solange das Handy ihn erreicht,
 *  - »own«: ein eigener Schlüssel, den Kadir in den Einstellungen einträgt.
 * Welcher ohne Mac gilt, bestimmt »useOwn«. Mit Mac wird keiner davon gebraucht.
 */
object AnjaKeys {
    private const val PREFS = "hikari_anja_keys"
    private const val ALIAS = "hikari_anja_key"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12))
    }.getOrNull()

    private fun read(ctx: Context, kind: String): String? = prefs(ctx).getString(kind, null)?.let(::decrypt)

    /** kind = "mac" | "own"; leer = löschen. */
    fun save(ctx: Context, kind: String, key: String) {
        if (kind != "mac" && kind != "own") return
        val k = key.trim()
        val p = prefs(ctx).edit()
        if (k.isEmpty()) p.remove(kind) else if (k != read(ctx, kind)) p.putString(kind, encrypt(k))
        p.apply()
    }

    fun setUseOwn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("useOwn", on).apply()

    /** Der Schlüssel für Anja ohne Mac — der eigene, wenn gewählt und vorhanden, sonst unserer. */
    fun active(ctx: Context): String? {
        val own = read(ctx, "own")
        return if (prefs(ctx).getBoolean("useOwn", false) && own != null) own else read(ctx, "mac") ?: own
    }

    /** Für die Einstellungen — ohne den Schlüssel selbst, nur die letzten vier Zeichen des eigenen. */
    fun infoJson(ctx: Context): String {
        val own = read(ctx, "own")
        return JSONObject()
            .put("mac", read(ctx, "mac") != null)
            .put("own", own != null)
            .put("ownEnd", own?.takeLast(4) ?: "")
            .put("useOwn", prefs(ctx).getBoolean("useOwn", false))
            .put("ready", active(ctx) != null)
            .toString()
    }
}
