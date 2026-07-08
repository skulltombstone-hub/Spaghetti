package net.perfectdreams.butterscotch.android.importer

import android.content.Context
import android.net.Uri
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.runtime.RuntimeKind
import java.io.File

class FlashImporter(
    private val context: Context
) {

    fun import(
        uri: Uri,
        library: GameLibrary
    ): GameEntry? {
        val fileName = getFileName(uri) ?: return null

        if (!fileName.lowercase().endsWith(".swf")) {
            return null
        }

        val destination = File(
            context.filesDir,
            "games/flash/$fileName"
        )

        destination.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null


        val entry = GameEntry(
            id = destination.absolutePath,
            title = fileName.removeSuffix(".swf"),
            gameType = "flash",
            runtimeKind = RuntimeKind.FLASH,
            importedAtMillis = System.currentTimeMillis(),
            path = destination.absolutePath
        )

        library.add(entry)

        return entry
    }


    private fun getFileName(uri: Uri): String? {
        var name: String? = null

        context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }

        return name
    }
}
