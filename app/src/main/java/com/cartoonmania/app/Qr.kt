package com.cartoonmania.app

import android.graphics.Bitmap

/** QR condiviso: dialog sync nelle Impostazioni e foto profilo TV. */
object Qr {

    fun bitmap(text: String, px: Int = 512): Bitmap? {
        return try {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(
                text, com.google.zxing.BarcodeFormat.QR_CODE, px, px
            )
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until px) {
                    for (y in 0 until px) {
                        setPixel(x, y, if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
