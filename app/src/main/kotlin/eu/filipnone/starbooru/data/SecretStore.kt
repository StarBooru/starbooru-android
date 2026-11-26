package eu.filipnone.starbooru.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the session's token is kept. */
interface SecretStore {
    fun read(): String?

    fun write(secret: String?)
}

/**
 * The token, encrypted with an AES key that never leaves the Android
 * Keystore. A key that's gone (a restored backup, a reset lock screen) or a
 * file that doesn't decrypt reads as no token: the app asks to sign in again.
 */
class KeystoreSecretStore(private val file: File) : SecretStore {
    override fun read(): String? =
        try {
            val bytes = file.takeIf { it.exists() }?.readBytes()
            bytes?.let(::decrypt)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    override fun write(secret: String?) {
        if (secret == null) {
            file.delete()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val sealed = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + sealed)
            if (!tmp.renameTo(file)) file.writeBytes(tmp.readBytes()).also { tmp.delete() }
        } catch (_: GeneralSecurityException) {
            file.delete()
        } catch (_: java.io.IOException) {
            file.delete()
        }
    }

    private fun decrypt(bytes: ByteArray): String? {
        val ivSize = bytes.firstOrNull()?.toInt() ?: return null
        if (ivSize <= 0 || bytes.size <= 1 + ivSize) return null
        val iv = bytes.copyOfRange(1, 1 + ivSize)
        val sealed = bytes.copyOfRange(1 + ivSize, bytes.size)
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun key(): SecretKey =
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "starbooru-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

/** For tests, and for a device where the Keystore isn't there. */
class MemorySecretStore(private var secret: String? = null) : SecretStore {
    override fun read(): String? = secret

    override fun write(secret: String?) {
        this.secret = secret
    }
}
