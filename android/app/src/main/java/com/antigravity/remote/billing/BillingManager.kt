package com.antigravity.remote.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.functions.FirebaseFunctions
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingUiState(
    val isReady: Boolean = false,
    val products: List<ProductDetails> = emptyList(),
    val activeProductId: String? = null,
    val isPending: Boolean = false,
    val message: String? = null,
)

/**
 * Google Play Billing adapter for the two Interestellar Pro subscription plans.
 *
 * The 7-day trial is an offer configured in Play Console, not a local timer.
 * Before releasing, purchase tokens must also be verified by a secure backend.
 */
class BillingManager(context: Context) {
    companion object {
        const val MONTHLY_PRODUCT_ID = "interestellar_pro_monthly"
        const val ANNUAL_PRODUCT_ID = "interestellar_pro_annual"
        private val PRODUCT_IDS = listOf(MONTHLY_PRODUCT_ID, ANNUAL_PRODUCT_ID)
    }

    private val appContext = context.applicationContext
    private val functions = FirebaseFunctions.getInstance()
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach(::processPurchase)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            publishMessage("Não foi possível iniciar o pagamento: ${result.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(isReady = true, message = null)
                    refresh()
                } else {
                    _state.value = _state.value.copy(
                        isReady = false,
                        message = "Google Play não está disponível neste dispositivo.",
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isReady = false)
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) return
        queryProducts()
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                _state.value = _state.value.copy(
                    activeProductId = active?.products?.firstOrNull(),
                    isPending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING },
                )
                purchases.forEach(::processPurchase)
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String, accountId: String? = null) {
        if (!billingClient.isReady) {
            publishMessage("Aguarde o Google Play carregar os planos e tente novamente.")
            connect()
            return
        }
        val product = _state.value.products.firstOrNull { it.productId == productId }
        val offer = product?.subscriptionOfferDetails
            ?.firstOrNull { offerDetails ->
                offerDetails.pricingPhases.pricingPhaseList.any { phase ->
                    phase.priceAmountMicros == 0L && phase.billingPeriod == "P7D"
                }
            }
            ?: product?.subscriptionOfferDetails?.firstOrNull()
        if (product == null || offer == null) {
            publishMessage("Este plano ainda não está disponível no Google Play.")
            return
        }

        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer.offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .apply { accountId?.let { setObfuscatedAccountId(obfuscateAccountId(it)) } }
            .build()
        val result = billingClient.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            publishMessage("Não foi possível abrir o pagamento: ${result.debugMessage}")
        }
    }

    fun openSubscriptionManagement(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { publishMessage("Não foi possível abrir o gerenciamento da assinatura.") }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(products = details.productDetailsList)
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _state.value = _state.value.copy(isPending = true)
            publishMessage("Pagamento pendente. O acesso será liberado quando o Google Play confirmar.")
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // TODO(production): send purchase.purchaseToken to the secure backend,
        // verify it with Google Play Developer API, then persist the entitlement.
        _state.value = _state.value.copy(
            activeProductId = purchase.products.firstOrNull(),
            isPending = false,
            message = "Compra detectada. Validando sua assinatura com segurança...",
        )
        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build(),
            ) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    publishMessage("A compra foi recebida, mas o reconhecimento ainda está pendente.")
                }
            }
        }
        syncPurchaseWithServer(purchase)
    }

    private fun publishMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private fun syncPurchaseWithServer(purchase: Purchase) {
        val productId = purchase.products.firstOrNull()
        functions.getHttpsCallable("syncSubscriptionPurchase")
            .call(
                mapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId,
                ),
            )
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    activeProductId = productId,
                    isPending = false,
                    message = "Assinatura validada com sucesso. O acesso Pro já pode ser liberado.",
                )
            }
            .addOnFailureListener { error ->
                publishMessage(
                    error.message
                        ?: "A compra foi reconhecida, mas a validação segura da assinatura falhou no servidor.",
                )
            }
    }

    private fun obfuscateAccountId(accountId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
