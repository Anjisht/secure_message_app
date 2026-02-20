package roy.ij.obscure.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun promptToEncryptAndStore(ctx: FragmentActivity, token: String, onDone: (Boolean) -> Unit) {
    BiometricLock.ensureKey(ctx)
    val cipher = BiometricLock.initEncryptCipher()
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Enable biometric lock")
        .setSubtitle("Use biometric to unlock next time")
        .setNegativeButtonText("Cancel")
        .build()
    val prompt = BiometricPrompt(ctx, ContextCompat.getMainExecutor(ctx),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                val blob = BiometricLock.encryptTokenWithCipher(token, c)
                SecureStore.saveTokenBlob(ctx, blob)
                SecureStore.saveBiometricEnabled(ctx, true)
                onDone(true)
            }
            override fun onAuthenticationError(code: Int, errString: CharSequence) { onDone(false) }
            override fun onAuthenticationFailed() { onDone(false) }
        })
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}

fun promptToDecryptAndUnlock(ctx: FragmentActivity, blobB64: String?, onToken: (String?) -> Unit) {
    if (blobB64.isNullOrBlank()) { onToken(null); return }
    BiometricLock.ensureKey(ctx)
    val (iv, _) = BiometricLock.unpackBlob(blobB64)
    val cipher = BiometricLock.initDecryptCipher(iv)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock")
        .setSubtitle("Authenticate to open chats")
        .setNegativeButtonText("Use password")
        .build()
    val prompt = BiometricPrompt(ctx, ContextCompat.getMainExecutor(ctx),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                val token = BiometricLock.decryptBlobWithCipher(blobB64, c)
                onToken(token)
            }
            override fun onAuthenticationError(code: Int, errString: CharSequence) { onToken(null) }
            override fun onAuthenticationFailed() { onToken(null) }
        })
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}