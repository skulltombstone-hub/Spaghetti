package net.perfectdreams.butterscotch.android.runtime

import android.app.Activity
import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.library.GameEntry

/**
 * Small helper used by launcher UI screens.
 *
 * This keeps the launcher from knowing which activity belongs to which
 * game type. It asks the runtime registry for the correct runtime and
 * receives a launch Intent in return.
 */
object GameLaunchResolver {
    fun buildLaunchIntent(
        context: Context,
        entry: GameEntry
    ): Intent? {
        val runtime = EngineRuntimeRegistry.resolve(entry.gameType) ?: return null
        val intent = runtime.buildLaunchIntent(context, entry.id)

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return intent
    }

    fun launch(
        context: Context,
        entry: GameEntry
    ): Boolean {
        val intent = buildLaunchIntent(context, entry) ?: return false
        context.startActivity(intent)
        return true
    }

    fun runtimeName(entry: GameEntry): String? {
        return EngineRuntimeRegistry.resolve(entry.gameType)?.displayName
    }

    fun runtimeId(entry: GameEntry): String? {
        return EngineRuntimeRegistry.resolve(entry.gameType)?.runtimeId
    }
}
