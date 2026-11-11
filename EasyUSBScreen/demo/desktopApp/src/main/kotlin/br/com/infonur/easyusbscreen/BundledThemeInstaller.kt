package br.com.infonur.easyusbscreen

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.zip.ZipInputStream

/** Adds a missing bundled theme on apply, without replacing existing themes or fonts. Call under withTuringLock. */
internal fun installBundledThemeIfMissing(turing: Path, theme: String) {
    require(theme.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _-]*"))) { "Nome de tema inválido." }
    val destination = turing.resolve("res/themes").resolve(theme)
    if (Files.exists(destination, NOFOLLOW_LINKS)) return
    val stage = Files.createTempDirectory(turing, ".theme-install-")
    try {
        val prefix = "turing/res/themes/$theme/"
        ZipInputStream(openTuringRuntime()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val relative = when {
                    entry.name.startsWith(prefix) -> "theme/" + entry.name.removePrefix(prefix)
                    entry.name.startsWith("turing/res/fonts/") -> "fonts/" + entry.name.removePrefix("turing/res/fonts/")
                    else -> continue
                }
                val output = stage.resolve(relative).normalize()
                check(output.startsWith(stage)) { "Caminho inválido no pacote de temas." }
                Files.createDirectories(output.parent)
                Files.copy(zip, output)
            }
        }
        val stagedTheme = stage.resolve("theme")
        check(Files.isRegularFile(stagedTheme.resolve("theme.yaml"))) { "O tema $theme não está incluído neste pacote." }
        val fonts = stage.resolve("fonts")
        if (Files.isDirectory(fonts)) Files.walk(fonts).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { source ->
                val target = turing.resolve("res/fonts").resolve(fonts.relativize(source))
                if (Files.notExists(target, NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target)
                }
            }
        }
        Files.createDirectories(destination.parent)
        Files.move(stagedTheme, destination, ATOMIC_MOVE)
    } finally {
        removeStandaloneTree(stage)
    }
}
