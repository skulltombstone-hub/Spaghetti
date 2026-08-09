package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * Small helper for the launcher UI.
 *
 * The launcher asks for a game entry and gets a ready-to-launch Intent.
 * No screen should need to know which activity class belongs to which game type.
 */
object GameLaunchResolver {
    fun buildLaunchIntent(
        context: Context,
        entry: GameEntry
    ): Intent? {
        val runtime = EngineRuntimeRegistry.resolve(entry.gameType) ?: return null
        return runtime.buildLaunchIntent(context, entry.id)
    }

    fun runtimeName(entry: GameEntry): String? {
        return EngineRuntimeRegistry.resolve(entry.gameType)?.displayName
    }

    fun runtimeId(entry: GameEntry): String? {
        return EngineRuntimeRegistry.resolve(entry.gameType)?.runtimeId
    }
}
