package net.perfectdreams.butterscotch.android

import android.content.res.AssetManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream

/**
 * Hosts the GLSurfaceView that owns Butterscotch's render thread, with a Compose-based virtual
 * gamepad overlay drawn on top.
 *
 * On launch we extract `assets/undertale/` into the app's internal `filesDir/butterscotch/undertale/`
 * if it isn't there yet, then point the runner at it. The "saves" folder lives next to it so
 * GameMaker's `ini_*` / `file_text_open_write` / `buffer_save` calls land in app-private storage.
 */
class GameActivity : ComponentActivity() {

    private var glView: GLSurfaceView? = null
    private var renderer: ButterscotchRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while the game runs - matches the GLFW build's default behavior.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameDir = File(filesDir, GAME_DIR_NAME).apply { mkdirs() }
        val bundleDir = File(gameDir, GAME_ASSET_DIR).apply { mkdirs() }
        val dataWin = File(bundleDir, "data.win")
        val savesDir = File(gameDir, "saves").apply { mkdirs() }

        // Extract the entire bundle (data.win + included files like Undertale's streamed .ogg music)
        // from assets/<GAME_ASSET_DIR>/ into the app's private storage on first launch. We use
        // data.win's presence as a "first launch done" marker - delete it (or uninstall) to force a
        // re-extract after updating the assets folder.
        if (!dataWin.exists()) {
            try {
                extractAssetTree(GAME_ASSET_DIR, bundleDir)
                Log.i(TAG, "Extracted $GAME_ASSET_DIR/ to $bundleDir")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract $GAME_ASSET_DIR from assets", e)
                finish()
                return
            }
            if (!dataWin.exists()) {
                Log.e(TAG, "Expected data.win at $dataWin after extraction; assets are probably empty")
                finish()
                return
            }
        }

        setContent {
            // VirtualKeyState lives here (not inside the gamepad) so we can release-all on
            // orientation changes that reflow the gamepad's Composable tree. If it lived inside
            // GameControls and Compose discarded that subtree across an orientation flip, held keys
            // could "leak" into the runner with no Composable left to release them.
            val keys = remember { VirtualKeyState() }

            // movableContentOf is the crucial bit. The GLSurfaceView is a single AndroidView that
            // gets re-parented (not recreated) when the layout switches between Overlay and Stacked.
            // Without movableContentOf, Compose would dispose the AndroidView in the outgoing layout
            // and create a new one in the incoming layout - which fires the factory again, calls
            // nativeStartRunner again, hits "runner already alive; ignoring", and leaves us with a
            // black screen. movableContent preserves the View instance + EGL context + render
            // thread across the move.
            val gameSurface = remember {
                movableContentOf<Modifier> { modifier ->
                    AndroidView(
                        modifier = modifier,
                        factory = { ctx ->
                            val view = GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(3)
                                // RGBA8, 0 depth, 0 stencil - Butterscotch renders into its own FBOs.
                                setEGLConfigChooser(8, 8, 8, 8, 0, 0)
                                preserveEGLContextOnPause = true
                            }
                            val r = ButterscotchRenderer(
                                dataWinPath = dataWin.absolutePath,
                                savesPath = savesDir.absolutePath,
                                onExit = { runOnUiThread { finish() } }
                            )
                            view.setRenderer(r)
                            view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glView = view
                            renderer = r
                            view
                        }
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                // BoxWithConstraints lets us read the available pixel size, which we need to
                // compute the device aspect ratio. Recomposes when constraints change (e.g.
                // orientation flip), so the layout mode re-picks itself.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gameSize = ButterscotchNative.currentGameSize
                    // Until the runner reports game dimensions (brief window between activity launch
                    // and nativeStartRunner returning), assume landscape 4:3. Wrong default for
                    // portrait mobile games is OK because the layout reflows the moment
                    // onGameSizeChanged fires.
                    val gameAspect = if (gameSize != null && gameSize.height > 0) {
                        gameSize.width.toFloat() / gameSize.height.toFloat()
                    } else {
                        4f / 3f
                    }
                    val deviceAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat()
                    val layoutMode = pickLayoutMode(gameAspect, deviceAspect)

                    // Layout-mode changes happen on orientation flips. Release any held keys so a
                    // "walk left" doesn't survive into the new layout.
                    LaunchedEffect(layoutMode) { keys.releaseAll() }

                    when (layoutMode) {
                        LayoutMode.Overlay -> {
                            // Game fills the screen, controls float on top. Controls naturally sit
                            // on the runner's letterbox bars where game and device aspects differ.
                            Box(Modifier.fillMaxSize()) {
                                gameSurface(Modifier.fillMaxSize())
                                GameControls(keys = keys, modifier = Modifier.fillMaxSize())
                            }
                        }
                        LayoutMode.Stacked -> {
                            // Game pinned to the top at its native aspect; controls fill the empty
                            // area below. safeDrawingPadding keeps the controls clear of gesture
                            // nav bars and notches.
                            Column(Modifier.fillMaxSize()) {
                                gameSurface(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(gameAspect)
                                )
                                GameControls(
                                    keys = keys,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .safeDrawingPadding()
                                )
                            }
                        }
                    }
                }

                // Menu sits above everything else and is always full-screen, so the slide-in
                // sidebar covers both the game and the controls regardless of which layout we picked.
                MenuOverlay(
                    onExitGame = { renderer?.requestStop() },
                    releaseAllKeys = { keys.releaseAll() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
    }

    override fun onPause() {
        glView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        renderer?.let { r ->
            r.requestStop()
            // Give the render thread a chance to run one more onDrawFrame so the native teardown
            // happens on the GL thread (it touches GL resources).
            glView?.queueEvent { /* no-op, just nudges the render thread */ }
        }
        super.onDestroy()
    }

    // Recursively mirrors `assets/<assetPath>/` into `destDir/`. AssetManager.list returns child
    // names without paths; an empty list means `assetPath` is a file (or doesn't exist - we treat
    // those as "file" and let assets.open throw if it's truly missing).
    private fun extractAssetTree(assetPath: String, destDir: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                FileOutputStream(destDir).use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            val grandChildren = assets.list(childAssetPath) ?: emptyArray()
            if (grandChildren.isEmpty()) {
                assets.open(childAssetPath, AssetManager.ACCESS_STREAMING).use { input ->
                    FileOutputStream(childDest).use { output -> input.copyTo(output) }
                }
            } else {
                extractAssetTree(childAssetPath, childDest)
            }
        }
    }

    companion object {
        private const val TAG = "GameActivity"
        // App-private storage layout: filesDir/butterscotch/undertale/{data.win, *.ogg, ...}
        // plus filesDir/butterscotch/saves/ for the GameMaker save area.
        private const val GAME_DIR_NAME = "butterscotch"
        // Drop the full Undertale bundle (data.win + .ogg music + included files) into
        // app/src/main/assets/undertale/. The whole folder is mirrored to internal storage on first launch.
        private const val GAME_ASSET_DIR = "undertale"
    }
}
