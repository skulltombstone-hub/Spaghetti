package net.perfectdreams.butterscotch.android.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import kotlinx.coroutines.delay

/**
 * Loops over a list of drawable frames, swapping the displayed frame every [frameDurationMs]
 */
@Composable
fun FrameAnimationImage(frames: List<Int>, frameDurationMs: Long, contentDescription: String?, modifier: Modifier = Modifier) {
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(frames) {
        while (true) {
            delay(frameDurationMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Image(
        bitmap = ImageBitmap.imageResource(frames[frameIndex]),
        contentDescription = contentDescription,
        // Nearest-neighbor keeps the pixel-art crisp when scaled up
        filterQuality = FilterQuality.None,
        modifier = modifier,
    )
}
