package io.interestellar.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.interestellar.remote.DownloadLinks
import io.interestellar.remote.R
import io.interestellar.remote.ui.theme.BackdropNebula
import io.interestellar.remote.ui.theme.BrandGold
import io.interestellar.remote.ui.theme.BrandPurple
import io.interestellar.remote.ui.theme.BrandTeal
import io.interestellar.remote.ui.theme.ChatAgentIconBackground
import io.interestellar.remote.ui.theme.CodeBlockBackground
import io.interestellar.remote.ui.theme.CodeBlockText
import io.interestellar.remote.ui.theme.FeatureRailBackground
import io.interestellar.remote.ui.theme.GoldChipBackground
import io.interestellar.remote.ui.theme.GoldChipBorder
import io.interestellar.remote.ui.theme.HeroPanelBackground
import io.interestellar.remote.ui.theme.HeroPanelBorder
import io.interestellar.remote.ui.theme.NeonBlue
import io.interestellar.remote.ui.theme.NeonCyan
import io.interestellar.remote.ui.theme.NeonGreen
import io.interestellar.remote.ui.theme.SpacePanel
import io.interestellar.remote.ui.theme.SpaceSurface
import io.interestellar.remote.ui.theme.StatusActive
import io.interestellar.remote.ui.theme.StatusError
import io.interestellar.remote.ui.theme.StatusModified
import io.interestellar.remote.ui.theme.SubtleBorder
import io.interestellar.remote.ui.theme.TealChipBackground
import io.interestellar.remote.ui.theme.TealChipBorder
import io.interestellar.remote.ui.theme.TealChipText
import io.interestellar.remote.ui.theme.TealStepBackground

@Composable
fun InterestellarLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.icon),
        contentDescription = "Ícone Interestellar",
        modifier = modifier,
    )
}

@Composable
fun BrandWordmark(compact: Boolean = false, centered: Boolean = false) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp),
    ) {
        Text(
            text = "INTERESTELLAR REMOTE",
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = BrandTeal,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = "companion independente",
            style = if (compact) {
                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            },
            color = Color.White,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier, accentAlpha: Float = 0.22f) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.035f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f),
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BackdropNebula.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(w * 0.16f, h * 0.2f),
                radius = size.minDimension * 0.5f,
            ),
            radius = size.minDimension * 0.5f,
            center = Offset(w * 0.16f, h * 0.2f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BrandPurple.copy(alpha = accentAlpha), Color.Transparent),
                center = Offset(w * 0.86f, h * 0.24f),
                radius = size.minDimension * 0.55f,
            ),
            radius = size.minDimension * 0.55f,
            center = Offset(w * 0.86f, h * 0.24f),
        )
        drawOval(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.07f), BrandTeal.copy(alpha = 0.08f), Color.Transparent),
            ),
            topLeft = Offset(w * 0.46f, h * 0.7f),
            size = Size(w * 0.42f, h * 0.12f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(22f)),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.055f),
            topLeft = Offset(w * 0.54f, h * 0.08f),
            size = Size(w * 0.22f, h * 0.08f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
        listOf(
            Offset(w * 0.09f, h * 0.13f),
            Offset(w * 0.25f, h * 0.6f),
            Offset(w * 0.48f, h * 0.18f),
            Offset(w * 0.67f, h * 0.75f),
            Offset(w * 0.82f, h * 0.14f),
            Offset(w * 0.9f, h * 0.48f),
        ).forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 3 == 0) BrandGold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.82f),
                radius = if (index % 2 == 0) 2.2.dp.toPx() else 1.6.dp.toPx(),
                center = point,
            )
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(28.dp),
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HeroPanelBackground),
        shape = shape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Box(Modifier.fillMaxWidth()) {
            SpaceBackdrop(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
                accentAlpha = 0.18f,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.045f), Color.Transparent, Color.Black.copy(alpha = 0.08f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
fun HeroPanel(content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = HeroPanelBorder,
        shape = RoundedCornerShape(30.dp),
        padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        content = content,
    )
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            eyebrow?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = BrandTeal)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = action)
    }
}

@Composable
fun MetricPill(
    value: String,
    label: String,
    accent: Color = NeonCyan,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpaceSurface.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FeatureRail(title: String, items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FeatureRailBackground),
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(50), color = TealStepBackground) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandTeal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(item, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HeroTag(text: String, accent: Color = BrandTeal, warm: Boolean = false) {
    val background = if (warm) GoldChipBackground else TealChipBackground
    val border = if (warm) GoldChipBorder else TealChipBorder
    val foreground = if (warm) BrandGold else TealChipText
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (warm) foreground else accent,
        )
    }
}

@Composable
fun StatusCapsule(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

@Composable
fun InstructionStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(50), color = BrandPurple.copy(alpha = 0.22f)) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CodeBlock(code: String) {
    Surface(
        color = CodeBlockBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonBlue.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
    ) {
        Text(
            code,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = CodeBlockText,
        )
    }
}

