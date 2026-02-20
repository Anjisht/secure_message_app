package roy.ij.obscure.data.network

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import roy.ij.obscure.BuildConfig

class SocketManager(private val token: String) {
    private var socket: Socket? = null

    fun connect() {
        val opts = IO.Options().apply {
            reconnection = true
            forceNew = true
            extraHeaders = mapOf("Authorization" to listOf("Bearer $token"))
        }
        socket = IO.socket(BuildConfig.SOCKET_BASE_URL, opts)
        socket?.connect()
    }

    fun joinRoom(roomId: String, ack: ((JSONObject) -> Unit)? = null) {
        val data = JSONObject(mapOf("roomId" to roomId))
        socket?.emit("rooms:join", data, io.socket.client.Ack { args ->
            if (args.isNotEmpty() && ack != null) {
                ack(args[0] as JSONObject)
            }
        })
    }

    fun sendMessage(payload: JSONObject, ack: ((JSONObject) -> Unit)? = null) {
        socket?.emit("message:send", payload, io.socket.client.Ack { args ->
            if (args.isNotEmpty() && ack != null) {
                ack(args[0] as JSONObject)
            }
        })
    }


    fun onNewMessage(handler: (JSONObject) -> Unit) {
        socket?.on("message:new") { args ->
            if (args.isNotEmpty()) handler(args[0] as JSONObject)
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }
}