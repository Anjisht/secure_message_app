package roy.ij.obscure.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import roy.ij.obscure.data.model.User

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val user: UserDto?)
data class UserDto(val id: String, val username: String)
data class PublicKeyRequest(val publicKey: String)
data class OkResponse(val ok: Boolean)
data class CreateRoomReq(val codePhrase: String?, val durationMinutes: Int?)
data class CreateRoomResp(val roomId: String, val alias: String, val expiresAt: String?)
data class JoinRoomReq(val roomId: String, val codePhrase: String?, val joinNote: String?)
data class ApproveReq(val roomId: String, val memberId: String)
data class MemberDto(
    val userId: String,
    val alias: String,
    val username: String?,
    val publicKey: String?
)
data class MembersResp(val roomId: String, val members: List<MemberDto>)
data class RoomInfoResp(val roomId: String, val isAdmin: Boolean, val expiresAt: String?, val members: List<MemberInfo>)
data class MemberInfo(val userId: String, val alias: String, val status: String, val requestedAt: String?, val joinNote: String?)
data class DmStartReq(val targetUsername: String? = null, val targetUserId: String? = null)
data class DmStartResp(val roomId: String, val type: String, val reused: Boolean)
data class UploadUrlReq(val contentType: String)
data class UploadUrlResp(val uploadUrl: String, val fileUrl: String, val fileKey: String)
data class DownloadUrlResp(val downloadUrl: String)


interface ApiService {
//    @POST("/api/proxy/writer-prompt")
//    suspend fun getRewrittenPrompt(@Body request: WriterRequest): WriterResponse

    @POST("users/register")
    suspend fun register(@Body req: AuthRequest): AuthResponse

    @POST("users/login")
    suspend fun login(@Body req: AuthRequest): AuthResponse

    @GET("users/me")
    suspend fun me(@Header("Authorization") bearer: String): Map<String, Any>

    @POST("users/public-key")
    suspend fun setPublicKey(
        @Header("Authorization") bearer: String,
        @Body req: PublicKeyRequest
    ): OkResponse

    @POST("rooms/create")
    suspend fun createRoom(@Header("Authorization") bearer: String,
                           @Body req: CreateRoomReq): CreateRoomResp

    @POST("rooms/join")
    suspend fun joinRoom(@Header("Authorization") bearer: String,
                         @Body req: JoinRoomReq): Map<String, Any>

    @POST("rooms/approve")
    suspend fun approve(@Header("Authorization") bearer: String, @Body req: ApproveReq): Map<String, Any>

    @GET("rooms/mine")
    suspend fun listMyRooms(@Header("Authorization") bearer: String): Map<String, Any>

    @GET("rooms/{roomId}/members")
    suspend fun getRoomMembers(
        @Header("Authorization") bearer: String,
        @retrofit2.http.Path("roomId") roomId: String
    ): MembersResp

    @GET("messages/{roomId}")
    suspend fun getHistory(
        @Header("Authorization") bearer: String,
        @retrofit2.http.Path("roomId") roomId: String
    ): Map<String, Any>

    @GET("rooms/{roomId}/info")
    suspend fun roomInfo(@Header("Authorization") bearer: String, @Path("roomId") roomId: String): RoomInfoResp

    @POST("rooms/deny")
    suspend fun denyMember(@Header("Authorization") bearer: String, @Body body: Map<String, String>): Map<String, Any>

    @POST("rooms/dm/start")
    suspend fun startDm(
        @Header("Authorization") bearer: String,
        @Body req: DmStartReq
    ): DmStartResp

    @POST("media/upload-url")
    suspend fun getUploadUrl(
        @Header("Authorization") bearer: String,
        @Body body: UploadUrlReq
    ): UploadUrlResp

    @GET("media/download-url")
    suspend fun getDownloadUrl(
        @Header("Authorization") bearer: String,
        @Query("fileKey") fileKey: String
    ): DownloadUrlResp

    @POST("users/fcm/add")
    suspend fun addFcmToken(
        @Header("Authorization") bearer: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("users/fcm/remove")
    suspend fun removeFcmToken(
        @Header("Authorization") bearer: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

}