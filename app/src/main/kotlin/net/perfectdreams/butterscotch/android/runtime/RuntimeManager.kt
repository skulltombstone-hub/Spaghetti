package net.perfectdreams.butterscotch.android.runtime

object RuntimeManager {

        private val runtimes = mutableMapOf<RuntimeKind, GameRuntime>()

            fun register(runtime: GameRuntime) {
                        runtimes[runtime.kind] = runtime
            }

                fun get(kind: RuntimeKind): GameRuntime? {
                            return runtimes[kind]
                }

                    fun getAll(): Collection<GameRuntime> {
                                return runtimes.values
                    }

                        fun isRegistered(kind: RuntimeKind): Boolean {
                                    return runtimes.containsKey(kind)
                        }
}
