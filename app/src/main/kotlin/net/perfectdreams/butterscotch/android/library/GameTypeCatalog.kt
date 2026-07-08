package net.perfectdreams.butterscotch.android.library

import kotlin.reflect.KClass

data class GameTypeDescriptor(
    val type: KClass<out GameEntry.GameType>,
    val title: String,
    val supportsFolder: Boolean,
    val supportsZip: Boolean,
    val supportsSingleFile: Boolean,
    val singleFileExtensions: List<String> = emptyList()
)

object GameTypeCatalog {

    val supportedTypes = listOf(

        GameTypeDescriptor(
            type = GameEntry.GameType.GameMakerStudio::class,
            title = "GameMaker",
            supportsFolder = true,
            supportsZip = true,
            supportsSingleFile = false
        ),

        GameTypeDescriptor(
            type = GameEntry.GameType.Flash::class,
            title = "Adobe Flash",
            supportsFolder = false,
            supportsZip = false,
            supportsSingleFile = true,
            singleFileExtensions = listOf("swf")
        ),

        GameTypeDescriptor(
            type = GameEntry.GameType.RPGMaker::class,
            title = "RPG Maker XP / VX / VX Ace",
            supportsFolder = true,
            supportsZip = true,
            supportsSingleFile = false
        ),

        GameTypeDescriptor(
            type = GameEntry.GameType.OldRPGM::class,
            title = "RPG Maker 2000 / 2003",
            supportsFolder = true,
            supportsZip = true,
            supportsSingleFile = false
        ),

        GameTypeDescriptor(
            type = GameEntry.GameType.Html::class,
            title = "HTML5",
            supportsFolder = true,
            supportsZip = true,
            supportsSingleFile = true,
            singleFileExtensions = listOf("html", "htm")
        )
    )
}
