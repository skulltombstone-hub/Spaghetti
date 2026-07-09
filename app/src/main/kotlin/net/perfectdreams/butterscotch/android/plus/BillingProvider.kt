package net.perfectdreams.butterscotch.android.plus

interface BillingProvider {

    /**
     * Inicializa o sistema de cobrança.
     */
    suspend fun initialize()

    /**
     * Atualiza o estado da assinatura.
     */
    suspend fun refresh()

    /**
     * Restaura compras.
     */
    suspend fun restorePurchases()

    /**
     * Inicia a compra da assinatura.
     */
    suspend fun purchasePlus()

    /**
     * Retorna se o usuário possui Plus.
     */
    suspend fun isPlus(): Boolean

    /**
     * Recursos disponíveis.
     */
    suspend fun availableFeatures(): Set<PlusManager.Feature>

    /**
     * Libera recursos utilizados pelo provider.
     */
    fun dispose()
}
