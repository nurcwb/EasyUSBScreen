package br.com.infonur.easyusbscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import demo.shared.generated.resources.Res
import demo.shared.generated.resources.hardware
import demo.shared.generated.resources.network_wired
import demo.shared.generated.resources.settings
import demo.shared.generated.resources.theme_applied
import demo.shared.generated.resources.theme_error
import demo.shared.generated.resources.theme_selected
import demo.shared.generated.resources.turing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class GalleryTheme(val name: String, val preview: ImageBitmap?)

private val ink = Color(0xFFEAF4FC)
private val muted = Color(0xFF9FB3C7)
private val cyan = Color(0xFF32ECFA)
private val green = Color(0xFF61EFB6)
private val canvas = Color(0xFF080F1B)
private val surface = Color(0xFF0D1828)
private val outline = Color(0xFF304558)
private val errorInk = Color(0xFFFFB4AB)
private val errorBackground = Color(0xFF421F28)
private val panelShape = RoundedCornerShape(16.dp)

@Composable
fun App(
    catalog: Result<List<GalleryTheme>>?,
    activeTheme: String?,
    applying: Boolean,
    applyError: String?,
    installationReady: Boolean,
    usbConnected: Boolean,
    onOpenTuring: () -> Unit,
    onOpenHardware: () -> Unit,
    onOpenNetwork: (() -> Unit)?,
    onApply: (String) -> Unit,
) {
    val themes = catalog?.getOrNull().orEmpty()
    val active = themes.firstOrNull { it.name == activeTheme }
    var selectedName by remember(themes) { mutableStateOf<String?>(null) }
    val selected = themes.firstOrNull { it.name == selectedName } ?: active ?: themes.firstOrNull()
    var settingsOpen by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = cyan,
            onPrimary = canvas,
            background = canvas,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            onSurfaceVariant = muted,
            outline = outline,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(canvas)) {
            val sideBySide = maxWidth >= 850.dp
            val outerPadding = if (maxHeight < 600.dp) 12.dp else 28.dp
            Column(
                Modifier.fillMaxSize().padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(appIconPainter(), null, Modifier.size(36.dp))
                            Text("Easy USB Screen", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Box {
                                TextButton(onClick = {
                                    settingsOpen = true
                                }) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(painterResource(Res.drawable.settings), contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Configurações")
                                    }
                                }
                                DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Hardware") },
                                        leadingIcon = {
                                            Icon(painterResource(Res.drawable.hardware), contentDescription = null)
                                        },
                                        onClick = {
                                            settingsOpen = false
                                            onOpenHardware()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Turing") },
                                        leadingIcon = {
                                            Icon(painterResource(Res.drawable.turing), contentDescription = null)
                                        },
                                        enabled = !applying,
                                        onClick = {
                                            settingsOpen = false
                                            onOpenTuring()
                                        },
                                    )
                                    if (onOpenNetwork != null) {
                                        DropdownMenuItem(
                                            text = { Text("Rede") },
                                            leadingIcon = {
                                                Icon(painterResource(Res.drawable.network_wired), contentDescription = null)
                                            },
                                            enabled = !applying,
                                            onClick = {
                                                settingsOpen = false
                                                onOpenNetwork()
                                            },
                                        )
                                    }
                                }
                            }
                            ConnectionStatus(usbConnected)
                        }
                    }
                }
                val current = selected
                when {
                    catalog == null -> Text("Carregando temas…", color = muted)
                    catalog.isFailure -> Status(
                        "Não foi possível ler a pasta de temas.",
                        Res.drawable.theme_error,
                        errorInk,
                        errorBackground,
                    )
                    current == null -> Text("Nenhum tema encontrado.", color = muted)
                    sideBySide -> Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        ThemeList(
                            themes, active, current, { selectedName = it.name },
                            Modifier.width(310.dp).fillMaxHeight(),
                        )
                        PreviewPanel(
                            activeTheme, current, applying, installationReady, applyError, { onApply(current.name) },
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    else -> {
                        ThemeList(themes, active, current, { selectedName = it.name }, Modifier.fillMaxWidth().weight(0.42f))
                        PreviewPanel(
                            activeTheme, current, applying, installationReady, applyError, { onApply(current.name) },
                            Modifier.fillMaxWidth().weight(0.58f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeList(
    themes: List<GalleryTheme>,
    active: GalleryTheme?,
    selected: GalleryTheme,
    onSelect: (GalleryTheme) -> Unit,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier.panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Temas", style = MaterialTheme.typography.headlineSmall, color = ink, fontWeight = FontWeight.Bold)
        Text("Escolha um tema para visualizar", color = muted, fontSize = 13.sp)
        Column(
            Modifier.weight(1f, fill = false)
                .fillMaxWidth()
                .scrollPositionIndicator(scrollState)
                .padding(end = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (theme in themes) {
                val shape = RoundedCornerShape(10.dp)
                val isSelected = theme == selected
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (isSelected) Color(0xFF133042) else Color(0xFF111F32))
                        .then(if (isSelected) Modifier.border(1.dp, cyan, shape) else Modifier)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeImage(theme, Modifier.size(72.dp, 48.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(theme.name, color = ink, fontWeight = FontWeight.Bold)
                        when {
                            theme == active -> Status(
                                "Em uso", Res.drawable.theme_applied, green, Color(0xFF112A2B),
                            )
                            isSelected -> Status(
                                "Selecionado", Res.drawable.theme_selected, cyan, Color(0xFF133042),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.scrollPositionIndicator(state: ScrollState) = drawWithContent {
    drawContent()
    val maxScroll = state.maxValue
    val viewport = state.viewportSize.toFloat()
    if (maxScroll <= 0 || maxScroll == Int.MAX_VALUE || viewport <= 0f) return@drawWithContent

    val width = 5.dp.toPx()
    val radius = CornerRadius(width / 2)
    val x = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
    val thumbHeight = (viewport * (viewport / (viewport + maxScroll)))
        .coerceIn(24.dp.toPx().coerceAtMost(viewport), viewport)
    val thumbTop = (viewport - thumbHeight) * (state.value.toFloat() / maxScroll).coerceIn(0f, 1f)

    drawRoundRect(Color(0xFF17283B), Offset(x, 0f), Size(width, viewport), radius)
    drawRoundRect(Color(0xFF59738D), Offset(x, thumbTop), Size(width, thumbHeight), radius)
}

@Composable
private fun PreviewPanel(
    activeTheme: String?,
    selected: GalleryTheme,
    applying: Boolean,
    installationReady: Boolean,
    applyError: String?,
    onApply: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prévia da tela", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
            Text(selected.name, color = muted, fontSize = 14.sp)
        }
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 504.dp).fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val frame = RoundedCornerShape(12.dp)
            Box(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 344.dp)
                    .clip(frame).background(canvas).border(1.dp, outline, frame).padding(12.dp),
            ) {
                ThemeImage(selected, Modifier.fillMaxSize(), showPlaceholderLabel = true)
            }
            Button(
                onClick = onApply,
                enabled = installationReady && !applying,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(when {
                    applying -> "Aplicando…"
                    selected.name == activeTheme -> "Reaplicar tema"
                    else -> "Aplicar tema"
                }, fontWeight = FontWeight.Bold)
            }
            if (applying) {
                Text("Aguardando a inicialização do Turing…", color = muted, fontSize = 13.sp)
            }
            if (applyError != null) Status(applyError, Res.drawable.theme_error, errorInk, errorBackground)
        }
    }
}

@Composable
private fun ThemeImage(theme: GalleryTheme, modifier: Modifier, showPlaceholderLabel: Boolean = false) {
    if (theme.preview != null) {
        Image(
            theme.preview,
            contentDescription = if (showPlaceholderLabel) "Prévia de ${theme.name}" else null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.background(canvas), contentAlignment = Alignment.Center) {
            if (showPlaceholderLabel) Text("Prévia indisponível", color = muted)
        }
    }
}

@Composable
private fun ConnectionStatus(usbConnected: Boolean) {
    val color = if (usbConnected) green else muted
    Row(
        Modifier.background(if (usbConnected) Color(0xFF112A2B) else canvas, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(if (usbConnected) "Tela conectada" else "USB sem conexão confirmada", color = color, fontSize = 12.sp)
    }
}

@Composable
private fun Status(label: String, icon: DrawableResource, foreground: Color, background: Color) {
    Row(
        Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = foreground)
        Text(label, color = foreground, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Modifier.panel() = background(surface, panelShape)
    .border(1.dp, outline, panelShape)
    .padding(18.dp)
