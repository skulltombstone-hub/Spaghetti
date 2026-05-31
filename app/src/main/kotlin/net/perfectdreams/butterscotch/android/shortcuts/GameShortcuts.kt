package net.perfectdreams.butterscotch.android.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.perfectdreams.butterscotch.android.R
import net.perfectdreams.butterscotch.android.GameActivity
import net.perfectdreams.butterscotch.android.MainActivity
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary

fun requestPinGameShortcut(context: Context, library: GameLibrary, entry: GameEntry): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

    val launchIntent = Intent(MainActivity.ACTION_LAUNCH_GAME)
        .setClass(context, MainActivity::class.java)
        .putExtra(GameActivity.EXTRA_GAME_ID, entry.id.toString())

    val iconFile = library.iconFile(entry)
    val icon = if (iconFile.exists()) {
        val decodedBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)

        if (decodedBitmap != null) {
            IconCompat.createWithBitmap(Bitmap.createScaledBitmap(decodedBitmap, 256, 256, false))
        } else {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }
    } else {
        IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    val info = ShortcutInfoCompat.Builder(context, "game-${entry.id}")
        .setShortLabel(entry.title.take(10))
        .setLongLabel(entry.title.take(25))
        .setIcon(icon)
        .setIntent(launchIntent)
        .build()

    return ShortcutManagerCompat.requestPinShortcut(context, info, null)
}
