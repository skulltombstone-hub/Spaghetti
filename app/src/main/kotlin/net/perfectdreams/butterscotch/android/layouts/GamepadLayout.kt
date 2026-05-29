package net.perfectdreams.butterscotch.android.layouts

import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.android.UUIDAsStringSerializer
import java.util.UUID

@Serializable
data class GamepadLayout(
    @Serializable(with = UUIDAsStringSerializer::class)
    val id: UUID,
    val orientation: GamepadTargetOrientation,
    val element: List<GamepadElement>
) {
    enum class GamepadTargetOrientation {
        PORTRAIT,
        LANDSCAPE
    }
}