@Composable
fun FormattedMessage(text: String) {
    val lines = text.replace("\r\n", "\n").lines()
    var codeBlock = false
    val codeLines = mutableListOf<String>()

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { sourceLine ->
            val line = sourceLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (codeBlock) {
                    CodeBlock(codeLines.joinToString("\n"))
                    codeLines.clear()
                }
                codeBlock = !codeBlock
            } else if (codeBlock) {
                codeLines += sourceLine
            } else if (line.isBlank()) {
                Spacer(Modifier.height(3.dp))
            } else {
                val trimmed = line.trimStart()
                val headingLevel = trimmed.takeWhile { it == '#' }.length.takeIf { it in 1..3 }
                when {
                    headingLevel != null && trimmed.getOrNull(headingLevel) == ' ' -> Text(
                        inlineMarkdown(trimmed.drop(headingLevel + 1)),
                        style = when (headingLevel) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row {
                        Text("•  ", color = MaterialTheme.colorScheme.primary)
                        Text(
                            inlineMarkdown(trimmed.drop(2)),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                        val marker = trimmed.substringBefore(' ') + " "
                        Row {
                            Text(marker, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(
                                inlineMarkdown(trimmed.substringAfter(' ')),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    trimmed.startsWith("> ") -> Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            inlineMarkdown(trimmed.drop(2)),
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    else -> Text(inlineMarkdown(line), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (codeLines.isNotEmpty()) CodeBlock(codeLines.joinToString("\n"))
    }
}

@Composable
fun inlineMarkdown(value: String) = buildAnnotatedString {
    val token = Regex("(\\*\\*.+?\\*\\*|__.+?__|`[^`]+`)")
    var position = 0
    token.findAll(value).forEach { match ->
        append(value.substring(position, match.range.first))
        val raw = match.value
        when {
            raw.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.secondary,
                ),
            ) { append(raw.drop(1).dropLast(1)) }
            else -> withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                append(raw.drop(2).dropLast(2))
            }
        }
        position = match.range.last + 1
    }
    append(value.substring(position))
}

@Composable
fun ListPage(
    title: String,
    subtitle: String? = null,
    topAction: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = topAction)
        }
        LazyColumn(content = content)
    }
}

@Composable
fun GitStatusText(status: String) {
    if (status.isBlank()) {
        Text("Repositório limpo ou Git não configurado.", fontFamily = FontFamily.Monospace)
        return
    }
    Text(
        buildAnnotatedString {
            status.lineSequence().forEachIndexed { index, rawLine ->
                val code = rawLine.take(2)
                val path = rawLine.drop(3).ifBlank { rawLine }.let(::middleEllipsisPath)
                val color = when {
                    code == "??" -> StatusActive
                    'D' in code -> StatusError
                    else -> StatusModified
                }
                if (index > 0) append('\n')
                withStyle(SpanStyle(color = color)) {
                    append(if (rawLine.length > 2) "$code $path" else rawLine)
                }
            }
        },
        fontFamily = FontFamily.Monospace,
    )
}

private fun middleEllipsisPath(path: String, maxLength: Int = 48): String {
    if (path.length <= maxLength) return path
    val separator = if ('\\' in path) '\\' else '/'
    val parts = path.split('/', '\\').filter(String::isNotBlank)
    if (parts.size < 3) return path.take(20) + "..." + path.takeLast(22)
    return listOf(parts.first(), "...", *parts.takeLast(2).toTypedArray()).joinToString(separator.toString())
}

fun Modifier.cardClick(action: () -> Unit) = this
    .padding(horizontal = 10.dp, vertical = 4.dp)
    .clip(RoundedCornerShape(22.dp))
    .clickable(onClick = action)

private const val INSTALLER_DOWNLOAD_URL = DownloadLinks.OFFICIAL_SITE_URL

@Composable
fun InitialHelpMenu() {
    var expanded by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val installerAvailable = INSTALLER_DOWNLOAD_URL.isNotBlank()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu inicial")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Como começar") },
                leadingIcon = { Icon(Icons.Default.RocketLaunch, contentDescription = null) },
                onClick = { expanded = false; showInstructions = true },
            )
            DropdownMenuItem(
                text = { Text(if (installerAvailable) "Baixar instalador" else "Instalador — em breve") },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                enabled = installerAvailable,
                onClick = {
                    expanded = false
                    if (installerAvailable) uriHandler.openUri(INSTALLER_DOWNLOAD_URL)
                },
            )
        }
    }

    if (showInstructions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showInstructions = false },
            icon = { InterestellarLogo(Modifier.size(54.dp)) },
            title = { Text("Prepare sua conexão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InstructionStep("1", "Abra a página oficial do Bridge no GitHub Pages.")
                    InstructionStep("2", "Baixe o instalador para Windows 10 ou 11. Não precisa de acesso de administrador.")
                    InstructionStep("3", "Faça login no CLI já configurado no computador, abra o Bridge e autorize apenas as pastas que você confia.")
                    InstructionStep("4", "Gere o QR Code no computador e toque em Escanear QR para concluir o pareamento.")
                    FilledTonalButton(
                        onClick = { if (installerAvailable) uriHandler.openUri(INSTALLER_DOWNLOAD_URL) },
                        enabled = installerAvailable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text(if (installerAvailable) " Abrir página oficial" else " Download em breve")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showInstructions = false }) {
                    Text("Entendi")
                }
            },
        )
    }
}

