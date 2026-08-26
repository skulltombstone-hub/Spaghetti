package net.perfectdreams.butterscotch.android.runtime

import android.util.Log
import net.perfectdreams.butterscotch.android.library.GameEntry

/**
 * Central registry for every runnable engine/runtime supported by Spaghetti.
 *
 * The launcher never needs to know which Activity belongs to a GameType.
 * It resolves the GameType to an EngineRuntime and asks that runtime for
 * the appropriate launch Intent.
 */
object EngineRuntimeRegistry {

    private const val TAG =
        "EngineRuntimeRegistry"

    private val runtimes:
        MutableList<EngineRuntime> =
        mutableListOf()

    @Volatile
    private var initialized =
        false

    /**
     * Registers a runtime once.
     *
     * Runtime IDs are stable and therefore act as the unique key.
     */
    @Synchronized
    fun register(
        runtime: EngineRuntime
    ) {
        if (
            runtimes.any {
                it.runtimeId ==
                    runtime.runtimeId
            }
        ) {
            return
        }

        runtimes += runtime

        Log.d(
            TAG,
            "Registered runtime " +
                "${runtime.runtimeId} " +
                "(${runtime.displayName})"
        )
    }

    /**
     * Registers all runtimes that are actually implemented
     * at the current stage of the Android project.
     *
     * New console runtimes should be added here only after their
     * Runtime + Activity pair has actually been implemented.
     */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) {
            return
        }

        initialized = true

        register(
            GameMakerRuntime
        )

        register(
            HtmlRuntime
        )

        register(
            Love2DRuntime
        )

        register(
            FlashRuntime
        )
    }

    /**
     * Finds the runtime capable of executing a given GameType.
     */
    fun resolve(
        gameType: GameEntry.GameType
    ): EngineRuntime? {
        ensureInitialized()

        return runtimes.firstOrNull {
            it.supports(gameType)
        }
    }

    /**
     * Returns an immutable snapshot of the current registry.
     */
    fun all(): List<EngineRuntime> {
        ensureInitialized()

        return runtimes.toList()
    }
}
