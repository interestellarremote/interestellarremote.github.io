package com.antigravity.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.Routes
import com.antigravity.remote.ui.components.*
import com.antigravity.remote.ui.theme.*

@Composable
fun DevicesScreen(
    state: RemoteUiState,
    vm: RemoteViewModel,
    nav: NavHostController,
    onScan: () -> Unit,
    onOpenPcDownload: () -> Unit = {},
    subscriptionActive: Boolean = false,
    onOpenSubscription: () -> Unit = {},
) {
    var showPairingSheet by remember { mutableStateOf(false) }
    val onlineCount = remember(state.devices) { state.devices.count { it.online } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroPanel {
                SectionHeading(
                    eyebrow = "Sua órbita remota",
                    title = "Computadores pareados",
                    subtitle = "Conecte com segurança os computadores autorizados e entre direto no fluxo de trabalho do agente.",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricPill(value = "${state.devices.size}", label = "pareados", accent = BrandTeal)
                    MetricPill(value = "$onlineCount", label = "online agora", accent = if (onlineCount > 0) StatusActive else StatusIdle)
                    MetricPill(
                        value = if (subscriptionActive) "PRO" else "FREE",
                        label = "plano atual",
                        accent = if (subscriptionActive) BrandGold else NeonBlue,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionTile(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Parear",
                    subtitle = "Adicione outro computador pelo QR Code com segurança.",
                    modifier = Modifier.weight(1f),
                    accent = BrandTeal,
                    onClick = { showPairingSheet = true },
                )
                ActionTile(
                    icon = Icons.Default.Download,
                    title = "Bridge para PC",
                    subtitle = "Abra a página oficial, veja os requisitos e baixe com segurança.",
                    modifier = Modifier.weight(1f),
                    accent = BrandGold,
                    onClick = onOpenPcDownload,
                )
            }
        }
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = if (subscriptionActive) BrandGold else MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(50.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = if (subscriptionActive) BrandGold else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (subscriptionActive) "Interestellar Pro ativo" else "Leve a experiência para o nível Pro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (subscriptionActive) {
                                "Seu dispositivo já tem acesso aos recursos premium."
                            } else {
                                "Teste 7 dias grátis e desbloqueie uma experiência mais completa."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onOpenSubscription) {
                        Text(if (subscriptionActive) "Detalhes" else "Conhecer")
                    }
                }
            }
        }
        if (state.devices.isNotEmpty()) {
            item {
                SectionHeading(
                    title = "Seus computadores",
                    subtitle = "Toque em um dispositivo para abrir projetos e conversar com o agente.",
                )
            }
        }
        items(state.devices) { device ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeviceCardBackground),
                border = if (device.online) BorderStroke(1.dp, OnlineDeviceBorder) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectDevice(device.id); nav.navigate(Routes.PROJECTS) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.linearGradient(listOf(DeviceIconGradientStart, DeviceIconGradientEnd)),
                                RoundedCornerShape(16.dp),
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Computer, null, Modifier.size(26.dp), tint = BrandTeal)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (device.online) "Disponível para trabalhar agora" else "Sem conexão no momento",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.online) StatusActive else StatusIdle,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusCapsule(
                            if (device.online) "Conectado" else "Offline",
                            if (device.online) StatusActive else StatusIdle,
                        )
                        Icon(
                            Icons.Default.ArrowOutward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.devices.isEmpty()) item {
            FeatureRail(
                title = "Como começar",
                items = listOf(
                    "Abra a página oficial do Bridge no GitHub Pages e baixe o instalador para Windows.",
                    "Autorize as pastas e projetos no painel local.",
                    "Escaneie o QR Code para adicionar o computador à sua órbita.",
                ),
            )
        }
        item {
            if (state.devices.isEmpty()) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accent = NeonBlue,
                ) {
                    Text("Nenhum computador pareado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Abra a página oficial do Bridge no computador, confira os pré-requisitos e finalize o pareamento via QR Code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "Requisitos rápidos: Windows 10/11, internet, sessão do CLI já autenticada no computador e download apenas pela página oficial do projeto.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Aviso de segurança: o instalador atual não é assinado digitalmente. Valide o hash SHA-256 e use somente em computadores que você controla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenPcDownload) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir página oficial")
                    }
                }
            }
        }
        item {
            Button(
                onClick = { showPairingSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.devices.isEmpty()) "Parear primeiro computador" else "Parear novo computador")
            }
        }
    }

    if (showPairingSheet) {
        PairingBottomSheet(
            onDismiss = { showPairingSheet = false },
            onScan = onScan,
            onOpenPcDownload = onOpenPcDownload,
        )
    }
}
