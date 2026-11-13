package com.vknxworks.arrctrl.data.server

import android.util.Base64
import com.vknxworks.arrctrl.data.crypto.KeystoreAes
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts saved API keys and passwords with a non-exportable AES key held in the Android Keystore
 * (in StrongBox when the key is made on a phone that has it; see [KeystoreAes]).
 */
object KeystoreCrypto {
    private const val ALIAS = "arrctrl_credentials"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey = KeystoreAes.key(ALIAS)

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, 12)
            val ct = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
