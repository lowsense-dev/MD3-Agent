package dev.huecoder.md3agent.feature.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Scratch files for the in-app camera; a confirmed photo is copied into the attachments right after. */
object CameraCapture {
    private const val DIRECTORY = "camera"

    fun newPhotoFile(context: Context): File {
        val folder = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        // Earlier shots were already imported or discarded; the folder only ever holds the photo being taken.
        folder.listFiles()?.forEach { it.delete() }
        return File(folder, "photo-${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
