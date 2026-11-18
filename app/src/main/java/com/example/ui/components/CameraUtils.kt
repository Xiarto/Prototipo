package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createTempFridgePhotoUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "fridge_photos").apply {
        if (!exists()) mkdirs()
    }
    val photoFile = File.createTempFile("fridge_", ".jpg", photoDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}
