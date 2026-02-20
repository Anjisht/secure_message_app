package roy.ij.obscure.features.chat

data class RoomState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val roomId: String? = null,
    val alias: String? = null,
    val error: String? = null
)
