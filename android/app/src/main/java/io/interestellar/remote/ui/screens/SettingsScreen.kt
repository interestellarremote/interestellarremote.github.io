package io.interestellar.remote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.interestellar.remote.BuildConfig
import io.interestellar.remote.billing.BillingUiState
import io.interestellar.remote.ui.RemoteUiState
import io.interestellar.remote.ui.RemoteViewModel
import io.interestellar.remote.ui.hasEffectiveProAccess
import io.interestellar.remote.ui.isPlayPurchaseSyncing
import io.interestellar.remote.ui.components.GlassPanel
import io.interestellar.remote.ui.components.HeroTag
import io.interestellar.remote.ui.components.MetricPill
import io.interestellar.remote.ui.components.SectionHeading
import io.interestellar.remote.ui.theme.BrandGold
import io.interestellar.remote.ui.theme.BrandTeal
import io.interestellar.remote.ui.theme.StatusError

@Composable
fun SettingsScreen(
    state: RemoteUiState,
    vm: RemoteViewModel,
    onLogout: () -> Unit,
    billingState: BillingUiState = BillingUiState(),
    onManageSubscription: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onOpenPcDownload: () -> Unit = {},
) = LazyColumn(
    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    item {
        GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
            SectionHeading(
                eyebrow = "Preferências",
                title = "Configurações",
                subtitle = "Gerencie assinatura, sincronização, bridge e o vínculo com o computador selecionado.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricPill(value = BuildConfig.VERSION_NAME, label = "app", accent = BrandTeal)
                MetricPill(value = state.bridgeVersion ?: "sync", label = "bridge", accent = BrandGold)
                MetricPill(value = state.protocolVersion.toString(), label = "protocolo", accent = MaterialTheme.colorScheme.primary)
            }
        }
    }
    item {
        GlassPanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
            Text("Bridge para PC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Baixe o Bridge na página oficial do projeto no GitHub Pages. O fluxo recomendado é instalar no Windows, autenticar o CLI já configurado no computador, abrir o Bridge e então parear pelo QR Code.")
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "Pré-requisitos: Windows 10/11, internet ativa, sessão do CLI já autenticada no computador e um computador confiável para o pareamento.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Segurança: o instalador atual não é assinado digitalmente. Baixe somente pela página oficial, confira o hash SHA-256 publicado lá e aceite o QR apenas em computadores seus.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenPcDownload) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir página oficial")
            }
        }
    }
    item {
        SubscriptionCard(
            state = state,
            billingState = billingState,
            onManageSubscription = onManageSubscription,
            onOpenSubscription = onOpenSubscription,
        )
    }
    if (BuildConfig.DEBUG) {
        item {
            DevTestCard(
                state = state,
                onToggleDevPro = vm::setDevForcePro,
            )
        }
    }
    item {
        GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandTeal) {
            Text("Backup e histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Backup criptografado automático após cada conversa. Para restaurar em outro celular, pareie novamente o mesmo computador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroTag("backup automático")
                HeroTag("histórico cifrado")
            }
        }
    }
    if (state.protocolVersion != 1) item {
        GlassPanel(modifier = Modifier.fillMaxWidth(), accent = StatusError) {
            Text(
                "Versões incompatíveis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("Atualize o app e a bridge antes de executar novas tarefas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        GlassPanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.outline) {
            Text("Dispositivo selecionado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.selectedDevice ?: "Nenhum computador selecionado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.selectedDevice != null) {
                Button(
                    onClick = vm::revokeSelectedDevice,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Revogar computador selecionado")
                }
            }
            OutlinedButton(onClick = onLogout) { Text("Sair da conta") }
        }
    }
}

@Composable
private fun SubscriptionCard(
    state: RemoteUiState,
    billingState: BillingUiState,
    onManageSubscription: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    val active = state.hasEffectiveProAccess(billingState)
    val playStoreActive = state.isPro && !state.devForcePro
    val playPurchaseSyncing = state.isPlayPurchaseSyncing(billingState)
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = if (active) BrandGold else MaterialTheme.colorScheme.primary,
    ) {
        Text("Interestellar Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            when {
                playStoreActive -> "Sua assinatura está ativa via Google Play."
                playPurchaseSyncing -> "Compra reconhecida pelo Google Play. O chat já fica liberado enquanto a assinatura é confirmada no servidor."
                state.devForcePro -> "Modo Pro ativado para testes (Dev Override)."
                else -> "Plano Gratuito: 10 mensagens/dia. Assine o Pro para mensagens ilimitadas (7 dias grátis)."
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTag(
                when {
                    playStoreActive -> "ativo"
                    playPurchaseSyncing -> "liberando"
                    active -> "ativo"
                    else -> "free (${state.dailyMessageCount}/${state.dailyMessageLimit} msgs hoje)"
                },
                warm = active,
            )
            if (billingState.isPending) {
                HeroTag("pagamento pendente", warm = true)
            }
        }
        if (playStoreActive) {
            OutlinedButton(onClick = onManageSubscription) { Text("Gerenciar assinatura") }
        } else {
            Button(onClick = onOpenSubscription) { Text(if (active) "Ver detalhes da assinatura" else "Conhecer planos") }
        }
        billingState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DevTestCard(
    state: RemoteUiState,
    onToggleDevPro: (Boolean) -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
        SectionHeading(
            eyebrow = "Ambiente de Testes",
            title = "Alternar Modo Free / Premium",
            subtitle = "Alterne o plano instantaneamente para testar a cota gratuita e os recursos do Pro.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Simular Assinatura Pro (Dev)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.devForcePro) "Assinatura Pro forçada (uso ilimitado)"
                    else "Modo Free ativo (limite de 10 msgs/dia)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.devForcePro,
                onCheckedChange = onToggleDevPro,
            )
        }
    }
}

