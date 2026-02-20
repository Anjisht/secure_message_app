package roy.ij.obscure.data.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object CryptoHelper {
    private const val RSA_MODE = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "baatcheet_rsa_e2ee"
    private val oaepParams = OAEPParameterSpec(
        "SHA-256",                  // main digest
        "MGF1",
        MGF1ParameterSpec.SHA1,     // ⚠️ Android quirk: must be SHA-1 for MGF1
        PSource.PSpecified.DEFAULT
    )

    // Generate random AES key
    fun generateAesKey(): SecretKey {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        return gen.generateKey()
    }

    // Encrypt content with AES/GCM
    fun encryptAes(plaintext: String, secret: SecretKey): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    // Decrypt with AES/GCM
    fun decryptAes(ciphertextB64: String, ivB64: String, secret: SecretKey): String {
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, secret, spec)
        val plain = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
        return String(plain)
    }

    // Wrap AES key with someone’s RSA public key (Base64 string from backend)
    fun wrapAesKey(secret: SecretKey, recipientPublicKeyB64: String): String {
        val pubBytes = Base64.decode(recipientPublicKeyB64, Base64.NO_WRAP)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pubBytes))
        val cipher = Cipher.getInstance(RSA_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepParams)
        val enc = cipher.doFinal(secret.encoded)
        return Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    // Unwrap AES key with our RSA private key from Keystore
    fun unwrapAesKey(encKeyB64: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val priv = ks.getKey(KEY_ALIAS, null)
        val cipher = Cipher.getInstance(RSA_MODE)
        cipher.init(Cipher.DECRYPT_MODE, priv, oaepParams)
        val decoded = cipher.doFinal(Base64.decode(encKeyB64, Base64.NO_WRAP))
        return javax.crypto.spec.SecretKeySpec(decoded, "AES")
    }

    fun encryptBytes(plain: ByteArray, secret: SecretKey): Pair<ByteArray, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return Pair(encrypted, Base64.encodeToString(iv, Base64.NO_WRAP))
    }

    fun decryptBytes(cipher: ByteArray, ivB64: String, secret: SecretKey): ByteArray {
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipherInst = Cipher.getInstance("AES/GCM/NoPadding")
        cipherInst.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        return cipherInst.doFinal(cipher)
    }
}
