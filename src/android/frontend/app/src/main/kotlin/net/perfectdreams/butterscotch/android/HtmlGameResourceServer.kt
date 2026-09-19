package net.perfectdreams.butterscotch.android

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLConnection
import java.util.Locale

/**
 * Serves the files of an imported HTML game bundle to the WebView.
 *
 * The WebView sees the game as:
 *
 * https://appassets.androidplatform.net/games/...
 *
 * while the actual files remain inside the application's private storage.
 *
 * This class does not open a TCP server.
 */
class HtmlGameResourceServer(
    bundleDirectory: File
) {

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val GAMES_PREFIX = "/games/"

        private const val SCHEME = "https"

        private val EMPTY_HEADERS = emptyMap<String, String>()

        /**
         * Generates the HTTPS URL that should be loaded by the WebView.
         */
        fun buildGameUrl(bundleDirectory: File, entryFile: File): String {
            val root = bundleDirectory.canonicalFile
            val entry = entryFile.canonicalFile

            require(isInsideRoot(root, entry)) {
                "HTML entry point is outside the game bundle: $entry"
            }

            val relativePath = entry
                .relativeTo(root)
                .invariantSeparatorsPath

            require(relativePath.isNotEmpty()) {
                "HTML entry point has an empty relative path."
            }

            val encodedPath = Uri.encode(relativePath, "/")

            return "$SCHEME://$HOST$GAMES_PREFIX$encodedPath"
        }

        private fun isInsideRoot(root: File, file: File): Boolean {
            val rootPath = root.absolutePath
                .trimEnd(File.separatorChar) + File.separatorChar

            val filePath = file.absolutePath

            return filePath == root.absolutePath ||
                filePath.startsWith(rootPath)
        }
    }

    private val rootDirectory = bundleDirectory.canonicalFile

    init {
        require(rootDirectory.exists()) {
            "HTML game bundle does not exist: $rootDirectory"
        }

        require(rootDirectory.isDirectory) {
            "HTML game bundle is not a directory: $rootDirectory"
        }
    }

    /**
     * Intercepts a WebView request.
     *
     * Returns:
     * - a local WebResourceResponse when the request belongs to the game;
     * - null for requests outside the local game origin.
     */
    fun intercept(uri: Uri): WebResourceResponse? {
        if (uri.scheme != SCHEME) {
            return null
        }

        if (!uri.host.equals(HOST, ignoreCase = true)) {
            return null
        }

        val encodedPath = uri.encodedPath ?: return null

        if (!encodedPath.startsWith(GAMES_PREFIX)) {
            return null
        }

        val encodedRelativePath = encodedPath.removePrefix(GAMES_PREFIX)

        if (encodedRelativePath.isBlank()) {
            return notFound()
        }

        val relativePath = try {
            Uri.decode(encodedRelativePath)
        } catch (_: IllegalArgumentException) {
            return notFound()
        }

        return serveFile(relativePath)
    }

    private fun serveFile(relativePath: String): WebResourceResponse {
        if (relativePath.isBlank()) {
            return notFound()
        }

        /*
         * Reject obviously invalid paths before canonical resolution.
         */
        if (
            relativePath.indexOf('\u0000') >= 0 ||
            relativePath.startsWith("/") ||
            relativePath.startsWith("\\")
        ) {
            return notFound()
        }

        val requestedFile = try {
            File(rootDirectory, relativePath).canonicalFile
        } catch (_: IOException) {
            return notFound()
        }

        /*
         * This is the main path-traversal protection.
         *
         * A request such as:
         *
         * ../secret.txt
         *
         * must never escape the imported game bundle.
         */
        if (!isInsideRoot(rootDirectory, requestedFile)) {
            return notFound()
        }

        if (!requestedFile.isFile) {
            return notFound()
        }

        val mimeType = detectMimeType(requestedFile)
        val charset = charsetForMimeType(mimeType)

        return try {
            WebResourceResponse(
                mimeType,
                charset,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-cache",
                    "Access-Control-Allow-Origin" to "https://$HOST"
                ),
                FileInputStream(requestedFile)
            )
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun detectMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.US)

        /*
         * WebAssembly must explicitly use application/wasm.
         */
        when (extension) {
            "wasm" -> return "application/wasm"

            "js",
            "mjs",
            "cjs" -> return "application/javascript"

            "json" -> return "application/json"

            "html",
            "htm" -> return "text/html"

            "css" -> return "text/css"

            "xml" -> return "application/xml"

            "svg" -> return "image/svg+xml"

            "webmanifest" -> return "application/manifest+json"

            "map" -> return "application/json"

            "woff" -> return "font/woff"

            "woff2" -> return "font/woff2"

            "ttf" -> return "font/ttf"

            "otf" -> return "font/otf"

            "mp3" -> return "audio/mpeg"

            "ogg" -> return "audio/ogg"

            "oga" -> return "audio/ogg"

            "wav" -> return "audio/wav"

            "m4a" -> return "audio/mp4"

            "aac" -> return "audio/aac"

            "flac" -> return "audio/flac"

            "mp4" -> return "video/mp4"

            "webm" -> return "video/webm"

            "ogv" -> return "video/ogg"

            "png" -> return "image/png"

            "jpg",
            "jpeg" -> return "image/jpeg"

            "gif" -> return "image/gif"

            "webp" -> return "image/webp"

            "ico" -> return "image/x-icon"

            "bmp" -> return "image/bmp"
        }

        return (
            MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(extension)
                ?: URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
        )
    }

    private fun charsetForMimeType(mimeType: String): String? {
        return when {
            mimeType.startsWith("text/") -> "UTF-8"

            mimeType == "application/javascript" -> "UTF-8"

            mimeType == "application/json" -> "UTF-8"

            mimeType == "application/manifest+json" -> "UTF-8"

            mimeType == "application/xml" -> "UTF-8"

            mimeType == "image/svg+xml" -> "UTF-8"

            else -> null
        }
    }

    private fun notFound(): WebResourceResponse {
        return WebResourceResponse(
            null,
            null,
            404,
            "Not Found",
            EMPTY_HEADERS,
            null
        )
    }

    private fun isInsideRoot(root: File, file: File): Boolean {
        val rootPath = root.absolutePath
            .trimEnd(File.separatorChar) + File.separatorChar

        val filePath = file.absolutePath

        return filePath == root.absolutePath ||
            filePath.startsWith(rootPath)
    }
}
