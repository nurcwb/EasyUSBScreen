package br.com.infonur.easyusbscreen

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.concurrent.TimeUnit

internal class TuringBootService(
    private val home: Path = Path.of(System.getProperty("user.home")),
) {
    private val unitFile = home.resolve(".config/systemd/user/$unitName")

    fun enabled(): Boolean = exists() && checkedState("UnitFileState") == "enabled" &&
        run("loginctl", "show-user", System.getProperty("user.name"), "-p", "Linger", "--value") == "yes"

    fun shouldManage(): Boolean = exists() && (
        checkedState("UnitFileState") == "enabled" ||
            checkedState("ActiveState") in setOf("active", "activating", "reloading", "deactivating")
        )

    fun setEnabled(enabled: Boolean) = withTuringLock {
        if (enabled) {
            check(!legacyAutostartEnabled()) {
                "Desative a entrada antiga do Turing em Configurações do Sistema → Inicialização automática e tente novamente."
            }
            val alreadyInstalled = exists()
            if (run("loginctl", "show-user", System.getProperty("user.name"), "-p", "Linger", "--value") != "yes") {
                run("loginctl", "enable-linger")
            }
            if (!alreadyInstalled) {
                Files.createDirectories(unitFile.parent)
                Files.writeString(unitFile, unitContents, CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
            }
            run("systemctl", "--user", "daemon-reload")
            run("systemctl", "--user", "enable", unitName)
        } else if (exists()) {
            run("systemctl", "--user", "disable", unitName)
        }
    }

    fun start() { run("systemctl", "--user", "start", unitName) }
    fun stop() { run("systemctl", "--user", "stop", unitName) }

    fun remove() {
        if (!exists()) return
        stop()
        run("systemctl", "--user", "disable", unitName)
        Files.delete(unitFile)
        run("systemctl", "--user", "daemon-reload")
    }

    private fun exists(): Boolean {
        if (Files.notExists(unitFile, NOFOLLOW_LINKS)) return false
        check(Files.isRegularFile(unitFile, NOFOLLOW_LINKS) && Files.readString(unitFile) == unitContents) {
            "Já existe um serviço $unitName diferente do serviço do EasyUSBScreen."
        }
        return true
    }

    private fun checkedState(property: String): String =
        run("systemctl", "--user", "show", unitName, "-p", property, "--value")

    private fun legacyAutostartEnabled(): Boolean {
        val directory = home.resolve(".config/autostart")
        if (!Files.isDirectory(directory)) return false
        return Files.list(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".desktop") && Files.isRegularFile(it) }.anyMatch { entry ->
                val lines = Files.readAllLines(entry)
                lines.any { it.startsWith("Exec=") && "turing-smart-screen-python" in it } &&
                    lines.none { it.trim().equals("Hidden=true", true) || it.trim().equals("X-KDE-autostart-enabled=false", true) }
            }
        }
    }

    private fun run(vararg command: String): String {
        val output = Files.createTempFile("easyusbscreen-systemd-", ".log")
        try {
            val process = ProcessBuilder(command.toList()).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start()
            try {
                check(process.waitFor(90, TimeUnit.SECONDS)) { "Tempo limite ao executar ${command.joinToString(" ")}." }
                val message = Files.readString(output).trim()
                check(process.exitValue() == 0) {
                    "Não foi possível executar ${command.joinToString(" ")}. $message"
                }
                return message
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private companion object {
        const val unitName = "easyusbscreen-turing.service"
        val unitContents = """
            # Gerenciado pelo EasyUSBScreen
            [Unit]
            Description=EasyUSBScreen Turing Monitor

            [Service]
            Type=simple
            WorkingDirectory=%h/.local/share/easyusbscreen/turing-standalone/turing
            Environment=PYSTRAY_BACKEND=dummy
            ExecStart="%h/.local/share/easyusbscreen/turing-standalone/python/bin/python3" -E -s "%h/.local/share/easyusbscreen/turing-standalone/turing/main.py"
            Restart=always
            RestartSec=10s
            TimeoutStopSec=15s

            [Install]
            WantedBy=default.target
        """.trimIndent() + "\n"
    }
}
