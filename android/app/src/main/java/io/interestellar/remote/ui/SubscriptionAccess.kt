package io.interestellar.remote.ui

import io.interestellar.remote.billing.BillingUiState

internal fun BillingUiState.hasRecognizedProPurchase(): Boolean =
    activeProductId != null && !isPending

internal fun RemoteUiState.hasEffectiveProAccess(billingState: BillingUiState): Boolean =
    isPro || billingState.hasRecognizedProPurchase()

internal fun RemoteUiState.isPlayPurchaseSyncing(billingState: BillingUiState): Boolean =
    !isPro && billingState.hasRecognizedProPurchase()
