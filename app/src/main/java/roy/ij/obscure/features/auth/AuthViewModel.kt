package roy.ij.obscure.features.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import roy.ij.obscure.data.network.RetrofitClient


data class AuthState(
    val isLoading: Boolean = false,
    val token: String? = null,
    val error: String? = null
)

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(RetrofitClient.api)
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    fun login(ctx: Context, username: String, password: String) {
        _state.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                val resp = repo.login(username, password)
                _state.value = AuthState(isLoading = false)
                println("inside login function")
                afterAuthSuccess(resp.token)
                onAuthSuccess(ctx, username, resp.token)
                roy.ij.obscure.data.AuthSession.onLogin(resp.token)
            } catch (e: Exception) {
                val errorMsg = if (e is HttpException) {
                    try {
                        val body = e.response()?.errorBody()?.string()
                        JSONObject(body ?: "").optString("error", "Unexpected error")
                    } catch (_: Exception) {
                        "Network or server error"
                    }
                } else {
                    e.message ?: "Unknown error"
                }
                _state.value = AuthState(error = errorMsg)
            }
        }
    }

    fun register(ctx: Context, username: String, password: String) {
        _state.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                val resp = repo.register(username, password)
                _state.value = AuthState(isLoading = false)
                afterAuthSuccess(resp.token)
                onAuthSuccess(ctx, username, resp.token)


                roy.ij.obscure.data.AuthSession.onLogin(resp.token)
            } catch (e: Exception) {
                val errorMsg = if (e is HttpException) {
                    try {
                        val body = e.response()?.errorBody()?.string()
                        JSONObject(body ?: "").optString("error", "Unexpected error")
                    } catch (_: Exception) {
                        "Network or server error"
                    }
                } else {
                    e.message ?: "Unknown error"
                }
                _state.value = AuthState(error = errorMsg)
            }
        }
    }

    private fun afterAuthSuccess(token: String) {
        _state.value = _state.value.copy(token = token)

        viewModelScope.launch {
            try {
                // Ensure keypair exists
                roy.ij.obscure.data.crypto.KeyManager.ensureKeyPair()
//                println("inside afterAuthSuccess function")

                // 🔑 Debug print
                val fingerprint = roy.ij.obscure.data.crypto.KeyManager.debugKeyFingerprint()
//                println("🔑 Current key fingerprint: $fingerprint")

                // Export public key
                val pubB64 = roy.ij.obscure.data.crypto.KeyManager.exportPublicKeyBase64()

                // Upload to backend
//                println("inside upload to backend")
                repo.uploadPublicKey(token, pubB64)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = _state.value.copy(error = "Key upload failed (will retry)")
            }
        }
    }

    fun onAuthSuccess(ctx: Context, username: String, token: String) {
        // Save locally
        roy.ij.obscure.security.SecureStore.saveUsername(ctx, username)

        // Token will be stored encrypted later if user enables biometric
        _state.value = _state.value.copy(token = token)
    }

}