package roy.ij.obscure.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CurrentChat {
    // null = no conversation open
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId = _roomId.asStateFlow()

    fun set(room: String?) { _roomId.value = room }
}