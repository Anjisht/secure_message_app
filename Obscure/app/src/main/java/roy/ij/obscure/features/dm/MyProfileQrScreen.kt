package roy.ij.obscure.features.dm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

@Composable
fun MyProfileQrScreen(username: String, userId: String) {
    val payload = remember(username, userId) {
        JSONObject(mapOf("v" to 1, "type" to "dm", "userId" to userId, "username" to username)).toString()
    }

    val bmp = remember(payload) { makeQr(payload, 800) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Your QR", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "My QR") }
        Spacer(Modifier.height(12.dp))
        Text("Username: $username")
//        Text("User ID: $userId")
    }
}

private fun makeQr(text: String, size: Int): android.graphics.Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        bmp
    } catch (_: Exception) { null }
}
