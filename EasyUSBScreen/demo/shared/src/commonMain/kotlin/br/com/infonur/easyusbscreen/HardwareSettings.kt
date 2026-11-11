package br.com.infonur.easyusbscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HardwareSettingsDialog(
    load: suspend () -> String,
    save: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("PYTHON") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            selected = load()
            loaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Não foi possível ler a configuração dos sensores."
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Sensores do Turing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolha o backend que o próprio Turing usa para ler os sensores.")
                Column(Modifier.selectableGroup()) {
                    for ((mode, label) in listOf("AUTO" to "Automático do Turing", "PYTHON" to "Bibliotecas Python")) {
                        Row(
                            Modifier.fillMaxWidth().selectable(selected == mode, enabled = loaded && !saving, role = Role.RadioButton) { selected = mode },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected == mode, onClick = null, enabled = loaded && !saving)
                            Text(label)
                        }
                    }
                }
                Text("Reaplique o tema para usar a configuração selecionada.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = loaded && !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        save(selected)
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "Não foi possível salvar a configuração dos sensores."
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}
