package com.r0ine.auraforge.core

import com.r0ine.auraforge.adventure.Adventurer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.logging.Logger

@Serializable
data class ForgeRecord(
    val id: String,
    val owner: String,
    val ownerName: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val items: List<String> = emptyList(),
    val crafts: Int = 0,
)

@Serializable
data class SmithRecord(
    val name: String,
    val crafted: Int = 0,
    val perfect: Int = 0,
    val best: Int = 0,
)

@Serializable
data class AltarRecord(
    val id: String,
    val owner: String,
    val ownerName: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val combines: Int = 0,
)

@Serializable
data class SaveFile(
    val version: Int = 1,
    val forges: List<ForgeRecord> = emptyList(),
    val smiths: Map<String, SmithRecord> = emptyMap(),
    val altars: List<AltarRecord> = emptyList(),
    val adventurers: Map<String, Adventurer> = emptyMap(),
)

class Storage(private val file: File, private val logger: Logger) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun load(): SaveFile {
        if (!file.exists()) return SaveFile()
        return try {
            json.decodeFromString(SaveFile.serializer(), file.readText())
        } catch (error: Exception) {
            val backup = File(file.parentFile, "${file.name}.broken-${System.currentTimeMillis()}")
            file.copyTo(backup, overwrite = true)
            logger.severe("${file.name} okunamadı (${error.message}). Dosya ${backup.name} olarak yedeklendi.")
            SaveFile()
        }
    }

    fun write(data: SaveFile) = synchronized(lock) {
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(SaveFile.serializer(), data))
        try {
            Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING)
        }
    }
}
