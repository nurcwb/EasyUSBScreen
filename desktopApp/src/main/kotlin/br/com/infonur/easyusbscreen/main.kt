package br.com.infonur.easyusbscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application {
    val installer = remember { StandaloneInstaller() }
    val sensorSettings = remember(installer) { TuringSensorSettingsStore(installer.destination.resolve("turing/config.yaml")) }
    val networkSettings = remember(installer) { NetworkSettingsStore(installer.destination.resolve("turing/config.yaml")) }
    val bootService = remember { if (linuxDesktop) TuringBootService() else null }
    var standaloneInstalled by remember { mutableStateOf(installer.isInstalled()) }
    var setupOpen by remember { mutableStateOf(Files.notExists(installer.destination, NOFOLLOW_LINKS)) }
    var installProgress by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    val managedMonitor = remember(installer, bootService) {
        val independent = installer.destination
        val development = projectFolder("turing-standalone")?.resolve("turing")
        val current = Path.of(System.getProperty("user.home"), "Programas", "turing-smart-screen-python")
        TuringMonitor(
            independent.resolve("turing"),
            privatePython(independent),
            otherDirectories = if (linuxDesktop) buildMap {
                put(current, "Instalação anterior")
                development?.let { put(it, "Cópia de desenvolvimento") }
            } else emptyMap(),
            bootService = bootService,
        )
    }
    val monitor = managedMonitor.takeIf { standaloneInstalled }
    val scope = rememberCoroutineScope()
    var activeTheme by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    var hardwareSettingsOpen by remember { mutableStateOf(false) }
    var networkSettingsOpen by remember { mutableStateOf(false) }
    var usbConnected by remember(monitor) { mutableStateOf(false) }
    var installationReady by remember(monitor) { mutableStateOf(false) }

    var managementOpen by remember { mutableStateOf(false) }
    var bootEnabled by remember { mutableStateOf<Boolean?>(null) }
    var managementProgress by remember { mutableStateOf<String?>(null) }
    var managementError by remember { mutableStateOf<String?>(null) }

    fun manageTuring(message: String, action: suspend () -> Unit) {
        managementProgress = message
        managementError = null
        scope.launch {
            try {
                action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                managementError = failure.message ?: "Não foi possível concluir a operação."
            } finally {
                managementProgress = null
            }
        }
    }

    fun applyTheme(theme: String) {
        if (monitor != null && installationReady && !applying) {
            applying = true
            applyError = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { runCatching { monitor.apply(theme) } }
                    result.onSuccess { activeTheme = theme }
                    result.onFailure { failure ->
                        activeTheme = withContext(Dispatchers.IO) { runCatching { monitor.activeTheme() }.getOrNull() }
                        applyError = "Falha ao aplicar $theme. ${failure.message.orEmpty()}"
                    }
                } finally {
                    applying = false
                }
            }
        }
    }

    Window(
        onCloseRequest = { if (!applying && installProgress == null && managementProgress == null) exitApplication() },
        title = "Easy USB Screen",
        icon = appIconPainter(),
        state = rememberWindowState(width = 1040.dp, height = 740.dp),
    ) {
        LaunchedEffect(monitor) {
            val selectedMonitor = monitor
            if (selectedMonitor == null) {
                applyError = "Instale a cópia independente em Configurações → Turing para aplicar um tema."
                return@LaunchedEffect
            }
            activeTheme = withContext(Dispatchers.IO) { runCatching { selectedMonitor.activeTheme() }.getOrNull() }
            installationReady = true
            while (true) {
                usbConnected = withContext(Dispatchers.IO) {
                    runCatching { selectedMonitor.hasUsbConnection() }.getOrDefault(false)
                }
                delay(1_000)
            }
        }
        val catalog by produceState<Result<List<GalleryTheme>>?>(null) {
            value = withContext(Dispatchers.IO) { runCatching { loadThemeCatalog() } }
        }
        App(
            catalog, activeTheme, applying, applyError, installationReady, usbConnected,
            onOpenTuring = {
                if (Files.exists(installer.destination, NOFOLLOW_LINKS)) {
                    managementOpen = true
                    bootEnabled = null
                    managementError = null
                    if (!standaloneInstalled) {
                        managementError = "A instalação está incompleta. Desinstale esta cópia para instalar novamente."
                    } else if (bootService != null) {
                        manageTuring("Consultando a inicialização automática…") {
                            bootEnabled = withContext(Dispatchers.IO) { bootService.enabled() }
                        }
                    }
                } else {
                    setupOpen = true
                }
            },
            onOpenHardware = { if (standaloneInstalled) hardwareSettingsOpen = true else setupOpen = true },
            onOpenNetwork = if (standaloneInstalled && linuxDesktop) { { networkSettingsOpen = true } } else null,
            onApply = { theme -> applyTheme(theme) },
        )
        if (setupOpen) {
            TuringSetupDialog(
                destination = installer.destination.toString(),
                progress = installProgress,
                error = installError,
                onInstall = {
                    installProgress = "Preparando a instalação…"
                    installError = null
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { installer.install { step -> installProgress = step } }
                            }
                            result.onSuccess {
                                standaloneInstalled = true
                                applyError = null
                                setupOpen = false
                            }.onFailure { failure ->
                                installError = failure.message ?: "Não foi possível concluir a instalação."
                            }
                        } finally {
                            installProgress = null
                        }
                    }
                },
                onDismiss = { setupOpen = false },
            )
        }
        if (managementOpen) {
            TuringManagementDialog(
                bootEnabled = bootEnabled,
                supportsBootService = bootService != null,
                progress = managementProgress,
                error = managementError,
                onToggleBoot = { enabled ->
                    manageTuring("Atualizando a inicialização automática…") {
                        bootEnabled = withContext(Dispatchers.IO) {
                            checkNotNull(bootService).setEnabled(enabled)
                            bootService.enabled()
                        }
                    }
                },
                onUninstall = {
                    manageTuring("Encerrando e removendo o Turing…") {
                        try {
                            withContext(Dispatchers.IO) {
                                withTuringLock {
                                    installer.uninstall {
                                        bootService?.remove()
                                        managedMonitor.stopManagedProcesses()
                                    }
                                }
                            }
                        } finally {
                            standaloneInstalled = withContext(Dispatchers.IO) { installer.isInstalled() }
                            if (!standaloneInstalled) activeTheme = null
                        }
                        managementOpen = false
                    }
                },
                onDismiss = { managementOpen = false },
            )
        }
        if (hardwareSettingsOpen) {
            HardwareSettingsDialog(
                load = { withContext(Dispatchers.IO) { sensorSettings.load() } },
                save = { mode -> withContext(Dispatchers.IO) { sensorSettings.save(mode) } },
                onDismiss = { hardwareSettingsOpen = false },
            )
        }
        if (networkSettingsOpen) {
            NetworkSettingsDialog(
                load = { withContext(Dispatchers.IO) { networkSettings.load() } },
                save = { name -> withContext(Dispatchers.IO) { networkSettings.save(name) } },
                onDismiss = { networkSettingsOpen = false },
            )
        }
    }
}
