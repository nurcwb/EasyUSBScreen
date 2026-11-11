package br.com.infonur.easyusbscreen

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Does not follow symlinks or leave an open walk stream while deleting directories. */
internal fun removeStandaloneTree(root: Path) {
    if (Files.notExists(root, NOFOLLOW_LINKS)) return
    try {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, failure: IOException?): FileVisitResult {
                if (failure != null) throw failure
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    } catch (failure: IOException) {
        val file = (failure as? java.nio.file.FileSystemException)?.file ?: root.toString()
        throw IllegalStateException(
            "Não foi possível remover $file. " +
                "Verifique as permissões e tente Desinstalar novamente. " +
                "Detalhe: ${failure.message}. Os arquivos já removidos não serão restaurados.", failure,
        )
    }
}
