package roy.ij.obscure.data

import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import roy.ij.obscure.data.network.RetrofitClient

object AuthSession {
    @Volatile
    var token: String? = null

    /** Called after a successful login/register */
    fun onLogin(jwt: String) {
        token = jwt
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { t -> uploadFcmTokenAsync(t) }
    }

    /** Called when the user logs out (not used yet, but ready) */
    fun onLogout() {
        val t = token
        token = null
        if (t != null) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { fcm ->
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            RetrofitClient.api.removeFcmToken(
                                "Bearer $t",
                                mapOf("token" to fcm)
                            )
                        } catch (_: Exception) {}
                    }
                }
        }
    }

    /** Helper used internally and also by FCMService.onNewToken */
    fun uploadFcmTokenAsync(fcm: String) {
        val jwt = token ?: return
        val ctx = roy.ij.obscure.App.instance
        val deviceId = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        GlobalScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.addFcmToken(
                    "Bearer $jwt",
                    mapOf(
                        "token" to fcm,
                        "deviceId" to deviceId,
                        "platform" to "android"
                    )
                )
            } catch (_: Exception) {}
        }
    }
}