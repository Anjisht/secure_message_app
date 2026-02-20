package roy.ij.obscure.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.AEADBadTagException

object SecureStore {
    private const val PREF_NAME = "secure_prefs"

    private fun prefs(ctx: Context): EncryptedSharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: AEADBadTagException) {
            // ❗ corrupted keyset or prefs – clear and rebuild
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: Exception) {
            // fallback catch to handle all crypto exceptions
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        }
    }

    fun saveUsername(ctx: Context, username: String) {
        prefs(ctx).edit().putString("username", username).apply()
    }

    fun getUsername(ctx: Context): String? = prefs(ctx).getString("username", null)

    fun saveTokenBlob(ctx: Context, blobB64: String) {
        prefs(ctx).edit().putString("token_blob", blobB64).apply()
    }

    fun getTokenBlob(ctx: Context): String? = prefs(ctx).getString("token_blob", null)

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun saveBiometricEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("bio_enabled", enabled).apply()
    }

    fun isBiometricEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("bio_enabled", false)

    fun clearBlob(ctx: Context) {
        prefs(ctx).edit().remove("token_blob").apply()
    }
}