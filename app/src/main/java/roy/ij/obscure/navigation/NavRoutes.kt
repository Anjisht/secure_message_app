package roy.ij.obscure.navigation

import kotlinx.serialization.Serializable

@Serializable
object StartScreen

@Serializable
object SignInScreen

@Serializable
object ChatsScreen

@Serializable
object PromptWriterScreen

@Serializable
data class ConversationScreen(val chatId: String)

sealed class NavRoutes(val route: String) {
    object Auth : NavRoutes("auth")
    object ChatList : NavRoutes("chatlist")
    object Room : NavRoutes("room")
    object Conversation : NavRoutes("conversation/{roomId}") {
        fun create(roomId: String) = "conversation/$roomId"
    }
    object MyQr : NavRoutes("myqr")
    object ScanOrType : NavRoutes("scanType")
    object Lock : NavRoutes("lock")
}
