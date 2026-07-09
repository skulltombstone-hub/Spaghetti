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
     * - sincronizar com um servidor (se existir).
     */
    suspend fun refresh() = mutex.withLock {
        plusManager.setLoading()

        // TODO: Consultar BillingManager
        // TODO: Consultar cache
        // TODO: Validar assinatura

        plusManager.setFree()
    }

    /**
     * Chamado quando uma compra é reconhecida.
     */
    suspend fun activatePlus() = mutex.withLock {
        plusManager.setPlus()
    }

    /**
     * Chamado quando a assinatura expira,
     * é cancelada ou a validação falha.
     */
    suspend fun deactivatePlus() = mutex.withLock {
        plusManager.setFree()
    }

    /**
     * Restaura compras.
     */
    suspend fun restorePurchases() = mutex.withLock {
        // TODO: BillingManager.restorePurchases()
    }

    /**
     * Força uma atualização da assinatura.
     */
    suspend fun sync() = mutex.withLock {
        refresh()
    }

    /**
     * Libera apenas um recurso específico.
     */
    suspend fun grantFeature(
        feature: PlusManager.Feature
    ) = mutex.withLock {
        plusManager.grantFeature(feature)
    }

    /**
     * Revoga um recurso específico.
     */
    suspend fun revokeFeature(
        feature: PlusManager.Feature
    ) = mutex.withLock {
        plusManager.revokeFeature(feature)
    }

    /**
     * Usado apenas durante desenvolvimento.
     */
    suspend fun enableDebugPlus() = mutex.withLock {
        plusManager.setPlus()
    }

    /**
     * Usado apenas durante desenvolvimento.
     */
    suspend fun disableDebugPlus() = mutex.withLock {
        plusManager.setFree()
    }
}
