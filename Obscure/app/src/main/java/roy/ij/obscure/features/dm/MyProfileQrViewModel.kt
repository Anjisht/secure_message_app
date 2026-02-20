package roy.ij.obscure.features.dm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import roy.ij.obscure.data.network.RetrofitClient

data class UserMe(val id: String, val username: String)

class MyProfileQrViewModel : ViewModel() {
    private val api = RetrofitClient.api
    private val _user = MutableStateFlow<UserMe?>(null)
    val user: StateFlow<UserMe?> = _user

    fun load(token: String) {
        viewModelScope.launch {
            try {
                val resp = api.me("Bearer $token") as Map<*, *>
                val userMap = resp["user"] as? Map<*, *>
                val id = userMap?.get("id")?.toString() ?: ""
                val username = userMap?.get("username")?.toString() ?: ""
                _user.value = UserMe(id, username)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
