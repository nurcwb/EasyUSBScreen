package br.com.infonur.easyusbscreen

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import java.util.concurrent.TimeUnit

private class TuringPortConflict(ports: List<String>) :
    IllegalStateException("Porta USB ocupada: ${ports.joinToString()}. Feche o processo que está usando a porta e tente novamente.")

private data class TuringUsbOwner(
    val directory: Path,
    val label: String,
    val pid: Long,
    val started: Instant,
    val ports: List<String>,
)

internal class TuringMonitor(
    private val directory: Path = Path.of(System.getProperty("user.home"), "Programas", "turing-smart-screen-python"),
    private val python: Path = directory.resolve("venv/bin/python"),
    private val otherDirectories: Map<Path, String> = emptyMap(),
    private val bootService: TuringBootService? = null,
) {
    private val config = directory.resolve("config.yaml")
    private val main = directory.resolve("main.py")
    private val log = directory.resolve("log.log")
    private var blockedHandoffPorts = emptyList<String>()

    private fun handoffCandidate(): TuringUsbOwner? {
        val peers = otherDirectories.flatMap { (root, label) ->
            monitorProcesses(root).mapNotNull { process ->
                val ports = openUsbPorts(process.pid()).sorted()
                val started = process.info().startInstant().orElse(null)
                if (ports.isEmpty() || started == null) null
                else TuringUsbOwner(root, label, process.pid(), started, ports)
            }
        }
        return peers.singleOrNull()
    }

    fun stopManagedProcesses() { monitorProcesses().forEach(::stop) }

    fun hasUsbConnection(): Boolean = monitorProcesses().any { openUsbPorts(it.pid()).isNotEmpty() }

    fun activeTheme(): String? {
        if (monitorProcesses().isEmpty() || !Files.isRegularFile(log)) return null
        return Files.lines(log).use { lines ->
            var loading: String? = null
            var ready: String? = null
            lines.forEach { line ->
                loadedTheme.find(line)?.let {
                    loading = it.groupValues[1]
                    ready = null
                }
                if (line.contains(readyMessage)) ready = loading
            }
            ready
        }
    }

    fun apply(theme: String) {
        require(theme.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _-]*"))) { "Nome de tema inválido." }
        check(Files.isExecutable(python) && Files.isRegularFile(main)) {
            "Instalação do Turing não encontrada em $directory."
        }
        withTuringLock {
            installBundledThemeIfMissing(directory, theme)
            check(Files.isRegularFile(directory.resolve("res/themes").resolve(theme).resolve("theme.yaml"))) {
                "O tema $theme não está instalado no Turing."
            }
            applyLocked(theme)
        }
    }

    private fun applyLocked(theme: String) {
        val monitors = monitorProcesses()
        val useService = bootService?.shouldManage() == true
        val conflicts = conflictingUsbPorts(monitors)
        val handoff = if (conflicts.isEmpty()) {
            blockedHandoffPorts = emptyList()
            null
        } else {
            handoffCandidate()?.takeIf { it.ports == conflicts } ?: throw TuringPortConflict(conflicts)
        }

        val original = Files.readString(config)
        val section = turingConfigSection.find(original) ?: error("Seção config não encontrada no config.yaml.")
        val field = themeField.findAll(section.value).singleOrNull()
            ?: error("Campo config.THEME ausente ou duplicado.")
        val value = field.groups[1] ?: error("Campo config.THEME inválido.")
        val previousTheme = value.value.trim().removeSurrounding("\"").removeSurrounding("'")
        val start = section.range.first + value.range.first
        val themed = original.replaceRange(start, start + value.value.length, theme)
        val updated = themed
        val owner = handoff?.let {
            val confirmed = monitorProcesses(handoff.directory).singleOrNull {
                it.pid() == handoff.pid && it.info().startInstant().orElse(null) == handoff.started
            }
            check(confirmed != null && handoffCandidate() == handoff && conflicts == handoff.ports) {
                "A instância que usa a porta mudou durante a troca. Tente aplicar o tema novamente."
            }
            confirmed
        }
        val backup = Files.createTempFile(directory, "config.yaml.bak-easyusbscreen-", "")
        Files.copy(config, backup, REPLACE_EXISTING)
        if (owner != null) {
            try {
                stop(owner)
            } catch (failure: Exception) {
                throw IllegalStateException("Não foi possível encerrar ${handoff.label} e liberar ${handoff.ports.joinToString()}.", failure)
            }
        }
        var wroteConfig = false
        try {
            if (useService) bootService.stop()
            monitors.forEach(::stop)
            if (handoff != null) {
                blockedHandoffPorts = occupiedUsbPorts(handoff.ports)
                check(blockedHandoffPorts.isEmpty()) { "A porta ${blockedHandoffPorts.joinToString()} continua ocupada." }
            }
            check(monitorProcesses().isEmpty()) { "Uma instância do Turing reiniciou durante a troca." }
            check(Files.readString(config) == original) { "O config.yaml mudou durante a troca." }
            if (updated != original) {
                writeTuringConfig(config, updated)
                wroteConfig = true
            }
            start(theme, useService)
        } catch (failure: Exception) {
            try {
                if (wroteConfig) {
                    check(Files.readString(config) == updated) { "O config.yaml foi alterado externamente." }
                    writeTuringConfig(config, original)
                }
                if ((monitors.isNotEmpty() || useService) && monitorProcesses().isEmpty()) start(previousTheme, useService)
            } catch (rollbackFailure: Exception) {
                throw IllegalStateException(
                    "Falha ao aplicar $theme e restaurar o monitor. Backup: $backup. ${rollbackFailure.message}",
                    failure,
                )
            }
            throw failure
        }
    }

    private fun monitorProcesses(root: Path = directory): List<ProcessHandle> {
        return linuxMonitorProcesses(root)
    }

    private fun linuxMonitorProcesses(root: Path): List<ProcessHandle> = ProcessHandle.allProcesses().use { processes ->
        processes.filter { handle ->
            val info = handle.info()
            val executable = info.command().orElse("").substringAfterLast('/')
            val arguments = info.arguments().orElse(emptyArray()).dropWhile { it == "-E" || it == "-s" }
            if (!executable.startsWith("python") || arguments.size != 1) return@filter false
            val cwd = try {
                Files.readSymbolicLink(Path.of("/proc", handle.pid().toString(), "cwd"))
            } catch (_: IOException) {
                return@filter false
            }
            cwd == root && cwd.resolve(arguments.single()).normalize() == root.resolve("main.py")
        }.toList()
    }

    private fun conflictingUsbPorts(monitors: List<ProcessHandle>): List<String> {
        val duplicatePorts = monitors.flatMap { openUsbPorts(it.pid()) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val portsInOtherInstallation = otherDirectories.keys.flatMap { root ->
            monitorProcesses(root).flatMap { openUsbPorts(it.pid()) }
        }
        return (duplicatePorts + portsInOtherInstallation + occupiedUsbPorts(blockedHandoffPorts)).sorted()
    }

    private fun occupiedUsbPorts(ports: List<String>): List<String> {
        if (ports.isEmpty()) return emptyList()
        return ProcessHandle.allProcesses().use { processes ->
            processes.flatMap { openUsbPorts(it.pid()).stream() }
                .filter(ports::contains)
                .distinct()
                .sorted()
                .toList()
        }
    }

    private fun openUsbPorts(pid: Long): Set<String> = try {
        Files.newDirectoryStream(Path.of("/proc", pid.toString(), "fd")).use { descriptors ->
            descriptors.mapNotNull { descriptor ->
                try {
                    Files.readSymbolicLink(descriptor).toString().takeIf(usbSerialPort::matches)
                } catch (_: IOException) {
                    null
                }
            }.toSet()
        }
    } catch (_: IOException) {
        emptySet()
    }

    private fun stop(handle: ProcessHandle) {
        if (!handle.isAlive) return
        check(handle.destroy() || !handle.isAlive) { "Não foi possível parar o monitor do Turing." }
        handle.onExit().get(15, TimeUnit.SECONDS)
    }

    private fun start(theme: String, useService: Boolean) {
        check(monitorProcesses().isEmpty()) { "Outro monitor do Turing já está em execução." }
        val conflicts = conflictingUsbPorts(emptyList())
        if (conflicts.isNotEmpty()) throw TuringPortConflict(conflicts)
        val offset = if (Files.isRegularFile(log)) Files.size(log) else 0L
        val process = if (useService) {
            checkNotNull(bootService).start()
            null
        } else ProcessBuilder(
            listOf(python.toString(), "-E", "-s", main.toString()),
        )
            .directory(directory.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start().toHandle()

        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            var cursor = offset
            var themeLoaded = false
            var startupError: String? = null
            fun running(): Boolean = process?.isAlive ?: monitorProcesses().isNotEmpty()
            while (System.nanoTime() < deadline) {
                if (Files.isRegularFile(log)) {
                    RandomAccessFile(log.toFile(), "r").use { reader ->
                        if (reader.length() < cursor) cursor = 0
                        reader.seek(cursor)
                        while (true) {
                            val line = reader.readLine() ?: break
                            val end = reader.filePointer
                            reader.seek(end - 1)
                            if (reader.read() != '\n'.code) break
                            cursor = end
                            if (line.contains("Error starting webserver")) {
                                error("A porta local 5678 do simulador já está em uso.")
                            }
                            if (line.contains("Cannot open COM port") || line.contains("Cannot find COM port") || line.contains("[ERROR]")) {
                                startupError = line.substringAfter("] ")
                            }
                            if (loadedTheme.find(line)?.groupValues?.get(1) == theme) themeLoaded = true
                            if (themeLoaded && line.contains(readyMessage) && running()) return
                        }
                    }
                }
                if (!running()) break
                Thread.sleep(200)
            }
            error("O Turing não confirmou a inicialização de $theme. ${startupError ?: "Consulte log.log na pasta do Turing."}")
        } catch (failure: Exception) {
            try {
                if (process == null) bootService?.stop() else stop(process)
            } catch (stopFailure: Exception) {
                throw IllegalStateException("O monitor iniciado não pôde ser encerrado.", stopFailure)
            }
            throw failure
        }
    }

    private companion object {
        val usbSerialPort = Regex("/dev/tty(?:ACM|USB)[0-9]+")
        val themeField = Regex("(?m)^  THEME:[ \\t]*([^#\\r\\n]+?)(?=[ \\t]*(?:#|\\r?$))")
        val loadedTheme = Regex("\\[INFO] Loading theme (.+?) from ")
        const val readyMessage = "[INFO] Starting system monitoring"
    }
}

internal val turingConfigSection = Regex("(?m)^config:[^\\r\\n]*\\r?\\n(?:[ \\t#][^\\r\\n]*\\r?\\n|\\r?\\n)*")
internal fun <T> withTuringLock(action: () -> T): T {
    val lockDirectory = Path.of(System.getProperty("user.home"), ".config", "easyusbscreen")
    Files.createDirectories(lockDirectory)
    return FileChannel.open(lockDirectory.resolve("turing.lock"), CREATE, WRITE).use { channel ->
        val lock = channel.tryLock() ?: error("O Turing está ocupado. Tente novamente.")
        lock.use { action() }
    }
}

internal fun writeTuringConfig(config: Path, contents: String) {
    val temporary = Files.createTempFile(config.parent, ".config-", ".yaml")
    try {
        Files.copy(config, temporary, REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
        Files.writeString(temporary, contents)
        Files.move(temporary, config, ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
