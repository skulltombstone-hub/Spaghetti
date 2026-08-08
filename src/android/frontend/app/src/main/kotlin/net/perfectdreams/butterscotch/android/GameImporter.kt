package net.perfectdreams.butterscotch.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.pe.IconCandidate
import net.perfectdreams.butterscotch.android.pe.scanIconCandidates
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.max

/**
 * Copies a user-picked folder (via Storage Access Framework tree Uri) into the app's per-game
 * bundle directory and detects which supported game type it contains.
 *
 * Supported inputs:
 * - GameMaker WAD-based games
 * - HTML-based games / sites
 *
 * Why copy and not just hold the tree Uri? `takePersistableUriPermission` is fragile (cleared on
 * reboot in some OEM ROMs, lost if the source folder moves) and `DocumentFile` access is slow.
 * Copying once at import time lets the runtime read files via plain POSIX paths forever after.
 *
 * Validation policy:
 * - GameMaker imports require a known WAD filename
 * - HTML imports require an `index.html` / `index.htm` entry point somewhere in the imported tree
 */
object GameImporter {
    private const val TAG = "GameImporter"

    /** Filenames the runner recognizes as the GameMaker WAD across export targets. */
    val WAD_FILENAMES = listOf(
        "data.win",   // Windows
        "game.unx",   // Linux
        "game.ios",   // iOS
        "game.droid", // Android
        "game.psp",   // PSP
        "game.win",   // PSVita
        "game.osx",   // macOS
    )

    private val HTML_ENTRY_FILENAMES = listOf(
        "index.html",
        "index.htm",
    )

    sealed interface Result {
        /**
         * The import was successful.
         *
         * [gameType] tells the caller whether this is GameMaker or HTML.
         * [wadFilename]/[wadVersion] are only set for GameMaker imports.
         * [entryPoint] is only set for HTML imports and is relative to the imported bundle root.
         *
         * IMPORTANT: the caller must either call
         * [net.perfectdreams.butterscotch.android.library.GameLibrary.commit] with the staged game
         * (passed back via [staged]) or
         * [net.perfectdreams.butterscotch.android.library.GameLibrary.discardStaging].
         */
        data class Success(
            val staged: GameLibrary.StagedGame,
            val gameType: GameEntry.GameType,
            val suggestedTitle: String,
            val folderName: String,
            val iconCandidates: List<IconCandidate>,
            val wadFilename: String? = null,
            val wadVersion: Int? = null,
            val entryPoint: String? = null,
        ) : Result

        /** The picked folder/archive did not contain a supported game entry point. */
        data class MissingWad(val folderName: String) : Result

        /** Tree Uri was unreadable, or copy/extract failed midway. Staged dir (if any) is deleted. */
        data class Failure(val message: String) : Result
    }

