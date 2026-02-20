package roy.ij.obscure.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String, // This could be the other user's UID
    val participantName: String,
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)