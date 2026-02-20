package roy.ij.obscure.features.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import roy.ij.obscure.features.chat.RoomState
import roy.ij.obscure.data.network.*

class RoomViewModel(
    private val api: ApiService = RetrofitClient.api
) : ViewModel() {

    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state

    private var token: String? = null
    fun setToken(t: String) {
        token = t
    }

    // 🔹 Added: room info flow for admin/pending view
    private val _info = MutableStateFlow<RoomInfoResp?>(null)
    val info: StateFlow<RoomInfoResp?> = _info

    // 🔹 Added: fetch full room info
    suspend fun refresh(roomId: String) {
        val t = token ?: return
        _info.value = api.roomInfo("Bearer $t", roomId)
    }

    // 🔹 Added: approve & deny actions
    fun approve(roomId: String, memberId: String) {
        val t = token ?: return
        viewModelScope.launch {
            api.approve("Bearer $t", ApproveReq(roomId, memberId))
            refresh(roomId)
        }
    }

    fun deny(roomId: String, memberId: String) {
        val t = token ?: return
        viewModelScope.launch {
            api.denyMember("Bearer $t", mapOf("roomId" to roomId, "memberId" to memberId))
            refresh(roomId)
        }
    }

    fun createRoom(code: String?, duration: Int?) {
        val bearer = "Bearer ${roy.ij.obscure.data.AuthSession.token ?: return}"
        _state.value = RoomState(isLoading = true)
        Log.d("RoomViewModel", "ABCDEFG Creating room with code $code and duration $duration")
        viewModelScope.launch {
            try {
                Log.d("RoomViewModel", "ABCD Creating room with code $code and duration $duration")
                val resp = api.createRoom(bearer, CreateRoomReq(code, duration))
                _state.value = RoomState(
                    isLoading = false,
                    roomId = resp.roomId,
                    alias = resp.alias,
                    message = "Room created: ${resp.roomId}"
                )
            } catch (e: Exception) {
                _state.value = RoomState(isLoading = false, error = e.message)
            }
        }
    }

    fun joinRoom(roomId: String, code: String?, joinNote: String?) {
        val bearer = "Bearer ${roy.ij.obscure.data.AuthSession.token ?: return}"
        _state.value = RoomState(isLoading = true)
        viewModelScope.launch {
            try {
                val resp = api.joinRoom(bearer, JoinRoomReq(roomId, code, joinNote))
                _state.value = RoomState(
                    isLoading = false,
                    message = resp["message"] as? String ?: "Join requested"
                )
            } catch (e: Exception) {
                _state.value = RoomState(isLoading = false, error = e.message)
            }
        }
    }

    fun approveMember(roomId: String, memberId: String) {
        val bearer = "Bearer ${roy.ij.obscure.data.AuthSession.token ?: return}"
        _state.value = RoomState(isLoading = true)
        viewModelScope.launch {
            try {
                val resp = api.approve(bearer, ApproveReq(roomId, memberId))
                _state.value = RoomState(
                    isLoading = false,
                    message = resp["message"] as? String ?: "Approved"
                )
            } catch (e: Exception) {
                _state.value = RoomState(isLoading = false, error = e.message)
            }
        }
    }

    fun listMyRooms() {
        val bearer = "Bearer ${roy.ij.obscure.data.AuthSession.token ?: return}"
        viewModelScope.launch {
            try {
                val resp = api.listMyRooms(bearer)
                println("Rooms: $resp") // you’ll build UI for this next
            } catch (e: Exception) {
                println("list rooms error: ${e.message}")
            }
        }
    }
}