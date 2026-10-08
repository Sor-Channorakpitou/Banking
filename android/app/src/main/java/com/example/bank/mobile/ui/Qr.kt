package com.example.bank.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.tasks.await

/** Draws a QR code; 'H' error correction leaves room for the logo placed in the middle. */
fun qrBitmap(content: String, size: Int = 640): Bitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF0E1A2B.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * Opens Google's code scanner (from Google Play services) and returns the text of
 * the QR code, or null if the user backed out. The scanner runs in its own process,
 * so this app never needs the camera permission or sees the camera feed.
 */
suspend fun scanQrCode(context: Context): String? {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    return try {
        GmsBarcodeScanning.getClient(context, options).startScan().await().rawValue
    } catch (e: com.google.mlkit.common.MlKitException) {
        if (e.errorCode == com.google.mlkit.common.MlKitException.CODE_SCANNER_CANCELLED) null else throw e
    }
}
