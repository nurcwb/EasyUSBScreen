package br.com.infonur.easyusbscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import demo.shared.generated.resources.Res
import demo.shared.generated.resources.network_other
import demo.shared.generated.resources.network_wifi
import demo.shared.generated.resources.network_wired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

enum class NetworkKind { ETHERNET, WIFI, OTHER }
data class NetworkDevice(val name: String, val kind: NetworkKind)
data class NetworkSettings(val selected: String?, val interfaces: List<NetworkDevice>)

@Composable
fun NetworkSettingsDialog(
    load: suspend () -> NetworkSettings,
    save: suspend (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<NetworkSettings?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            settings = load().also { selected = it.selected }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Não foi possível carregar as interfaces."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Rede") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolha qual interface a cópia independente monitora para TX e RX.")
                settings?.let { loaded ->
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                        NetworkOption("Nenhuma", "Sem interface configurada no Turing", null, selected == null, !saving) { selected = null }
                        for (device in loaded.interfaces) {
                            val description = when (device.kind) {
                                NetworkKind.ETHERNET -> "Ethernet"
                                NetworkKind.WIFI -> "Wi-Fi"
                                NetworkKind.OTHER -> "Outra interface"
                            }
                            NetworkOption(device.name, description, device.kind, selected == device.name, !saving) {
                                selected = device.name
                            }
                        }
                    }
                    if (loaded.selected != null && loaded.interfaces.none { it.name == loaded.selected }) {
                        Text("A interface salva (${loaded.selected}) está indisponível. Escolha outra ou deixe sem interface.")
                    }
                }
                Text("Reaplique o tema para usar a nova escolha.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = settings != null && settings?.selected != selected && !saving,
                onClick = {
                    saving = true
                    error = null
                    val choice = selected
                    scope.launch {
                        try {
                            save(choice)
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Não foi possível salvar a interface."
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
private fun NetworkOption(
    name: String,
    description: String,
    kind: NetworkKind?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        val icon = when (kind) {
            NetworkKind.ETHERNET -> Res.drawable.network_wired
            NetworkKind.WIFI -> Res.drawable.network_wifi
            else -> Res.drawable.network_other
        }
        Icon(painterResource(icon), contentDescription = null)
        Column {
            Text(name)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
