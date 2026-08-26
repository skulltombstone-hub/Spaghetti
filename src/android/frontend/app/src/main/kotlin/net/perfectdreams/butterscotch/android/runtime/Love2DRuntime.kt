package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.Love2DGameActivity
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * LÖVE 2D runtime bridge.
 *
 * This runtime is deliberately independent from GameMaker/Butterscotch and
 * HTML/WebView. A LÖVE game is launched by its own Activity and native bridge.
 */
object Love2DRuntime : EngineRuntime {

    override val runtimeId: String = "love2d"

    override val displayName: String = "LÖVE 2D"

    override fun supports(gameType: GameEntry.GameType): Boolean {
        return gameType is GameEntry.GameType.Love2D
    }

    override fun buildLaunchIntent(
        context: Context,
        gameId: UUID
    ): Intent {
        return Intent(context, Love2DGameActivity::class.java).apply {
            putExtra(
                Love2DGameActivity.EXTRA_GAME_ID,
                gameId.toString()
            )
        }
    }
}
