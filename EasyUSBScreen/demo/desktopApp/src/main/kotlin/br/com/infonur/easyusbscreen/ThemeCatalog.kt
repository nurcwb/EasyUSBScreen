package br.com.infonur.easyusbscreen

import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import org.jetbrains.skia.Image

internal fun projectFolder(name: String): Path? =
    generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .take(3)
        .map { it.resolve(name) }
        .firstOrNull { Files.isDirectory(it) }

private val previewFiles = listOf("preview.png", "background.png")

private fun decodePreview(bytes: ByteArray) =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

internal fun loadThemeCatalog(): List<GalleryTheme> {
    val folder = projectFolder("themes")
        ?: return loadBundledThemes()

    return Files.list(folder).use { entries ->
        entries.filter { Files.isRegularFile(it.resolve("theme.yaml")) }
            .sorted()
            .map { theme ->
                val preview = previewFiles.firstNotNullOfOrNull { name ->
                    val file = theme.resolve(name)
                    if (Files.isRegularFile(file)) {
                        runCatching { Files.readAllBytes(file) }.getOrNull()?.let(::decodePreview)
                    } else {
                        null
                    }
                }
                GalleryTheme(theme.fileName.toString(), preview)
            }
            .toList()
    }
}

private fun loadBundledThemes(): List<GalleryTheme> {
    val names = sortedSetOf<String>()
    val previews = mutableMapOf<Pair<String, String>, ByteArray>()
    ZipInputStream(openTuringRuntime()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory || !entry.name.startsWith("turing/res/themes/")) continue
            val parts = entry.name.removePrefix("turing/res/themes/").split('/')
            if (parts.size != 2) continue
            val (name, file) = parts
            when (file) {
                "theme.yaml" -> names.add(name)
                in previewFiles -> previews[name to file] = zip.readBytes()
            }
        }
    }
    return names.map { name ->
        val preview = previewFiles.firstNotNullOfOrNull { file -> previews[name to file]?.let(::decodePreview) }
        GalleryTheme(name, preview)
    }
}
