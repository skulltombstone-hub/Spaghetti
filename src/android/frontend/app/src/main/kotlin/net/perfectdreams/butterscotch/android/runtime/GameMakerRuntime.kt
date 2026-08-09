package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.ButterscotchNativeActivity
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * GameMaker/Butterscotch runtime bridge.
 *
 * This keeps the old runner isolated behind the new runtime interface.
 */
object GameMakerRuntime : EngineRuntime {
    override val runtimeId: String = "gamemaker"
    override val displayName: String = "GameMaker"

    override fun supports(gameType: GameEntry.GameType): Boolean {
        return gameType is GameEntry.GameType.GameMakerStudio
    }

    override fun buildLaunchIntent(
        context: Context,
        gameId: UUID
    ): Intent {
        return Intent(context, ButterscotchNativeActivity::class.java).apply {
            putExtra(ButterscotchNativeActivity.EXTRA_GAME_ID, gameId.toString())
        }
    }
}
