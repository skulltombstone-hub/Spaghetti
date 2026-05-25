package net.perfectdreams.butterscotch.android

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Drives Butterscotch from a [GLSurfaceView]'s render thread.
 *
 * Lifecycle:
 *  - [onSurfaceCreated] fires on the render thread once an EGL context is alive. We start the
 *    native runner here because [ButterscotchNative.nativeStartRunner] needs a current GL context
 *    to build the renderer.
 *  - [onSurfaceChanged] fires on resize / orientation change.
 *  - [onDrawFrame] fires once per VSYNC in `RENDERMODE_CONTINUOUSLY`; it runs one game step.
 *
 * Teardown is initiated from the Activity by calling [requestStop]; the next [onDrawFrame] then
 * disposes the runner on the render thread.
 */
class ButterscotchRenderer(
    private val dataWinPath: String,
    private val savesPath: String,
    private val onExit: () -> Unit
) : GLSurfaceView.Renderer {

    @Volatile private var started = false
    @Volatile private var stopRequested = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (started) {
            Log.w(TAG, "Tried to start the runner while it is already started! Bug? Or maybe the device was rotated?")
            return
        }

        Log.i(TAG, "Starting Runner...")
        val ok = ButterscotchNative.startRunner(dataWinPath, savesPath)
        if (!ok) {
            Log.e(TAG, "Failed to start Runner!")
            stopRequested = true
            onExit()
            return
        }
        started = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (!started)
            return

        ButterscotchNative.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!started) return
        if (stopRequested) {
            Log.w(TAG, "Stop has been requested, bailing!")
            ButterscotchNative.stopRunner()
            started = false
            onExit()
            return
        }
        val keepRunning = ButterscotchNative.step()
        if (!keepRunning) {
            Log.w(TAG, "Butterscotch has requested to stop running, bailing!")
            ButterscotchNative.stopRunner()
            started = false
            onExit()
        }
    }

    fun requestStop() {
        stopRequested = true
    }

    companion object {
        private const val TAG = "ButterscotchRenderer"
    }
}
