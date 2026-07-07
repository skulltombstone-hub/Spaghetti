package net.perfectdreams.butterscotch.android.runtime

enum class RuntimeKind(
        val displayName: String
) {
        BUTTERSCOTCH("GameMaker"),
            RUFFLE("Adobe Flash"),
                HTML("HTML5"),
                    MKXP_Z("RPG Maker")
}
