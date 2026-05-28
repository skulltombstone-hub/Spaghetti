package net.perfectdreams.butterscotch.android.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.android.UUIDAsStringSerializer
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
) {
    @Serializable
    sealed class GameType {
        @Serializable
        @SerialName("GameMakerStudio")
        class GameMakerStudio(
            val wadVersion: Int,
            val filename: String
        ) : GameType()

        // We keep it like this for when we decide to add new GameMaker versions :3
    }

    @Serializable
    data class SaveSlot(
        @Serializable(with = UUIDAsStringSerializer::class)
        val id: UUID,
        val active: Boolean,
        val fancyName: String,
    )
}