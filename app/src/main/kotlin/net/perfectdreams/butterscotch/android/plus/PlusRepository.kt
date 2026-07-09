package net.perfectdreams.butterscotch.android.plus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlusRepository(
    private val plusManager: PlusManager = PlusManager
) {

    private val mutex = Mutex()

    /**
     * Atualiza o estado do usuário.
     *
     * Futuramente este método deverá:
     * - consultar o Google Play Billing;
     * - restaurar compras;
     * - validar cache local;
     * - sincronizar com um servidor.
     */
    suspend fun refresh() = mutex.withLock {

        plusManager.update(
            PlusManager.PlusState(
                status = PlusManager.Status.UNKNOWN,
                isLoading = true
            )
        )

        // TODO: BillingManager
        // TODO: Cache
        // TODO: Validação

        plusManager.update(
            PlusManager.PlusState(
                status = PlusManager.Status.FREE
            )
        )
    }

    /**
     * Ativa todos os recursos Plus.
     */
    suspend fun activatePlus() = mutex.withLock {

        plusManager.update(
            PlusManager.PlusState(
                status = PlusManager.Status.PLUS,
                features = PlusManager.Feature.entries.toSet()
            )
        )
    }

    /**
     * Remove a assinatura.
     */
    suspend fun deactivatePlus() = mutex.withLock {

        plusManager.update(
            PlusManager.PlusState(
                status = PlusManager.Status.FREE
            )
        )
    }

    /**
     * Restaura compras.
     */
    suspend fun restorePurchases() = mutex.withLock {
        // TODO: BillingManager.restorePurchases()
    }

    /**
     * Força sincronização.
     */
    suspend fun sync() {
        refresh()
    }

    /**
     * Libera um recurso específico.
     */
    suspend fun grantFeature(
        feature: PlusManager.Feature
    ) = mutex.withLock {

        val state = plusManager.currentState

        plusManager.update(
            state.copy(
                features = state.features + feature
            )
        )
    }

    /**
     * Revoga um recurso específico.
     */
    suspend fun revokeFeature(
        feature: PlusManager.Feature
    ) = mutex.withLock {

        val state = plusManager.currentState

        plusManager.update(
            state.copy(
                features = state.features - feature
            )
        )
    }

    /**
     * Limpa todos os recursos.
     */
    suspend fun clearFeatures() = mutex.withLock {

        val state = plusManager.currentState

        plusManager.update(
            state.copy(
                features = emptySet()
            )
        )
    }

    /**
     * Ativa o Plus apenas para testes.
     */
    suspend fun enableDebugPlus() {
        activatePlus()
    }

    /**
     * Desativa o Plus apenas para testes.
     */
    suspend fun disableDebugPlus() {
        deactivatePlus()
    }
}
