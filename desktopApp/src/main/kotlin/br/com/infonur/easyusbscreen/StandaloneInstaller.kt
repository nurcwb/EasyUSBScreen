package br.com.infonur.easyusbscreen

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

internal class StandaloneInstaller(
    val destination: Path = standaloneDirectory(),
) {
    fun isInstalled(): Boolean = Files.isExecutable(privatePython(destination)) &&
        Files.isRegularFile(destination.resolve("turing/main.py")) &&
        Files.isRegularFile(destination.resolve("turing/config.yaml"))

    fun install(progress: (String) -> Unit) {
        if (isInstalled()) return
        check(linuxDesktop && System.getProperty("os.arch") in setOf("amd64", "x86_64")) {
            "O instalador está disponível para Linux x86-64."
        }

        withInstallLock {
            if (!isInstalled()) {
                check(Files.notExists(destination, NOFOLLOW_LINKS)) { "A pasta de destino já existe e contém uma instalação incompleta." }
                installLocked(progress)
            }
        }
    }

    fun uninstall(stopMonitor: () -> Unit) = withInstallLock {
        check(Files.notExists(destination, NOFOLLOW_LINKS) || Files.isDirectory(destination, NOFOLLOW_LINKS)) {
            "A pasta do Turing tem um formato inesperado."
        }
        stopMonitor()
        if (Files.isDirectory(destination, NOFOLLOW_LINKS)) {
            removeStandaloneTree(destination)
        }
    }

    private fun withInstallLock(action: () -> Unit) {
        Files.createDirectories(destination.parent)
        FileChannel.open(destination.parent.resolve(".install.lock"), CREATE, WRITE).use { channel ->
            val lock = channel.tryLock() ?: error("Já existe uma instalação ou desinstalação em andamento.")
            lock.use { action() }
        }
    }

    private fun installLocked(progress: (String) -> Unit) {
        val stage = Files.createTempDirectory(destination.parent, ".turing-install-")
        try {
            progress("Baixando o Python Standalone…")
            val archive = stage.resolve("python.tar.gz")
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build()
            val request = HttpRequest.newBuilder(URI.create(linuxPythonUrl)).timeout(Duration.ofMinutes(3)).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive))
            check(response.statusCode() == 200) { "O download do Python falhou (HTTP ${response.statusCode()})." }

            progress("Verificando o Python e preparando o Turing…")
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(Files.newInputStream(archive), digest).use { it.transferTo(java.io.OutputStream.nullOutputStream()) }
            check(HexFormat.of().formatHex(digest.digest()) == linuxPythonSha256) { "O arquivo do Python falhou na verificação SHA-256." }
            val tar = "tar"
            run(stage, listOf(tar, "-xzf", archive.toString()), 60, "Extrair o Python")
            Files.delete(archive)
            unpackRuntime(stage)
            progress("Instalando as dependências no Python Standalone…")
            val python = privatePython(stage).toString()
            run(
                stage,
                listOf(
                    python, "-I", "-m", "pip", "--isolated", "install", "--no-cache-dir",
                    "--disable-pip-version-check", "--only-binary=:all:", "--retries", "2", "--timeout", "20",
                    "--find-links", "wheels", "-r", "requirements.txt",
                ),
                300,
                "Instalar dependências",
            )

            progress("Validando o Turing e os temas…")
            run(stage, listOf(python, "-I", "-m", "pip", "--isolated", "--no-cache-dir", "check"), 30, "Verificar dependências")
            val validator = stage.resolve("validate-runtime.py")
            Files.writeString(validator, validation)
            run(
                stage,
                listOf(python, "-I", "-X", "utf8", validator.toString()),
                30,
                "Validar o Python e os temas",
            )
            Files.delete(validator)
            Files.deleteIfExists(stage.resolve("install.log"))
            Files.move(stage, destination, ATOMIC_MOVE)
        } finally {
            if (Files.exists(stage)) {
                Files.walk(stage).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }

    private fun unpackRuntime(stage: Path) {
        ZipInputStream(openTuringRuntime()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = stage.resolve(entry.name).normalize()
                check(output.startsWith(stage)) { "O pacote do Turing contém um caminho inválido." }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                } else {
                    Files.createDirectories(output.parent)
                    Files.copy(zip, output)
                }
            }
        }
    }

    private fun run(directory: Path, command: List<String>, timeoutSeconds: Long, step: String) {
        val log = directory.resolve("install.log")
        val process = ProcessBuilder(command).directory(directory.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "$step excedeu o tempo limite." }
            check(process.exitValue() == 0) {
                val detail = Files.readAllBytes(log).toString(Charsets.UTF_8)
                    .lineSequence().filter { it.isNotBlank() }.toList().takeLast(4).joinToString("\n")
                "$step falhou.\n$detail"
            }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
    }

    private companion object {
        const val linuxPythonUrl = "https://github.com/astral-sh/python-build-standalone/releases/download/20261026/cpython-3.14.10%2B20261026-x86_64-unknown-linux-gnu-install_only_stripped.tar.gz"
        const val linuxPythonSha256 = "4d3c16c59ed0802a3d1d34d8bce504b15ceeaff39d62dadecc6a9fa803412aed"
        val validation = """
            from pathlib import Path
            import GPUtil, numpy, psutil, serial, sys, yaml
            from PIL import Image, ImageTk
            import darkdetect, ruamel.yaml, sv_ttk, tktooltip, tkinter
            root = Path("turing")
            compile((root / "main.py").read_bytes(), "main.py", "exec")
            for name in ("configure.py", "theme-editor.py"):
                compile((root / name).read_bytes(), name, "exec")
            for name in ("res/backgrounds/circular-mask.png", "res/docs/no-preview.png", "res/docs/error-in-theme.png"):
                assert (root / name).is_file()

            compile((root / "library/sensors/sensors_custom.py").read_bytes(), "sensors_custom.py", "exec")
            config = yaml.safe_load((root / "config.yaml").read_bytes())
            themes = sorted((root / "res/themes").glob("*/theme.yaml"))
            assert themes, "Nenhum tema no pacote"
            assert config["config"]["THEME"] in {theme.parent.name for theme in themes}
            for theme in themes:
                data = yaml.safe_load(theme.read_bytes())
                display = data.get("display", {})
                assert display.get("DISPLAY_SIZE", '3.5"') == '3.5"', str(theme)
                assert display.get("DISPLAY_ORIENTATION", "portrait") == "landscape", str(theme)
                for name in ("background.png", "preview.png"):
                    with Image.open(theme.parent / name) as image:
                        assert image.size == (480, 320)
                        image.verify()
        """.trimIndent()
    }
}

internal fun openTuringRuntime() = checkNotNull(
    StandaloneInstaller::class.java.getResourceAsStream("/turing-runtime.zip"),
) { "Pacote do Turing ausente nesta versão do EasyUSBScreen." }
