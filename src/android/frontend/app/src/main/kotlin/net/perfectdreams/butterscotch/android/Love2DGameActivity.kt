package net.perfectdreams.butterscotch.android

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.io.File
import java.util.UUID

/**
 * Activity responsible for running imported LÖVE 2D games.
 *
 * Architecture:
 *
 * Android Activity
 *      |
 *      +-- SurfaceView
 *      |
 *      +-- Love2DNative (JNI bridge)
 *      |
 *      +-- native LÖVE runtime
 *
 * The .love file itself is never interpreted by Kotlin. The native runtime
 * receives the file path and owns rendering, audio and the LÖVE event loop.
 */
class Love2DGameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"

        private const val TAG = "Love2DGameActivity"
    }

    private var surfaceView: SurfaceView? = null

    private var nativeInitialized = false

    private var currentGameId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemBars()

        val gameLibrary = Libraries.loadGameLibrary(applicationContext)
        val layoutLibrary = Libraries.loadLayoutLibrary(applicationContext)

        val gameId = intent
            .getStringExtra(EXTRA_GAME_ID)
            ?.let {
                runCatching {
                    UUID.fromString(it)
                }.getOrNull()
            }

        if (gameId == null) {
            finish()
            return
        }

        currentGameId = gameId

        val entry = gameLibrary.findById(gameId)

        if (
            entry == null ||
            entry.gameType !is GameEntry.GameType.Love2D
        ) {
            finish()
            return
        }

        val loveGameType = entry.gameType as GameEntry.GameType.Love2D

        val gameFile = File(
            gameLibrary.bundleDir(entry),
            loveGameType.filename
        )

        if (!gameFile.exists() || !gameFile.isFile) {
            finish()
            return
        }

        val savesDirectory = gameLibrary
            .savesDir(entry)
            .apply { mkdirs() }

        /*
         * The native library is loaded only here, when a LÖVE Activity
         * actually starts. This prevents the whole application from
         * depending on the native library just to launch its main screen.
         */
        val nativeAvailable = runCatching {
            Love2DNative.ensureLoaded()
            true
        }.getOrElse {
            android.util.Log.e(
                TAG,
                "LÖVE native library could not be loaded",
                it
            )
            false
        }

        if (!nativeAvailable) {
            finish()
            return
        }

        setContent {
            ButterscotchAndroidTheme {
                Love2DGameContent(
                    entry = entry,
                    layoutLibrary = layoutLibrary,
                    onSurfaceCreated = { surface ->
                        startNativeRuntime(
                            gameFile = gameFile,
                            savesDirectory = savesDirectory,
                            surface = surface
                        )
                    },
                    onSurfaceChanged = { width, height ->
                        Love2DNative.setSurfaceSize(width, height)
                    },
                    onSurfaceDestroyed = {
                        Love2DNative.detachSurface()
                    },
                    onKeyboardEvent = { keyCode, pressed, repeatCount ->
                        Love2DNative.onAndroidKey(
                            keyCode,
                            pressed,
                            repeatCount
                        )
                    },
                    onGamepadButton = { device, button, pressed ->
                        Love2DNative.onVirtualGamepadButton(
                            device,
                            button,
                            pressed
                        )
                    },
                    onTouchEvent = { event ->
                        forwardTouchEvent(event)
                    },
                    onExit = {
                        finish()
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        /*
         * Let Android handle Back normally. Game events are forwarded to
         * LÖVE so keyboard and controller input can reach the game.
         */
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }

        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return super.dispatchKeyEvent(event)
        }

        Love2DNative.onAndroidKey(
            event.keyCode,
            pressed,
            event.repeatCount
        )

        return true
    }

    override fun onPause() {
        super.onPause()

        if (nativeInitialized) {
            runCatching {
                Love2DNative.pauseRuntime()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        if (nativeInitialized) {
            runCatching {
                Love2DNative.resumeRuntime()
            }
        }
    }

    override fun onDestroy() {
        if (nativeInitialized) {
            runCatching {
                Love2DNative.stopRuntime()
            }

            nativeInitialized = false
        }

        surfaceView = null

        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun startNativeRuntime(
        gameFile: File,
        savesDirectory: File,
        surface: Surface
    ) {
        if (nativeInitialized) {
            Love2DNative.attachSurface(surface)
            return
        }

        try {
            Love2DNative.setSurfaceSize(
                surfaceView?.width ?: 0,
                surfaceView?.height ?: 0
            )

            Love2DNative.initializeRuntime(
                gameFile.absolutePath,
                savesDirectory.absolutePath
            )

            Love2DNative.attachSurface(surface)

            Love2DNative.startRuntime()

            nativeInitialized = true
        } catch (t: Throwable) {
            android.util.Log.e(
                TAG,
                "Failed to start LÖVE runtime",
                t
            )

            nativeInitialized = false
            finish()
        }
    }

    private fun forwardTouchEvent(
        event: MotionEvent
    ) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {

                val actionIndex = event.actionIndex

                if (
                    event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    val pointerId =
                        event.getPointerId(actionIndex)

                    val x =
                        event.getX(actionIndex)

                    val y =
                        event.getY(actionIndex)

                    Love2DNative.onTouch(
                        event.actionMasked,
                        pointerId,
                        x,
                        y,
                        event.getPressure(actionIndex)
                    )
                } else {
                    for (index in 0 until event.pointerCount) {
                        Love2DNative.onTouch(
                            MotionEvent.ACTION_MOVE,
                            event.getPointerId(index),
                            event.getX(index),
                            event.getY(index),
                            event.getPressure(index)
                        )
                    }
                }
            }
        }
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

/**
 * Main Compose container for the LÖVE runtime.
 */
@Composable
private fun Love2DGameContent(
    entry: GameEntry,
    layoutLibrary: LayoutLibrary,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceChanged: (Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onKeyboardEvent: (Int, Boolean, Int) -> Unit,
    onGamepadButton: (Int, Int, Boolean) -> Unit,
    onTouchEvent: (MotionEvent) -> Unit,
    onExit: () -> Unit
) {
    var showControls by remember {
        mutableStateOf(true)
    }

    var showMenu by remember {
        mutableStateOf(false)
    }

    val configuration = LocalConfiguration.current

    LaunchedEffect(configuration.orientation) {
        Love2DNative.releaseAllInputs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Love2DSurface(
            onSurfaceCreated = onSurfaceCreated,
            onSurfaceChanged = onSurfaceChanged,
            onSurfaceDestroyed = onSurfaceDestroyed,
            onTouchEvent = onTouchEvent
        )

        if (showControls && !showMenu) {
            Love2DControls(
                entry = entry,
                layoutLibrary = layoutLibrary,
                onKeyboardEvent = onKeyboardEvent,
                onGamepadButton = onGamepadButton,
                onMenu = {
                    showMenu = true
                }
            )
        }

        if (showMenu) {
            Love2DMenu(
                controlsVisible = showControls,
                onResume = {
                    showMenu = false
                },
                onToggleControls = {
                    showControls = !showControls
                    showMenu = false
                    Love2DNative.releaseAllInputs()
                },
                onExit = {
                    Love2DNative.releaseAllInputs()
                    showMenu = false
                    onExit()
                }
            )
        }
    }
}

/**
 * Surface owned by the native LÖVE runtime.
 *
 * A SurfaceView is used instead of GLSurfaceView because the LÖVE side must
 * own its own EGL/OpenGL lifecycle.
 */
@Composable
private fun Love2DSurface(
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceChanged: (Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTouchEvent: (MotionEvent) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false
                    )

                    /*
                     * Touch events themselves are captured by the Android
                     * SurfaceView below. Compose only keeps this gesture
                     * pipeline alive so the view remains interactive.
                     */
                    waitForUpOrCancellation()
                }
            },
        factory = { context ->
            SurfaceView(context).apply {

                surfaceViewReference = this

                holder.addCallback(
                    object : SurfaceHolder.Callback {

                        override fun surfaceCreated(
                            holder: SurfaceHolder
                        ) {
                            onSurfaceCreated(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            onSurfaceChanged(
                                width,
                                height
                            )
                        }

                        override fun surfaceDestroyed(
                            holder: SurfaceHolder
                        ) {
                            onSurfaceDestroyed()
                        }
                    }
                )

                setOnTouchListener { _, event ->
                    onTouchEvent(event)
                    true
                }
            }
        },
        update = {
            surfaceViewReference = it
        }
    )
}

/**
 * Current SurfaceView reference.
 *
 * Kept outside Compose so the Activity can read the dimensions when the
 * native runtime starts.
 */
private var surfaceViewReference: SurfaceView? = null

private val SurfaceView
    .widthSafe: Int
    get() = width.coerceAtLeast(1)

/**
 * Virtual controls shared conceptually with the existing GamepadLayout system.
 *
 * We intentionally do not use GameControls.kt here because that component
 * is coupled to the GameMaker/Butterscotch input state.
 */
@Composable
private fun Love2DControls(
    entry: GameEntry,
    layoutLibrary: LayoutLibrary,
    onKeyboardEvent: (Int, Boolean, Int) -> Unit,
    onGamepadButton: (Int, Int, Boolean) -> Unit,
    onMenu: () -> Unit
) {
    val configuration = LocalConfiguration.current

    val isLandscape =
        configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val layoutId =
        if (isLandscape) {
            entry.landscapeLayout
        } else {
            entry.portraitLayout
        }

    val fallbackId =
        if (isLandscape) {
            LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT
        } else {
            LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT
        }

    val layout =
        layoutLibrary.findById(layoutId)
            ?: layoutLibrary.findById(fallbackId)
            ?: return

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasHeight =
            if (isLandscape) {
                maxHeight
            } else {
                maxHeight * 0.62f
            }

        val canvasTop =
            if (isLandscape) {
                0.dp
            } else {
                maxHeight - canvasHeight
            }

        layout.elements.forEach { element ->

            when (element) {

                is GamepadElement.Key -> {
                    Love2DKeyButton(
                        element = element,
                        onKeyboardEvent = onKeyboardEvent,
                        onGamepadButton = onGamepadButton,
                        modifier = love2DPlacement(
                            element = element,
                            canvasTop = canvasTop,
                            canvasHeight = canvasHeight
                        )
                    )
                }

                is GamepadElement.Joystick -> {
                    Love2DJoystick(
                        element = element,
                        onKeyboardEvent = onKeyboardEvent,
                        onGamepadButton = onGamepadButton,
                        modifier = love2DPlacement(
                            element = element,
                            canvasTop = canvasTop,
                            canvasHeight = canvasHeight
                        )
                    )
                }

                is GamepadElement.Menu -> {
                    Love2DMenuButton(
                        element = element,
                        onClick = onMenu,
                        modifier = love2DPlacement(
                            element = element,
                            canvasTop = canvasTop,
                            canvasHeight = canvasHeight
                        )
                    )
                }

                /*
                 * LÖVE itself supports gamepad/joystick input, but virtual
                 * analog mapping needs a dedicated native axis implementation.
                 * We leave these for the next input/runtime phase instead of
                 * silently converting analog input into four digital buttons.
                 */
                is GamepadElement.AnalogJoystick -> Unit

                /*
                 * These controls are specific to the existing GameMaker
                 * runner and have no LÖVE-equivalent behavior at this layer.
                 */
                is GamepadElement.MouseButton -> Unit

                is GamepadElement.FastForward -> Unit
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.love2DPlacement(
    element: GamepadElement,
    canvasTop: Dp,
    canvasHeight: Dp
): Modifier {
    val referenceSize =
        if (maxWidth < canvasHeight) {
            maxWidth
        } else {
            canvasHeight
        }

    val elementSize =
        referenceSize * element.scale.toFloat()

    val x =
        maxWidth * element.positionX.toFloat() -
            elementSize / 2f

    val y =
        canvasTop +
            canvasHeight * element.positionY.toFloat() -
            elementSize / 2f

    return Modifier
        .offset(
            x = x,
            y = y
        )
        .size(elementSize)
        .then(
            Modifier
        )
}

@Compo
