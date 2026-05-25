package net.perfectdreams.butterscotch.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * JNI bridge to the Butterscotch native library.
 *
 * The Android side "drives" the runner the same way the web build does: we hand it a data.win,
 * call [nativeStep] once per frame from the GLSurfaceView render thread, and tear things down
 * with [nativeStopRunner] when leaving the activity.
 *
 * Native -> Kotlin push notifications (e.g. [currentTitle]) come in through `@JvmStatic fun`
 * methods that the C side invokes via cached jmethodIDs (see JNI_OnLoad in src/android/main.c).
 * Compose state set from those callbacks propagates to the UI via the snapshot system - safe to
 * write from any thread.
 */
object ButterscotchNative {
    init {
        System.loadLibrary("butterscotch")
        init()
    }

    external fun init()

    /**
     * Parses data.win, creates the VM/runner/renderer, and fires Game Start + first room.
     * Must be called from the GL render thread (the native side calls into GL).
     */
    external fun startRunner(dataWinPath: String, savesPath: String): Boolean

    /** Inform native of the GL surface size in pixels. Call after [nativeStartRunner]. */
    external fun onSurfaceChanged(width: Int, height: Int)

    /** Run one game step + render one frame. Returns false when the runner has exited. */
    external fun step(): Boolean

    /** Forward a keyboard event (GameMaker vk_* code) to the runner. */
    external fun onKey(keyCode: Int, isDown: Boolean)

    /** Tear down the runner. Must be called from the GL render thread. */
    external fun stopRunner()

    // ===[ Native -> Kotlin push state ]===

    /**
     * The current window title - whatever the game has most recently set via `window_set_caption`,
     * or its initial `gen8.displayName` if it hasn't. Same string GLFW puts in its OS window title.
     * Backed by [mutableStateOf] so Compose recomposes automatically when the title changes.
     */
    var currentTitle: String? by mutableStateOf(null)
        private set

    /**
     * Called from native (src/android/main.c::fireOnTitleChanged) any time setWindowTitle fires.
     * @JvmStatic + the matching signature descriptor in JNI_OnLoad is what makes this discoverable
     * from C. Runs on whatever thread the callback fired from (usually the render thread).
     *
     * Compose's snapshot system serializes [mutableStateOf] writes across threads, so we don't need
     * to hop to the main thread just to update [currentTitle]. If a future callback needs to do
     * something genuinely main-thread-only (Toast, system service call), hop with
     * `Handler(Looper.getMainLooper()).post { ... }` inside that specific callback.
     */
    @JvmStatic
    fun onTitleChanged(title: String) {
        currentTitle = title
    }

    /**
     * The game's native dimensions, taken from gen8.defaultWindowWidth/Height (essentially "the
     * resolution the game was designed for"). Used to compute the aspect ratio that the GLSurfaceView
     * should preserve. Null until the runner has parsed a data.win.
     */
    var currentGameSize: IntSize? by mutableStateOf(null)
        private set

    /**
     * Called from native (src/android/main.c::startRunner) once per session, right after data.win is
     * parsed. The dimensions don't change for the runner's lifetime, so this fires once.
     */
    @JvmStatic
    fun onGameSizeChanged(width: Int, height: Int) {
        currentGameSize = IntSize(width, height)
    }
}
