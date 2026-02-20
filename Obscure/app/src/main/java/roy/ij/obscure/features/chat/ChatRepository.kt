package roy.ij.obscure.features.chat

import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.RetrofitClient


class ChatRepository(private val api: ApiService = RetrofitClient.api) {
    suspend fun me(token: String) =
        api.me("Bearer $token")

    suspend fun members(token: String, roomId: String) =
        api.getRoomMembers("Bearer $token", roomId)

    suspend fun history(token: String, roomId: String) =
        api.getHistory("Bearer $token", roomId)
}