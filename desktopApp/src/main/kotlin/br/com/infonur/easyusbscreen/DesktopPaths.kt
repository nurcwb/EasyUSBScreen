package br.com.infonur.easyusbscreen

import java.nio.file.Path

internal val linuxDesktop: Boolean get() = System.getProperty("os.name") == "Linux"
internal fun standaloneDirectory(): Path = Path.of(System.getProperty("user.home"), ".local/share/easyusbscreen/turing-standalone")
internal fun privatePython(root: Path): Path = root.resolve("python/bin/python3")
