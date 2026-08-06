package io.interestellar.remote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.interestellar.remote.ui.components.CodeBlock
import io.interestellar.remote.ui.components.FeatureRail
import io.interestellar.remote.ui.components.HeroTag
import io.interestellar.remote.ui.components.InstructionStep
import io.interestellar.remote.ui.theme.*

/**
 * Modal bottom-sheet wizard for pairing a new computer.
 *
 * Step 0 – "Como funciona": explains E2EE encryption model.
 * Step 1 – "Primeira conexão": setup instructions + QR scan CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onOpenPcDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 2

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NeonCyan.copy(alpha = 0.5f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Step indicator ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(totalSteps) { index ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (index <= currentStep) BrandTeal else BrandTeal.copy(alpha = 0.2f),
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        content = {},
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Animated step content ───────────────────────
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * direction } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it * direction } + fadeOut())
                },
                label = "pairing_step",
            ) { step ->
                when (step) {
                    0 -> StepHowItWorks(onNext = { currentStep = 1 })
                    1 -> StepFirstConnection(
                        onBack = { currentStep = 0 },
                        onScan = { onDismiss(); onScan() },
                        onOpenPcDownload = onOpenPcDownload,
                    )
                }
            }
        }
    }
}

// ── Step 0: Como funciona ─────────────────────────────────────
@Composable
private fun StepHowItWorks(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Shield, null, tint = BrandTeal, modifier = Modifier.size(28.dp))
            Text(
                "Como funciona?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "A conexão entre o celular e o computador usa criptografia de ponta a ponta. Ninguém — nem mesmo o Firebase — tem acesso às suas mensagens.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FeatureRail(
            title = "Segurança",
            items = listOf(
                "As mensagens são cifradas de ponta a ponta. O Firebase não consegue lê-las.",
                "O par de chaves é gerado localmente pelo QR Code no portal do computador.",
                "Acesse shell, git, arquivos e aprove comandos remotamente.",
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroTag("AES-256-GCM")
            HeroTag("E2EE")
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Próximo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null)
        }
    }
}

// ── Step 1: Primeira conexão ──────────────────────────────────
@Composable
private fun StepFirstConnection(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenPcDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.QrCodeScanner, null, tint = BrandTeal, modifier = Modifier.size(28.dp))
            Text(
                "Primeira conexão",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "Siga os passos abaixo para parear seu computador com este celular.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        InstructionStep("1", "Abra a página oficial do Bridge no computador.")
        CodeBlock("https://interestellarremote.github.io/")
        InstructionStep("2", "Baixe o instalador para Windows 10/11 e conclua a instalação local.")
        InstructionStep("3", "Faça login no CLI já configurado no computador, abra o Interestellar Bridge e autorize apenas as pastas que você confia.")
        InstructionStep("4", "Gere o QR Code no painel do Bridge e escaneie com este celular.")
        Text(
            "O instalador atual não é assinado digitalmente. Antes de executar, confira o hash SHA-256 publicado na página oficial.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onOpenPcDownload,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("Abrir página oficial")
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Voltar")
            }
            Button(
                onClick = onScan,
                modifier = Modifier.weight(2f).height(52.dp),
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Escanear QR Code", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

