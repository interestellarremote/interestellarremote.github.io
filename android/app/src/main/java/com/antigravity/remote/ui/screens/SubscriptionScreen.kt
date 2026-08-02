package com.antigravity.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.remote.billing.BillingManager
import com.antigravity.remote.billing.BillingUiState
import com.antigravity.remote.ui.components.GlassPanel
import com.antigravity.remote.ui.components.HeroTag
import com.antigravity.remote.ui.components.MetricPill
import com.antigravity.remote.ui.components.SectionHeading
import com.antigravity.remote.ui.theme.BrandGold
import com.antigravity.remote.ui.theme.BrandTeal
import com.antigravity.remote.ui.theme.NeonCyan

private enum class SubscriptionPlan(val productId: String) {
    MONTHLY(BillingManager.MONTHLY_PRODUCT_ID),
    ANNUAL(BillingManager.ANNUAL_PRODUCT_ID),
}

@Composable
fun SubscriptionScreen(
    billingState: BillingUiState,
    subscriptionActive: Boolean,
    onPurchase: (String) -> Unit,
    onManageSubscription: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedPlan by rememberSaveable { mutableStateOf(SubscriptionPlan.ANNUAL) }
    val purchaseDetected = billingState.activeProductId != null
    val active = subscriptionActive
    val monthlyPrice = billingState.priceLabel(
        BillingManager.MONTHLY_PRODUCT_ID,
        "R$ 19,90",
    )
    val annualPrice = billingState.priceLabel(
        BillingManager.ANNUAL_PRODUCT_ID,
        "R$ 159,90",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                }
                Text(
                    "Assinatura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
                SectionHeading(
                    eyebrow = "Membership",
                    title = "Interestellar Pro",
                    subtitle = if (active) {
                        "Sua assinatura está ativa e pronta para uso."
                    } else {
                        "Use todos os recursos gratuitamente por 7 dias e decida depois."
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricPill(value = "7 dias", label = "trial", accent = BrandGold)
                    MetricPill(value = monthlyPrice, label = "mensal", accent = BrandTeal)
                    MetricPill(value = annualPrice, label = "anual", accent = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroTag("sem fidelidade")
                    HeroTag("google play")
                    HeroTag("cancelamento fácil", warm = true)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumBenefit("Acesso completo aos recursos do Interestellar Remote")
                PremiumBenefit("Controle seguro dos seus projetos pelo celular")
                PremiumBenefit("Histórico e sincronização com criptografia")
                PremiumBenefit("Novos recursos incluídos durante a assinatura")
            }
        }
        if (active) {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandTeal) {
                    Text(
                        "Interestellar Pro ativo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Seu plano é administrado com segurança pelo Google Play.")
                    Button(
                        onClick = onManageSubscription,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Gerenciar assinatura")
                    }
                }
            }
        } else if (purchaseDetected) {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
                    Text(
                        "Compra detectada, validação em andamento",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("O Google Play reconheceu a compra, mas o acesso Pro só é liberado após a validação segura no servidor.")
                    Button(
                        onClick = onManageSubscription,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Gerenciar assinatura")
                    }
                }
            }
        } else {
            item {
                Text(
                    "Escolha seu plano",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                PlanCard(
                    title = "Mensal",
                    price = "$monthlyPrice por mês",
                    detail = "Flexibilidade para cancelar quando quiser",
                    selected = selectedPlan == SubscriptionPlan.MONTHLY,
                    onClick = { selectedPlan = SubscriptionPlan.MONTHLY },
                )
            }
            item {
                PlanCard(
                    title = "Anual",
                    price = "$annualPrice por ano",
                    detail = "Equivale a R$ 13,33/mês",
                    badge = "ECONOMIZE 33%",
                    selected = selectedPlan == SubscriptionPlan.ANNUAL,
                    onClick = { selectedPlan = SubscriptionPlan.ANNUAL },
                )
            }
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = BrandGold)
                        Column {
                            Text("Economia no plano anual", fontWeight = FontWeight.Bold)
                            Text(
                                "Você economiza R$ 78,90 por ano comparado a 12 mensalidades.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onPurchase(selectedPlan.productId) },
                    enabled = !billingState.isPending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (billingState.isPending) "Pagamento pendente"
                        else "Começar 7 dias grátis",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Text(
                    "Para novas assinaturas elegíveis. Após os 7 dias, o Google Play fará a cobrança do plano escolhido. Você pode cancelar antes do fim do teste para não pagar. O preço e as condições finais são exibidos pelo Google Play antes da confirmação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        billingState.message?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PremiumBenefit(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            },
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    badge?.let {
                        Surface(
                            color = BrandGold,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                it,
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(price, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun BillingUiState.priceLabel(productId: String, fallback: String): String =
    products.firstOrNull { it.productId == productId }
        ?.subscriptionOfferDetails
        ?.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        ?.lastOrNull { it.priceAmountMicros > 0L }
        ?.formattedPrice
        ?: fallback
