package at.libmatti.mtztool

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Random access view on an archive (ZIP or tar inside a MIUI backup). */
interface ArchiveReader : Closeable {
    val names: List<String>
    fun read(name: String): ByteArray?
    override fun close() {}
}

class ZipArchive(file: File) : ArchiveReader {
    private val zip = ZipFile(file)
    private val entriesByName: Map<String, ZipEntry> = buildMap {
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (!entry.isDirectory) put(entry.name, entry)
        }
    }

    override val names: List<String> = entriesByName.keys.toList()

    override fun read(name: String): ByteArray? {
        val entry = entriesByName[name] ?: return null
        return loop(entry)
    }

    private fun loop(entry: ZipEntry): ByteArray? {
        return try {
            zip.getInputStream(entry).use { it.readBytes() }
        } catch (e: Exception) {
            Log.w("ZipArchive", "Cannot read $entry", e)
            null
        }
    }

    override fun close() = zip.close()
}

/**
 * Minimal tar reader supporting ustar, GNU long names and pax extended headers.
 * Entries are referenced by offset, so the payload is not copied.
 */
class TarArchive(
    private val data: ByteArray,
    private val baseOffset: Int = 0,
) : ArchiveReader {

    private data class Range(val offset: Int, val size: Int)

    private val entries = LinkedHashMap<String, Range>()

    init {
        parse()
    }

    override val names: List<String> get() = entries.keys.toList()

    override fun read(name: String): ByteArray? {
        val range = entries[name] ?: return null
        if (range.size < 0 || range.offset + range.size > data.size) return null
        return data.copyOfRange(range.offset, range.offset + range.size)
    }

    private fun parse() {
        var pos = baseOffset
        var gnuLongName: String? = null
        var paxPath: String? = null
        var paxSize: Int? = null

        while (pos + 512 <= data.size) {
            if (data[pos.toInt()].toInt() == 0 && isZeroBlock(pos)) break

            val name = stringField(pos, 100)
            val size = parseNumeric(pos + 124, 12).toInt()
            val type = data[pos + 156].toInt().toChar()
            val prefix = stringField(pos + 345, 155)
            val fullName = if (prefix.isEmpty()) name else "$prefix/$name"
            val dataOffset = pos + 512

            // Entries with a pax size override store that override in the
            // extended header, not in the tar header of the real entry.
            val effectiveSize = if (type == 'L' || type == 'x' || type == 'g') size else (paxSize ?: size)

            when (type) {
                'L' -> gnuLongName = stringOf(dataOffset, size).trimEnd('\u0000')
                'x', 'g' -> {
                    val pax = parsePax(dataOffset, size)
                    paxPath = pax["path"] ?: paxPath
                    paxSize = pax["size"]?.toIntOrNull() ?: paxSize
                }
                '0', '\u0000', '7' -> {
                    val finalName = paxPath ?: gnuLongName ?: fullName
                    val finalSize = effectiveSize
                    if (finalName.isNotBlank()) {
                        entries[finalName] = Range(dataOffset, finalSize)
                    }
                    gnuLongName = null
                    paxPath = null
                    paxSize = null
                }
                else -> {
                    // directories, symlinks, ... are not needed
                    gnuLongName = null
                    paxPath = null
                    paxSize = null
                }
            }

            pos = dataOffset + padded(effectiveSize)
        }
    }

    private fun isZeroBlock(pos: Int): Boolean {
        if (pos + 512 > data.size) return true
        for (i in 0 until 512) {
            if (data[pos + i].toInt() != 0) return false
        }
        return true
    }

    private fun padded(size: Int): Int = ((size + 511) / 512) * 512

    private fun stringField(offset: Int, length: Int): String =
        String(data, offset, length, StandardCharsets.ISO_8859_1)
            .substringBefore('\u0000')
            .trim()

    private fun stringOf(offset: Int, length: Int): String =
        String(data, offset, length, StandardCharsets.UTF_8)

    private fun parseNumeric(offset: Int, length: Int): Long {
        if (data[offset].toInt() and 0x80 != 0) {
            var value = 0L
            for (i in 1 until length) {
                value = (value shl 8) or (data[offset + i].toLong() and 0xFF)
            }
            return value
        }
        val text = String(data, offset, length, StandardCharsets.US_ASCII)
            .trim { it == ' ' || it == '\u0000' }
        return if (text.isEmpty()) 0L else text.toLongOrNull(8) ?: 0L
    }

    private fun parsePax(offset: Int, length: Int): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var pos = offset
        val end = offset + length
        while (pos < end) {
            var space = pos
            while (space < end && data[space].toInt() != ' '.code) space++
            if (space >= end) break
            val recordLength = String(data, pos, space - pos, StandardCharsets.US_ASCII).toIntOrNull()
                ?: break
            if (recordLength <= 0) break
            val recordEnd = pos + recordLength
            if (recordEnd > end) break
            val bodyLength = recordEnd - (space + 1) - 1
            if (bodyLength > 0) {
                val body = String(data, space + 1, bodyLength, StandardCharsets.UTF_8)
                val equals = body.indexOf('=')
                if (equals > 0) result[body.substring(0, equals)] = body.substring(equals + 1)
            }
            pos = recordEnd
        }
        return result
    }
}

/**
 * Reads the proprietary MIUI backup header, optionally inflates the payload
 * and returns the contained tar stream.
 */
object MiuiBackup {

    private const val MAGIC = "MIUI BACKUP\n"

    fun looksLikeBackup(file: File): Boolean {
        return try {
            FileInputStream(file).use { stream ->
                val head = ByteArray(MAGIC.length)
                var read = 0
                while (read < head.size) {
                    val count = stream.read(head, read, head.size - read)
                    if (count <= 0) break
                    read += count
                }
                read == head.size && String(head, StandardCharsets.US_ASCII) == MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    fun open(file: File): TarArchive {
        val data = file.readBytes()
        var pos = 0
        val lines = mutableListOf<String>()
        repeat(9) {
            val newline = data.indexOfByte('\n'.code.toByte(), pos)
            if (newline < 0) throw IllegalArgumentException("Ungültiger MIUI-Backup-Header")
            lines.add(String(data, pos, newline - pos, StandardCharsets.UTF_8))
            pos = newline + 1
        }
        val compressed = lines[7].trim()
        return when (compressed) {
            "0" -> TarArchive(data, pos)
            "1" -> TarArchive(inflate(data, pos), 0)
            else -> throw IllegalArgumentException("Unbekanntes Backup-Format (compressed=$compressed)")
        }
    }

    private fun inflate(data: ByteArray, offset: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data, offset, data.size - offset)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    output.write(buffer, 0, count)
                }
            }
        } finally {
            inflater.end()
        }
        return output.toByteArray()
    }

    private fun ByteArray.indexOfByte(value: Byte, from: Int): Int {
        for (i in from until size) {
            if (this[i] == value) return i
        }
        return -1
    }
}

/** Builds a nested ZIP (used for folder style .mtz entries). */
fun buildNestedZip(
    archive: ArchiveReader,
    prefix: String,
    entries: List<String>,
): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        for (entry in entries) {
            val content = archive.read(entry) ?: continue
            zip.putNextEntry(ZipEntry(entry.removePrefix(prefix)))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
