package br.com.infonur.easyusbscreen

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class NetworkSettingsStore(private val config: Path) {
    fun load(): NetworkSettings {
        val saved = networkField(Files.readString(config)).value.trim()
        val name = when {
            saved.startsWith("'") -> saved.removeSurrounding("'").replace("''", "'")
            saved.equals("null", ignoreCase = true) || saved == "~" -> ""
            else -> saved.removeSurrounding("\"")
        }.ifEmpty { null }
        return NetworkSettings(name, interfaces())
    }

    fun save(name: String?) = withTuringLock {
        val original = Files.readString(config)
        val field = networkField(original)
        val current = field.value.trim()
        val value = name?.let { "'${it.replace("'", "''")}'" } ?: "\"\""
        if (current != value) {
            check(name == null || interfaces().any { it.name == name }) {
                "A interface $name não está mais disponível. Abra as configurações de rede novamente."
            }
            val replacement = if (field.value.isEmpty() && original.getOrNull(field.start) == '#') "$value " else value
            val updated = original.replaceRange(field.start, field.start + field.value.length, replacement)
            val backup = Files.createTempFile(config.parent, "config.yaml.bak-network-", "")
            Files.copy(config, backup, REPLACE_EXISTING)
            writeTuringConfig(config, updated)
        }
    }

    private fun interfaces(): List<NetworkDevice> = Files.list(Path.of("/sys/class/net")).use { devices ->
        devices.map { device ->
            val name = device.fileName.toString()
            val kind = when {
                Files.exists(device.resolve("wireless")) || Files.exists(device.resolve("phy80211")) -> NetworkKind.WIFI
                Files.exists(device.resolve("device")) && isEthernet(device) -> NetworkKind.ETHERNET
                else -> NetworkKind.OTHER
            }
            NetworkDevice(name, kind)
        }.filter { it.name != "lo" }.sorted(compareBy({ it.kind }, { it.name })).toList()
    }

    private fun isEthernet(device: Path): Boolean = try {
        Files.readString(device.resolve("type")).trim() == "1"
    } catch (_: IOException) {
        false
    }

    private fun networkField(configText: String): NetworkField {
        val section = turingConfigSection.find(configText) ?: error("Seção config do Turing ausente.")
        val match = ethField.findAll(section.value).singleOrNull() ?: error("Campo config.ETH ausente ou duplicado.")
        val value = match.groups[2] ?: error("Campo config.ETH inválido.")
        return NetworkField(value.value, section.range.first + match.range.first + match.groupValues[1].length)
    }

    private data class NetworkField(val value: String, val start: Int)

    private companion object {
        val ethField = Regex("""(?m)^(  ETH:[ \t]*)(('[^\r\n]*?'|"[^\r\n]*?"|[^#\r\n]*?))(?=[ \t]*(?:#|\r?$))""")
    }
}
