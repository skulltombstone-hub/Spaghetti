package net.perfectdreams.butterscotch.android

import android.content.Context
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameLibrary

object Libraries {
    private var gameLibrary: GameLibrary? = null
    private var layoutLibrary: LayoutLibrary? = null

    fun getGameLibrary() = requireNotNull(gameLibrary)
    fun getLayoutLibrary() = requireNotNull(layoutLibrary)

    fun loadGameLibrary(context: Context) {
        if (gameLibrary != null)
            return

        this.gameLibrary = GameLibrary.load(context)
    }

    fun loadLayoutLibrary(context: Context) {
        if (layoutLibrary != null)
            return

        this.layoutLibrary = LayoutLibrary.load(context)
    }
}