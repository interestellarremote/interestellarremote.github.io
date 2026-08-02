package com.antigravity.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.remote.R
import com.antigravity.remote.ui.components.BrandWordmark
import com.antigravity.remote.ui.components.FeatureRail
import com.antigravity.remote.ui.components.HeroPanel
import com.antigravity.remote.ui.components.HeroTag
import com.antigravity.remote.ui.components.MetricPill
import com.antigravity.remote.ui.components.SpaceBackdrop
import com.antigravity.remote.ui.theme.*

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val context = LocalContext.current
    val clientId = context.getString(R.string.default_web_client_id)
    val configured = clientId != "firebase-not-configured"

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LoginGradientTop, LoginGradientMid, LoginGradientBottom))),
    ) {
        SpaceBackdrop(Modifier.fillMaxSize(), accentAlpha = 0.26f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            HeroTag("MOBILE COMMAND CENTER")
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(92.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.icon),
                            contentDescription = "Ícone Interestellar",
                            modifier = Modifier.size(72.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrandWordmark()
                    Text(
                        "Companion móvel independente para acompanhar tarefas, contexto e controles de execução do seu computador no celular.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricPill(value = "E2EE", label = "mensagens cifradas", accent = BrandTeal)
                MetricPill(value = "24/7", label = "acesso ao PC", accent = BrandGold)
            }
            Spacer(Modifier.height(18.dp))

            HeroPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroTag("AES-256-GCM")
                    HeroTag("E2EE")
                    HeroTag("TRIAL 7 DIAS", warm = true)
                }
                Spacer(Modifier.height(8.dp))
                Text("Controle remoto com cara de produto premium", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Conecte o celular ao seu computador, acompanhe tarefas ao vivo, aprove mudanças e coordene o agente com uma interface mais limpa e segura.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan)
                            Text("Chaves geradas localmente", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = BrandGold)
                            Text("Execução com feedback ao vivo", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FeatureRail(
                title = "Primeiros passos",
                items = listOf(
                    "Instale o Bridge no computador e autentique a conta usada pelo app.",
                    "Gere o QR Code no painel local para parear o celular com segurança.",
                    "Abra projetos, converse com o agente e acompanhe execuções em tempo real.",
                ),
            )

            if (!configured) {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "O projeto não está vinculado ao Firebase. Verifique google-services.json.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                enabled = configured,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Login, null)
                Spacer(Modifier.width(10.dp))
                Text("Entrar com Google", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "O login apenas autentica sua conta. O pareamento e o tráfego do agente continuam protegidos com criptografia ponta a ponta.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
