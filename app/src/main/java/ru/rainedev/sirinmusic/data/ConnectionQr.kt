package ru.rainedev.sirinmusic.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Account and server settings produce exactly the same connection QR format. */
internal suspend fun createConnectionQr(url: String, token: String): Bitmap = withContext(Dispatchers.Default) {
    val edge = 768
    val matrix = QRCodeWriter().encode(ConnectLink(url, token).encode(), BarcodeFormat.QR_CODE, edge, edge)
    createBitmap(edge, edge).apply {
        setPixels(IntArray(edge * edge) { i -> if (matrix[i % edge, i / edge]) Color.BLACK else Color.WHITE },
            0, edge, 0, 0, edge, edge)
    }
}
