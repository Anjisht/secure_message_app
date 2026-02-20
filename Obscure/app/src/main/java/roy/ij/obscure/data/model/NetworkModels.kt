package roy.ij.obscure.data.model

data class SyncRequest(val idToken: String)

data class User(
    val firebaseUid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val publicKey: String?,
    val fcmToken: String?
)