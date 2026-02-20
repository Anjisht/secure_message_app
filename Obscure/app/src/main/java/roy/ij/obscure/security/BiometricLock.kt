package roy.ij.obscure.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import javax.crypto.spec.IvParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

object BiometricLock {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bc_bio_aes"

    fun ensureKey(ctx: Context) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) return

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // require strong biometric each time (0s validity)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(false) // set true if device supports StrongBox and you want it
        }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(builder.build())
        kg.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun initEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return cipher
    }

    fun initDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    /** encrypt token with a cipher from BiometricPrompt onSuccess */
    fun encryptTokenWithCipher(token: String, cipher: Cipher): String {
        val enc = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val pack = ByteArray(iv.size + enc.size)
        System.arraycopy(iv, 0, pack, 0, iv.size)
        System.arraycopy(enc, 0, pack, iv.size, enc.size)
        return Base64.encodeToString(pack, Base64.NO_WRAP)
    }

    /** decrypt blob when BiometricPrompt returns a decrypt Cipher */
    fun decryptBlobWithCipher(blobB64: String, cipher: Cipher): String {
        val pack = Base64.decode(blobB64, Base64.NO_WRAP)
        // pack already includes IV if cipher initialised with same IV; with CryptoObject::cipher,
        // you typically pass cipher pre-init with IV, so just doFinal on ciphertext portion:
        val ivLen = 12 // GCM 12 bytes IV
        val enc = pack.copyOfRange(ivLen, pack.size)
        return String(cipher.doFinal(enc), Charsets.UTF_8)
    }

    /** convenience: split blob to iv+ciphertext when setting decrypt cipher */
    fun unpackBlob(blobB64: String): Pair<ByteArray, ByteArray> {
        val pack = Base64.decode(blobB64, Base64.NO_WRAP)
        val iv = pack.copyOfRange(0, 12)
        val enc = pack.copyOfRange(12, pack.size)
        return iv to enc
    }
}