package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.HtmlGameActivity
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * HTML/WebView runtime bridge.
 */
object HtmlRuntime : EngineRuntime {
    override val runtimeId: String = "html"
    override val displayName: String = "HTML"

    override fun supports(gameType: GameEntry.GameType): Boolean {
        return gameType is GameEntry.GameType.Html
    }

    override fun buildLaunchIntent(
        context: Context,
        gameId: UUID
    ): Intent {
        return Intent(context, HtmlGameActivity::class.java).apply {
            putExtra(HtmlGameActivity.EXTRA_GAME_ID, gameId.toString())
        }
    }
}
