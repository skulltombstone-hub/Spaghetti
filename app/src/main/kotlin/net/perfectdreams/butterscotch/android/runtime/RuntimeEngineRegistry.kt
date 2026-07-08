package net.perfectdreams.butterscotch.android.runtime

import net.perfectdreams.butterscotch.android.library.GameEntry

/**
 * Central registry for runtime engines hosted by Spaghetti.
 *
 * The app already knows the abstract engine family of each game. The missing piece is a single
 * registry that can resolve the right runtime implementation from that family without the rest of
 * the UI needing to know about individual engines.
 *
 * Each engine module can register itself here during startup.
 */
interface RuntimeEngineFactory {
    val kind: EngineKind

    /**
     * Gives a factory a chance to reject a game entry even if the high-level kind matches.
     * This is useful when two engines share a family but support different subformats.
     */
    fun canHandle(entry: GameEntry): Boolean = RuntimeEngineResolver.supports(entry.gameType, kind)

    fun create(
        context: EngineSessionContext,
        callbacks: EngineHostCallbacks
    ): RuntimeEngine
}

object RuntimeEngineRegistry {
    private val factories = linkedMapOf<EngineKind, RuntimeEngineFactory>()

    /**
     * Register or replace a factory for a given engine kind.
     * Later registrations win, so feature modules can override defaults in tests.
     */
    @Synchronized
    fun register(factory: RuntimeEngineFactory) {
        factories[factory.kind] = factory
    }

    @Synchronized
    fun unregister(kind: EngineKind) {
        factories.remove(kind)
    }

    @Synchronized
    fun clear() {
        factories.clear()
    }

    @Synchronized
    fun isRegistered(kind: EngineKind): Boolean = factories.containsKey(kind)

    @Synchronized
    fun registeredKinds(): List<EngineKind> = factories.keys.toList()

    @Synchronized
    fun resolveFactory(entry: GameEntry): RuntimeEngineFactory? {
        return factories.values.firstOrNull { it.canHandle(entry) }
    }

    @Synchronized
    fun resolveKind(entry: GameEntry): EngineKind {
        return RuntimeEngineResolver.resolveKind(entry.gameType)
    }

    @Synchronized
    fun create(
        context: EngineSessionContext,
        callbacks: EngineHostCallbacks
    ): RuntimeEngine {
        val factory = resolveFactory(context.entry)
            ?: error("No runtime engine registered for ${context.entry.gameType}")

        return factory.create(context, callbacks)
    }
}
