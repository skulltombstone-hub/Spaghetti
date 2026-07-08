package net.perfectdreams.butterscotch.android.importer

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.Log
import net.perfectdreams.butterscotch.android.GameImporter
import net.perfectdreams.butterscotch.android.ParsedDataWin
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.pe.IconCandidate
import net.perfectdreams.butterscotch.android.pe.scanIconCandidates
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object GameMakerImporter {

    private const val TAG = "GameMakerImporter"


    val WAD_FILENAMES = listOf(
        "data.win",
        "game.unx",
        "game.ios",
        "game.droid",
        "game.psp",
        "game.win",
        "game.osx"
    )


    sealed interface Result {

        data class Success(
            val staged: GameLibrary.StagedGame,
            val wadFilename: String,
            val title: String,
            val wadVersion: Int,
            val iconCandidates: List<IconCandidate>
        ) : Result


        data class MissingWad(
            val folderName: String
        ) : Result


        data class Failure(
            val message: String
        ) : Result
    }


    suspend fun import(
        context: Context,
        uri: Uri,
        library: GameLibrary,
        writeFileCallback: (String) -> Unit
    ): Result = withContext(Dispatchers.IO) {


        val staged =
            GameImporter.importFolder(
                context,
                uri,
                library,
                writeFileCallback
            )
            ?: return@withContext Result.Failure(
                "Unable to import folder"
            )


        val wad =
            WAD_FILENAMES.firstOrNull {
                File(staged.bundleDir, it).exists()
            }


        if (wad == null) {

            library.discardStaging(staged)

            return@withContext Result.MissingWad(
                staged.id
            )
        }


        finalize(
            library,
            staged,
            wad
        )
    }


    private fun finalize(
        library: GameLibrary,
        staged: GameLibrary.StagedGame,
        wadFilename: String
    ): Result {


        val wad =
            File(
                staged.bundleDir,
                wadFilename
            )


        val metadata =
            ParsedDataWin
                .parseLight(wad.absolutePath)
                ?.use {
                    it.displayName to it.wadVersion
                }


        val icons =
            runCatching {
                scanIconCandidates(
                    staged.bundleDir
                )
            }
            .getOrDefault(emptyList())


        return Result.Success(
            staged,
            wadFilename,
            metadata?.first ?: staged.id,
            metadata?.second ?: -1,
            icons
        )
    }
}
