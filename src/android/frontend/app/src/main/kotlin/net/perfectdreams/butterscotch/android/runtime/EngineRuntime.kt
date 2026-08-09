package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * Contract for every runnable engine/runtime supported by Spaghetti.
 *
 * The goal of this layer is to stop the app from branching everywhere on
 * GameType directly. The launcher should ask the registry which runtime owns
 * a given entry, and then use the runtime's launch contract.
 */
interface EngineRuntime {
    /**
     * Stable runtime identifier used internally by the registry.
     * Examples: "gamemaker", "html", "flash", "love2d", "gba".
     */
    val runtimeId: String

    /**
     * Human-readable name for diagnostics and debug UI.
     */
    val displayName: String

    /**
     * Returns true if this runtime can execute the given game type.
     */
    fun supports(gameType: GameEntry.GameType): Boolean

    /**
     * Build the Intent used to launch the runtime.
     *
     * The launcher should use this instead of hardcoding activity classes.
     */
    fun buildLaunchIntent(
        context: Context,
        gameId: UUID
    ): Intent
}
