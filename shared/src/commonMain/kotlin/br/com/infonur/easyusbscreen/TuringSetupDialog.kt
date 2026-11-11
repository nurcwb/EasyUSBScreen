package br.com.infonur.easyusbscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TuringSetupDialog(
    destination: String,
    progress: String?,
    error: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Configurar o Turing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Instale o Turing com Python Standalone e os temas do EasyUSBScreen.")
                Text(destination, style = MaterialTheme.typography.bodySmall)
                if (progress != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = onInstall) {
                Text(if (error == null) "Baixar e instalar" else "Tentar novamente")
            }
        },
        dismissButton = {
            TextButton(enabled = progress == null, onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
