package roy.ij.obscure.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64   // for API >= 26
import android.util.Base64 as ABase64 // fallback for <26

object KeyManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "baatcheet_rsa_e2ee"

    /** Create if missing, else return existing pair (public only is exported). */
    fun ensureKeyPair(): KeyPair {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.getCertificate(KEY_ALIAS)?.publicKey?.let { pub ->
            val priv = ks.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            if (priv != null) return KeyPair(pub, priv)
        }

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
        )
            .setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(2048, java.math.BigInteger.valueOf(65537)))
            .setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            // we’ll use RSA/ECB/OAEPWithSHA-256AndMGF1Padding for wrapping AES keys later
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false) // set true if you want biometrics gate
            .build()

        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    /** Export X.509 SubjectPublicKeyInfo (DER) as Base64 (PEM without headers). */
    fun exportPublicKeyBase64(): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(KEY_ALIAS) ?: throw IllegalStateException("Key missing")
        val der = cert.publicKey.encoded
        return if (Build.VERSION.SDK_INT >= 26) {
            Base64.getEncoder().encodeToString(der)
        } else {
            ABase64.encodeToString(der, ABase64.NO_WRAP)
        }
    }

    fun debugKeyFingerprint(): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(KEY_ALIAS)
            ?: return "❌ No keypair found"

        val pubBytes = cert.publicKey.encoded
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pubBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasKey(): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.containsAlias(KEY_ALIAS)
    }
}