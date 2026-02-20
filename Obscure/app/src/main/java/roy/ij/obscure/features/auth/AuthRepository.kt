package roy.ij.obscure.features.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.AuthRequest
import roy.ij.obscure.data.network.PublicKeyRequest

class AuthRepository(private val api: ApiService) {

    suspend fun register(username: String, password: String) =
        withContext(Dispatchers.IO) {
            Log.d("AuthRepository", "Registering user: $username")
            val response = api.register(AuthRequest(username, password))
            Log.d("AuthRepository", "Register response: $response")
            response
        }

    suspend fun login(username: String, password: String) =
        withContext(Dispatchers.IO) {
            Log.d("AuthRepository", "Logging in user: $username")
            val response = api.login(AuthRequest(username, password))
            Log.d("AuthRepository", "Login response: $response")
            response
        }

    suspend fun uploadPublicKey(token: String, publicKeyB64: String) =
        withContext(Dispatchers.IO) {
            Log.d("AuthRepository", "Uploading public key for token: ${token.take(15)}..., key: ${publicKeyB64.take(30)}...")
            val response = api.setPublicKey("Bearer $token", PublicKeyRequest(publicKeyB64))
            Log.d("AuthRepository", "Upload public key response: $response")
            response
        }
}