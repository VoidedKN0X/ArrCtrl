package com.vknxworks.arrctrl.data.settings

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a configuration backup with a password the user chooses: the key comes from the password
 * with PBKDF2-HMAC-SHA256 and a random salt, and the content is sealed with AES-256-GCM. The file is a
 * few lines of text:
 *
 * ```
 * ARRCTRL-BACKUP1
 * <iterations>
 * <salt, base64>
 * <nonce, base64>
 * <ciphertext and tag, base64>
 * ```
 *
 * Nothing but the password opens it; a wrong password or a changed file is noticed (GCM), never half-read.
 * No Android classes here, so it can be unit-tested on its own.
 */
object BackupCrypto {
    const val MAGIC = "ARRCTRL-BACKUP1"
    const val MIN_PASSWORD = 8
    private const val ITERATIONS = 310_000
    /** Files claiming more rounds than this are refused, so a made-up file can't keep the phone busy for minutes. */
    private const val MAX_ITERATIONS = 5_000_000
    private const val MIN_ITERATIONS = 100_000

    class NotABackup : Exception()
    class WrongPassword : Exception()

    fun looksLikeBackup(text: String): Boolean = text.trimStart().startsWith(MAGIC)

    fun seal(plain: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
        val sealed = cipher.doFinal(plain)
        val b64 = Base64.getEncoder()
        return listOf(MAGIC, ITERATIONS.toString(), b64.encodeToString(salt), b64.encodeToString(nonce), b64.encodeToString(sealed)).joinToString("\n") + "\n"
    }

    /** The content of a backup file. Throws [NotABackup] for another kind of file and [WrongPassword] when it won't open. */
    fun open(text: String, password: CharArray): ByteArray {
        val lines = text.trim().lines().map { it.trim() }
        if (lines.size != 5 || lines[0] != MAGIC) throw NotABackup()
        val iterations = lines[1].toIntOrNull()?.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS } ?: throw NotABackup()
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(lines[2]) }.getOrNull()?.takeIf { it.size == 16 } ?: throw NotABackup()
        val nonce = runCatching { b64.decode(lines[3]) }.getOrNull()?.takeIf { it.size == 12 } ?: throw NotABackup()
        val sealed = runCatching { b64.decode(lines[4]) }.getOrNull()?.takeIf { it.size >= 16 } ?: throw NotABackup()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, nonce))
            cipher.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
            cipher.doFinal(sealed)
        } catch (e: java.security.GeneralSecurityException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
