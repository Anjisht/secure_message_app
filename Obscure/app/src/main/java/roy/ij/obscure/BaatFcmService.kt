package roy.ij.obscure

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import roy.ij.obscure.data.AuthSession
import roy.ij.obscure.App
import roy.ij.obscure.util.CurrentChat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.core.content.ContextCompat

class BaatFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Forward to backend if logged in
        AuthSession.uploadFcmTokenAsync(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val roomId = remoteMessage.data["roomId"] ?: return

        // ✅ 1) If app is foreground AND the same room is open, skip notification
        if (App.isForeground.get()) {
            val openRoom: String? = runBlocking { CurrentChat.roomId.first() }
            if (openRoom == roomId) return // same room open → no heads-up
        }

        createChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_ROOM"
            putExtra("roomId", roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, roomId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, "messages")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New message")
            .setContentText("Tap to open")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("room_$roomId")
            .build()

        val hasPerm = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            val nm = NotificationManagerCompat.from(this)

            // Post the individual message notification (grouped by room)
            nm.notify(roomId.hashCode(), notif)

            // 🔽 Post/update the room summary so multiple messages collapse under one group
            val summaryId = ("room_$roomId#summary").hashCode()
            val summary = NotificationCompat.Builder(this, "messages")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("New messages")
                .setContentText("Room $roomId")
                .setStyle(NotificationCompat.InboxStyle()) // optional, looks nicer
                .setGroup("room_$roomId")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            nm.notify(summaryId, summary)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_HIGH)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}