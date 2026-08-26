package net.perfectdreams.butterscotch.android.runtime

import android.content.Context
import android.content.Intent
import net.perfectdreams.butterscotch.android.FlashGameActivity
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID

/**
 * Adobe Flash runtime bridge.
 *
 * Flash games are executed through the dedicated FlashGameActivity,
 * which hosts the bundled Ruffle Web runtime.
 *
 * This runtime intentionally does not contain WebView/Ruffle logic.
 * Its job is only to connect GameType.Flash to the correct Activity.
 */
object FlashRuntime : EngineRuntime {

    override val runtimeId: String = "flash"

    override val displayName: String = "Adobe Flash / Ruffle"

    override fun supports(
        gameType: GameEntry.GameType
    ): Boolean {
        return gameType is GameEntry.GameType.Flash
    }

    override fun buildLaunchIntent(
        context: Context,
        gameId: UUID
    ): Intent {
        return Intent(
            context,
            FlashGameActivity::class.java
        ).apply {
            putExtra(
                FlashGameActivity.EXTRA_GAME_ID,
                gameId.toString()
            )
        }
    }
}
