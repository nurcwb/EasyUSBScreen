package br.com.infonur.easyusbscreen

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class TuringSensorSettingsStore(private val config: Path) {
    fun load(): String = sensorField(Files.readString(config)).value.trim().removeSurrounding("\"").removeSurrounding("'")

    fun save(mode: String) = withTuringLock {
        check(mode in setOf("AUTO", "PYTHON")) { "Backend de sensores inválido." }
        val original = Files.readString(config)
        val field = sensorField(original)
        if (field.value.trim() != mode) {
            val backup = Files.createTempFile(config.parent, "config.yaml.bak-sensors-", "")
            Files.copy(config, backup, REPLACE_EXISTING)
            writeTuringConfig(config, original.replaceRange(field.start, field.start + field.value.length, mode))
        }
    }

    private fun sensorField(text: String): Field {
        val section = turingConfigSection.find(text) ?: error("Seção config ausente no Turing.")
        val match = sensorPattern.findAll(section.value).singleOrNull() ?: error("Campo HW_SENSORS ausente ou duplicado.")
        val value = checkNotNull(match.groups[1])
        return Field(value.value, section.range.first + value.range.first)
    }

    private data class Field(val value: String, val start: Int)

    companion object {
        val sensorPattern = Regex("(?m)^  HW_SENSORS:[ \\t]*([^#\\r\\n]+?)(?=[ \\t]*(?:#|\\r?$))")
    }
}
