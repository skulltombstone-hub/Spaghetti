package net.perfectdreams.butterscotch.android.runtime

import android.util.Log
import net.perfectdreams.butterscotch.android.library.GameEntry

/**
 * Central registry for all installed runtimes.
 *
 * In version 2.0 this is the place where we will register:
 * - GameMaker / Butterscotch
 * - HTML / WebView
 * - Flash / Ruffle
 * - Love2D
 * - Console runtimes
 *
 * The registry keeps the launcher logic small and predictable.
 */
object EngineRuntimeRegistry {
    private const val TAG = "EngineRuntimeRegistry"

    private val runtimes: MutableList<EngineRuntime> = mutableListOf()

    @Volatile
    private var initialized = false

    /**
     * Register a runtime. Safe to call multiple times, duplicates are ignored
     * by runtimeId.
     */
    @Synchronized
    fun register(runtime: EngineRuntime) {
        if (runtimes.any { it.runtimeId == runtime.runtimeId }) {
            return
        }

        runtimes += runtime
        Log.d(TAG, "Registered runtime ${runtime.runtimeId} (${runtime.displayName})")
    }

    /**
     * Register the built-in runtimes once.
     *
     * This should be called from Application.onCreate() or the launcher entrypoint.
     */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        initialized = true

        register(GameMakerRuntime)
        register(HtmlRuntime)
    }

    /**
     * Finds the runtime that supports the given game type.
     */
    fun resolve(gameType: GameEntry.GameType): EngineRuntime? {
        ensureInitialized()
        return runtimes.firstOrNull { it.supports(gameType) }
    }

    /**
     * Returns all runtimes currently registered.
     */
    fun all(): List<EngineRuntime> {
        ensureInitialized()
        return runtimes.toList()
    }
}
