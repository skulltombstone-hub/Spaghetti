package net.perfectdreams.butterscotch.android.library

import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.UUIDAsStringSerializer
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import java.util.UUID

@Serializable
data class GameEntry(
    @Serializable(with = UUIDAsStringSerializer::class)
    val id: UUID,
    val title: String,
    val gameType: GameType,
    val importedAtMillis: Long,
    val favorited: Boolean,
    val saveSlots: List<SaveSlot>,

    /** Bumped whenever the icon file is rewritten, so launcher UI invalidates its bitmap cache. */
    val iconRevision: Long = 0,

    @Serializable(with = UUIDAsStringSerializer::class)
    val portraitLayout: UUID = LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT,

    @Serializable(with = UUIDAsStringSerializer::class)
    val landscapeLayout: UUID = LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT,

    /**
     * OS reported to the game through GML's os_type / os_* builtins.
     * Defaults to Windows, matching the previous native runner behavior.
     */
    val runnerOs: RunnerOs = RunnerOs.WINDOWS,

    /**
     * When true, physical controllers (Bluetooth/USB gamepads) feed
     * the GameMaker gamepad_* builtins.
     */
    val enablePhysicalControllers: Boolean = true,

    /**
     * When true, a physical keyboard feeds the GameMaker keyboard_* builtins.
     */
    val enablePhysicalKeyboard: Boolean = true,

    val enableWidescreenHack: Boolean = false,

    val postProcessing: PostProcessingSettings = PostProcessingSettings(),
) {

    /**
     * Operating-system targets understood by the existing GameMaker runner.
     */
    @Serializable
    enum class RunnerOs(
        val nativeValue: Int,
        val fancyName: String,
        val displayResolution: IntSize? = null
    ) {
        WINDOWS(0, "Windows"),
        MACOSX(1, "macOS"),
        PSP(2, "PSP"),
        IOS(3, "iOS"),
        ANDROID(4, "Android"),
        SYMBIAN(5, "Symbian"),
        LINUX(6, "Linux"),
        WINPHONE(7, "Windows Phone"),
        TIZEN(8, "Tizen"),
        WIN8NATIVE(9, "Windows 8 Native"),
        WIIU(10, "Wii U"),
        THREEDS(11, "3DS"),
        PSVITA(12, "PS Vita", IntSize(960, 544)),
        BB10(13, "BlackBerry 10"),
        PS4(14, "PS4", IntSize(1920, 1080)),
        XBOXONE(15, "Xbox One", IntSize(1920, 1080)),
        PS3(16, "PS3", IntSize(1920, 1080)),
        XBOX360(17, "Xbox 360", IntSize(1920, 1080)),
        UWP(18, "UWP"),
        AMAZON(19, "Amazon"),
        SWITCH(20, "Switch", IntSize(1280, 720)),
    }

    /**
     * Identifies the runtime/engine that owns a game.
     *
     * IMPORTANT:
     * These types are deliberately separate. Each runtime will continue
     * to launch its own Activity through EngineRuntime.
     */
    @Serializable
    sealed class GameType {

        @Serializable
        @SerialName("GameMakerStudio")
        data class GameMakerStudio(
            val wadVersion: Int,
            val filename: String
        ) : GameType()

        /**
         * HTML/WebView-based game.
         *
         * sourceUrl is reserved for future hosted-content support.
         * entryPoint is relative to the imported game's bundle directory.
         */
        @Serializable
        @SerialName("Html")
        data class Html(
            val sourceUrl: String? = null,
            val entryPoint: String = "index.html"
        ) : GameType()

        /**
         * LÖVE 2D game.
         *
         * A .love file is normally a ZIP archive containing main.lua
         * at the archive root.
         */
        @Serializable
        @SerialName("Love2D")
        data class Love2D(
            val filename: String
        ) : GameType()

        /**
         * Adobe Flash game.
         *
         * The primary supported file is SWF.
         */
        @Serializable
        @SerialName("Flash")
        data class Flash(
            val filename: String
        ) : GameType()

        /**
         * Game Boy Advance cartridge image.
         *
         * The importer will validate that the actual ROM appears to be
         * a GBA image instead of blindly trusting the file extension.
         */
        @Serializable
        @SerialName("GameBoyAdvance")
        data class GameBoyAdvance(
            val filename: String
        ) : GameType()

        /**
         * Sega Mega Drive / Genesis cartridge image.
         *
         * Both .md and .bin will be supported by the importer.
         * The runtime must still validate the actual ROM before attempting
         * to execute it, because .bin is not exclusive to Mega Drive.
         */
        @Serializable
        @SerialName("MegaDrive")
        data class MegaDrive(
            val filename: String
        ) : GameType()
    }

    @Serializable
    data class SaveSlot(
        @Serializable(with = UUIDAsStringSerializer::class)
        val id: UUID,
        val active: Boolean,
        val fancyName: String,
    )

    @Serializable
    enum class PostProcessingShader(val fancyName: String) {
        OFF("Off"),
        CRT("CRT Shader"),
    }

    @Serializable
    data class CrtSettings(
        val curvature: Double = 1.0,
        val aberration: Double = 1.0,
        val halation: Double = 1.0,
        val scanlines: Double = 1.0,
        val mask: Double = 1.0,
        val vignette: Double = 1.0,
    )

    @Serializable
    data class PostProcessingSettings(
        val shader: PostProcessingShader = PostProcessingShader.OFF,
        val crt: CrtSettings = CrtSettings(),
    )
}
