package roy.ij.obscure.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.RetrofitClient

class ChatListViewModel(private val api: ApiService = RetrofitClient.api) : ViewModel() {
    private val _rooms = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val rooms: StateFlow<List<Map<String, Any>>> = _rooms

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId

    fun load(token: String) {
        viewModelScope.launch {
            try {
                // 1️⃣ Get my user ID
                val meResp = api.me("Bearer $token")
                val me = meResp["user"] as? Map<*, *>
                val myUserId = me?.get("id") as? String
                _myUserId.value = myUserId

                // 2️⃣ Load rooms
                val resp = api.listMyRooms("Bearer $token")
                val arr = resp["rooms"] as? List<Map<String, Any>> ?: emptyList()
                _rooms.value = arr
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}