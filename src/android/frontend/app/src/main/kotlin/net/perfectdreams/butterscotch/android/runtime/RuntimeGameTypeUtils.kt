package net.perfectdreams.butterscotch.android.runtime

import net.perfectdreams.butterscotch.android.library.GameEntry
import java.io.File

/**
 * Small runtime-related helpers shared by the importer and individual
 * runtime Activities.
 *
 * This file does not launch anything and does not change existing Activities.
 * It only provides common, deterministic information about a GameEntry.
 */
object RuntimeGameTypeUtils {

    /**
     * Returns the stable runtime id associated with a GameType.
     *
     * These ids must remain stable because they may be used by launcher
     * configuration, diagnostics and future runtime-specific settings.
     */
    fun runtimeId(gameType: GameEntry.GameType): String {
        return when (gameType) {
            is GameEntry.GameType.GameMakerStudio -> "gamemaker"
            is GameEntry.GameType.Html -> "html"
            is GameEntry.GameType.Love2D -> "love2d"
            is GameEntry.GameType.Flash -> "flash"
            is GameEntry.GameType.GameBoyAdvance -> "gba"
            is GameEntry.GameType.MegaDrive -> "megadrive"
        }
    }

    /**
     * Returns a human-readable runtime name.
     */
    fun displayName(gameType: GameEntry.GameType): String {
        return when (gameType) {
            is GameEntry.GameType.GameMakerStudio -> "GameMaker"
            is GameEntry.GameType.Html -> "HTML"
            is GameEntry.GameType.Love2D -> "LÖVE 2D"
            is GameEntry.GameType.Flash -> "Adobe Flash"
            is GameEntry.GameType.GameBoyAdvance -> "Game Boy Advance"
            is GameEntry.GameType.MegaDrive -> "Sega Mega Drive"
        }
    }

    /**
     * Returns the primary file associated with a game type.
     *
     * This intentionally does not know about GameLibrary paths.
     * The caller supplies the bundle directory and this helper resolves
     * only the runtime-specific filename.
     */
    fun primaryFile(
        gameType: GameEntry.GameType,
        bundleDirectory: File
    ): File {
        val filename = when (gameType) {
            is GameEntry.GameType.GameMakerStudio ->
                gameType.filename

            is GameEntry.GameType.Html ->
                gameType.entryPoint

            is GameEntry.GameType.Love2D ->
                gameType.filename

            is GameEntry.GameType.Flash ->
                gameType.filename

            is GameEntry.GameType.GameBoyAdvance ->
                gameType.filename

            is GameEntry.GameType.MegaDrive ->
                gameType.filename
        }

        return File(bundleDirectory, filename)
    }

    /**
     * True when the supplied game type represents a cartridge ROM.
     */
    fun isConsoleRom(gameType: GameEntry.GameType): Boolean {
        return when (gameType) {
            is GameEntry.GameType.GameBoyAdvance,
            is GameEntry.GameType.MegaDrive -> true

            is GameEntry.GameType.GameMakerStudio,
            is GameEntry.GameType.Html,
            is GameEntry.GameType.Love2D,
            is GameEntry.GameType.Flash -> false
        }
    }

    /**
     * Returns the friendly import-time extensions associated with a runtime.
     *
     * This list is intentionally descriptive rather than being the actual
     * validation logic. Real validation belongs to GameImporter because
     * extension alone is not trustworthy for ambiguous formats such as .bin.
     */
    fun supportedExtensions(gameType: GameEntry.GameType): List<String> {
        return when (gameType) {
            is GameEntry.GameType.GameMakerStudio ->
                listOf(
                    ".win",
                    ".unx",
                    ".ios",
                    ".droid",
                    ".psp",
                    ".osx"
                )

            is GameEntry.GameType.Html ->
                listOf(
                    ".html",
                    ".htm",
                    ".zip"
                )

            is GameEntry.GameType.Love2D ->
                listOf(
                    ".love"
                )

            is GameEntry.GameType.Flash ->
                listOf(
                    ".swf"
                )

            is GameEntry.GameType.GameBoyAdvance ->
                listOf(
                    ".gba"
                )

            is GameEntry.GameType.MegaDrive ->
                listOf(
                    ".md",
                    ".bin"
                )
        }
    }

    /**
     * Creates a user-facing fallback explanation for runtime failures.
     *
     * This is intentionally separate from the technical log. The log can
     * remain detailed while this message stays understandable to normal users.
     */
    fun probableImportErrorMessage(
        gameType: GameEntry.GameType,
        file: File?,
        technicalCause: String? = null
    ): String {
        val name = displayName(gameType)

        val extension = file
            ?.extension
            ?.takeIf { it.isNotBlank() }
            ?.let { ".$it" }

        val ambiguousFormatWarning = when (gameType) {
            is GameEntry.GameType.MegaDrive -> {
                if (extension.equals(".bin", ignoreCase = true)) {
                    "\n\nArquivos .bin também são usados por outros consoles e formatos. " +
                        "É possível que este arquivo seja uma ROM de outro console, " +
                        "e não uma ROM de Mega Drive."
                } else {
                    ""
                }
            }

            is GameEntry.GameType.GameBoyAdvance -> {
                if (extension.equals(".gba", ignoreCase = true)) {
                    "\n\nEmbora a extensão .gba seja típica de Game Boy Advance, " +
                        "o arquivo pode estar corrompido ou ter sido renomeado incorretamente."
                } else {
                    ""
                }
            }

            else -> ""
        }

        val technicalSuffix = technicalCause
            ?.takeIf { it.isNotBlank() }
            ?.let {
                "\n\nDetalhe técnico disponível no log: $it"
            }
            ?: ""

        return buildString {
            append("Não foi possível iniciar este jogo com o runtime $name.")

            append(
                "\n\nProvavelmente o arquivo importado não corresponde ao formato esperado " +
                    "pelo runtime, está corrompido ou não é compatível com esta versão."
            )

            append(ambiguousFormatWarning)
            append(technicalSuffix)
        }
    }
}