    /**
     * Pick a folder → copy its contents into a fresh staging dir → detect supported format.
     * Runs on IO dispatcher.
     */
    suspend fun import(
        context: Context,
        treeUri: Uri,
        library: GameLibrary,
        writeFileCallback: (String) -> (Unit)
    ): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.isDirectory) {
            return@withContext Result.Failure("Selected location is not a readable folder.")
        }

        val folderName = root.name ?: "Imported Game"
        val staged = library.beginStaging()

        try {
            copyTree(context, root, staged.bundleDir, writeFileCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed for $treeUri", e)
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't copy folder: ${e.message}")
        }

        finalizeFromBundle(
            library = library,
            staged = staged,
            bundleRoot = staged.bundleDir,
            folderName = folderName
        )
    }

    /**
     * Pick a ZIP → extract it into a fresh staging dir → detect supported format.
     *
     * For ZIPs, the supported root is promoted so that the WAD or HTML entry point becomes
     * available at the bundle root, together with its sibling assets.
     */
    suspend fun importZip(
        context: Context,
        zipUri: Uri,
        library: GameLibrary,
        writeFileCallback: (String) -> (Unit)
    ): Result = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, zipUri)
        val fallbackName = (displayName?.removeSuffix(".zip") ?: "").ifBlank { "Imported Game" }
        val staged = library.beginStaging()

        val temp = File(staged.bundleDir.parentFile, "extract-tmp")
        if (temp.exists()) temp.deleteRecursively()
        temp.mkdirs()

        try {
            extractZip(context, zipUri, temp, writeFileCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Zip extraction failed for $zipUri", e)
            temp.deleteRecursively()
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't extract ZIP: ${e.message}")
        }

        val wadFile = findWadFile(temp)
        val htmlFile = findHtmlEntryPoint(temp)

        val sourceRoot = when {
            wadFile != null -> wadFile.parentFile ?: temp
            htmlFile != null -> htmlFile.parentFile ?: temp
            else -> {
                temp.deleteRecursively()
                library.discardStaging(staged)
                return@withContext Result.MissingWad(fallbackName)
            }
        }

        try {
            for (child in sourceRoot.listFiles() ?: emptyArray()) {
                child.copyRecursively(File(staged.bundleDir, child.name), overwrite = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed while promoting ZIP root", e)
            temp.deleteRecursively()
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't copy ZIP contents: ${e.message}")
        } finally {
            temp.deleteRecursively()
        }

        finalizeFromBundle(
            library = library,
            staged = staged,
            bundleRoot = staged.bundleDir,
            folderName = fallbackName
        )
    }

    /**
     * Import a ZIP already held in memory as a [ByteArray] (e.g. a sample game downloaded over HTTP).
     * Same locate-the-entry-point behavior as the Uri overload. [fallbackName] is used as the
     * suggested title when the imported content has no better title.
     */
    suspend fun importZip(
        library: GameLibrary,
        zipBytes: ByteArray,
        name: String,
        iconAsBytes: ByteArray,
        writeFileCallback: (String) -> (Unit)
    ): Result = withContext(Dispatchers.IO) {
        val staged = library.beginStaging()

        val temp = File(staged.bundleDir.parentFile, "extract-tmp")
        if (temp.exists()) temp.deleteRecursively()
        temp.mkdirs()

        try {
            extractZip(zipBytes, temp, writeFileCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Zip extraction failed for in-memory ZIP", e)
            temp.deleteRecursively()
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't extract ZIP: ${e.message}")
        }

        val wadFile = findWadFile(temp)
        val htmlFile = findHtmlEntryPoint(temp)

        val sourceRoot = when {
            wadFile != null -> wadFile.parentFile ?: temp
            htmlFile != null -> htmlFile.parentFile ?: temp
            else -> {
                temp.deleteRecursively()
                library.discardStaging(staged)
                return@withContext Result.MissingWad(name)
            }
        }

        try {
            for (child in sourceRoot.listFiles() ?: emptyArray()) {
                child.copyRecursively(File(staged.bundleDir, child.name), overwrite = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed while promoting in-memory ZIP root", e)
            temp.deleteRecursively()
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't copy ZIP contents: ${e.message}")
        } finally {
            temp.deleteRecursively()
        }

        val decodedIcon = BitmapFactory.decodeByteArray(iconAsBytes, 0, iconAsBytes.size)
        val additionalIconCandidates =
            decodedIcon?.let {
                listOf(
                    IconCandidate(
                        it,
                        "Sample",
                        max(it.width, it.height)
                    )
                )
            } ?: emptyList()

        finalizeFromBundle(
            library = library,
            staged = staged,
            bundleRoot = staged.bundleDir,
            folderName = name,
            additionalIconCandidates = additionalIconCandidates
        )
    }

    /**
     * Shared tail for both import paths: verify the bundle contains a supported entry point,
     * derive metadata, scan icons, and build the [Result.Success].
     */
    private fun finalizeFromBundle(
        library: GameLibrary,
        staged: GameLibrary.StagedGame,
        bundleRoot: File,
        folderName: String,
        additionalIconCandidates: List<IconCandidate> = emptyList(),
    ): Result {
        val wadFile = findWadFile(bundleRoot)
        if (wadFile != null) {
            if (!wadFile.exists()) {
                library.discardStaging(staged)
                return Result.Failure("WAD vanished after copy (this is a bug).")
            }

            val (suggestedTitle, wadVersion) = ParsedDataWin.parseLight(wadFile.absolutePath)?.use { dw ->
                val name = (dw.displayName ?: dw.name)?.takeIf { it.isNotBlank() }
                name to dw.wadVersion
            } ?: (null to -1)

            val iconCandidates = runCatching { scanIconCandidates(bundleRoot) }
                .onFailure { Log.w(TAG, "Icon extraction failed for ${staged.id}", it) }
                .getOrDefault(emptyList()) + additionalIconCandidates

            return Result.Success(
                staged = staged,
                gameType = GameEntry.GameType.GameMakerStudio(
                    wadVersion = wadVersion,
                    filename = wadFile.name
                ),
                suggestedTitle = suggestedTitle ?: folderName,
                folderName = folderName,
                iconCandidates = iconCandidates,
                wadFilename = wadFile.name,
                wadVersion = wadVersion,
                entryPoint = null,
            )
        }

        val htmlFile = findHtmlEntryPoint(bundleRoot)
            ?: return Result.Failure("Copied bundle does not contain a supported WAD or HTML entry point.")

        val entryPoint = htmlFile.relativeTo(bundleRoot).invariantSeparatorsPath
        val htmlTitle = extractHtmlTitle(htmlFile)
        val iconCandidates = runCatching { scanIconCandidates(bundleRoot) }
            .onFailure { Log.w(TAG, "Icon extraction failed for ${staged.id}", it) }
            .getOrDefault(emptyList()) + additionalIconCandidates

        return Result.Success(
            staged = staged,
            gameType = GameEntry.GameType.Html(
                sourceUrl = null,
                entryPoint = entryPoint
            ),
            suggestedTitle = htmlTitle ?: folderName,
            folderName = folderName,
            iconCandidates = iconCandidates,
            wadFilename = null,
            wadVersion = null,
            entryPoint = entryPoint,
        )
    }

    /**
     * Recursive DocumentFile → File copy. Mirrors the SAF tree directly into the staging bundle.
     */
    private fun copyTree(context: Context, src: DocumentFile, dest: File, writeFileCallback: (String) -> (Unit)) {
        if (!dest.exists()) dest.mkdirs()

        for (child in src.listFiles()) {
            val name = child.name ?: continue
            val target = File(dest, name)

            if (child.isDirectory) {
                copyTree(context, child, target, writeFileCallback)
            } else if (child.isFile) {
                writeFileCallback.invoke(target.name)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open child $name for reading")
            }
        }
    }

    /**
     * Finds the shallowest WAD in a tree. If there are multiple, prefer the one closest to the root.
     */
    private fun findWadFile(root: File): File? {
        return root.walkTopDown()
            .filter { it.isFile && it.name in WAD_FILENAMES }
            .minByOrNull { file ->
                file.relativeTo(root).invariantSeparatorsPath.count { c -> c == '/' }
            }
    }

    /**
     * Finds the best HTML entry point in a tree.
     *
     * Preference order:
     * 1) `index.html` / `index.htm`
     * 2) shallowest `.html` / `.htm` file
     */
    private fun findHtmlEntryPoint(root: File): File? {
        val preferred = root.walkTopDown()
            .firstOrNull { file ->
                file.isFile && HTML_ENTRY_FILENAMES.any { file.name.equals(it, ignoreCase = true) }
            }

        if (preferred != null) return preferred

        return root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    (file.name.endsWith(".html", ignoreCase = true) ||
                        file.name.endsWith(".htm", ignoreCase = true))
            }
            .minByOrNull { file ->
                file.relativeTo(root).invariantSeparatorsPath.count { c -> c == '/' }
            }
    }

    /**
     * Tries to extract a title from an HTML file.
     */
    private fun extractHtmlTitle(file: File): String? {
        return runCatching {
            val text = file.readText()
            val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(text) ?: return null
            val title = match.groupValues[1]
                .replace(Regex("(?is)<[^>]+>"), "")
                .trim()

            title.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Extract a user-picked zip into [dest]. Mirrors the zip-slip guard and separator normalization.
     */
    private fun extractZip(context: Context, source: Uri, dest: File, writeFileCallback: (String) -> (Unit)) {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not open input stream for $source")
        input.use { extractZipStream(it, dest, writeFileCallback) }
    }

    /** [extractZip] for a ZIP already held in memory. */
    private fun extractZip(zipBytes: ByteArray, dest: File, writeFileCallback: (String) -> (Unit)) {
        zipBytes.inputStream().use { extractZipStream(it, dest, writeFileCallback) }
    }

    /** Core ZIP extraction shared by both [extractZip] overloads. Refuses zip-slip entries. */
    private fun extractZipStream(input: java.io.InputStream, dest: File, writeFileCallback: (String) -> (Unit)) {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val safeName = entry.name.replace('\\', '/')

                if (safeName.contains("..")) {
                    zip.closeEntry()
                    continue
                }

                val target = File(dest, safeName)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    writeFileCallback.invoke(target.name)
                    target.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
            }
        }
    }

    /** Resolve a content Uri's display name (the file name) for use as a fallback title. */
    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
