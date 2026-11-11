package br.com.infonur.easyusbscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TuringManagementDialog(
    bootEnabled: Boolean?,
    supportsBootService: Boolean,
    progress: String?,
    error: String?,
    onToggleBoot: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val busy = progress != null
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (confirming) "Desinstalar Turing?" else "Turing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (confirming) {
                    val service = if (supportsBootService) " O serviço de inicialização será removido." else ""
                    Text("O monitor será encerrado. O Python Standalone, os temas e as configurações desta cópia serão removidos.$service")
                } else if (supportsBootService) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Iniciar o Turing com o computador", Modifier.weight(1f))
                        Switch(
                            checked = bootEnabled == true,
                            enabled = bootEnabled != null && !busy,
                            onCheckedChange = onToggleBoot,
                        )
                    }
                    Text("O Turing inicia antes do login. O sistema pode pedir autorização de administrador para ativar o serviço.")
                } else {
                    Text("Gerencie a cópia independente do Turing e seu Python Standalone.")
                }
                if (progress != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { if (confirming) onUninstall() else onDismiss() },
            ) { Text(if (confirming) "Confirmar desinstalação" else "Fechar") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { confirming = !confirming }) {
                Text(if (confirming) "Voltar" else "Desinstalar")
            }
        },
    )
}
