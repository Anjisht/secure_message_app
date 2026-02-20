package roy.ij.obscure.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(context: Context) {

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val sharedPreferences = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    private fun getMasterKey(): SecretKey {
        val keyAlias = "database_master_key"
        return if (keystore.containsAlias(keyAlias)) {
            keystore.getKey(keyAlias, null) as SecretKey
        } else {
            generateMasterKey(keyAlias)
        }
    }

    private fun generateMasterKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val keySpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
        }.build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateEncryptedPassphrase(): ByteArray {
        val encryptedPassphrase = sharedPreferences.getString("db_passphrase", null)
        val iv = sharedPreferences.getString("db_iv", null)

        return if (encryptedPassphrase != null && iv != null) {
            // Decrypt existing passphrase
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, Base64.getDecoder().decode(iv))
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
            cipher.doFinal(Base64.getDecoder().decode(encryptedPassphrase))
        } else {
            // Generate, encrypt, and store a new one
            val newPassphrase = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

            val encrypted = cipher.doFinal(newPassphrase)
            sharedPreferences.edit()
                .putString("db_passphrase", Base64.getEncoder().encodeToString(encrypted))
                .putString("db_iv", Base64.getEncoder().encodeToString(cipher.iv))
                .apply()
            newPassphrase
        }
    }

    fun getDatabasePassphrase(): ByteArray {
        return getOrCreateEncryptedPassphrase()
    }
}