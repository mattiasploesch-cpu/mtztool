package at.libmatti.mtztool

import com.android.apksig.ApkVerifier
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.InflaterInputStream

/**
 * Applies an ``MTZPATCH1`` delta to the installed base APK.
 *
 * The patch contains COPY(offset,length) and ADD(bytes) operations. The result
 * is written to a new file, checked against the SHA-256 from the signed update
 * manifest and verified with apksig before the installer sees it. Nothing is
 * executed while patching; the app keeps running and the activity streams the
 * log in real time.
 */
object ApkPatchEngine {

    private val MAGIC = "MTZPATCH1".toByteArray(Charsets.US_ASCII)
    private const val OP_COPY = 0x01
    private const val OP_ADD = 0x02
    private const val MAX_HEADER = 64 * 1024
    private const val BUFFER = 128 * 1024

    data class Header(
        val fromVersionCode: Int,
        val toVersionCode: Int,
        val fromVersionName: String,
        val toVersionName: String,
        val fromSha256: String,
        val toSha256: String,
        val toSize: Long,
    )

    data class Applied(val file: File, val header: Header)

    fun apply(
        baseApk: File,
        patchFile: File,
        output: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Applied {
        if (!baseApk.isFile || baseApk.length() == 0L) {
            throw IOException("Die installierte Basis-APK wurde nicht gefunden.")
        }
        if (!patchFile.isFile || patchFile.length() == 0L) {
            throw IOException("Die Patch-Datei fehlt.")
        }

        output.parentFile?.mkdirs()
        output.delete()

        DataInputStream(BufferedInputStream(FileInputStream(patchFile))).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IOException("Unbekanntes Patch-Format.")
            }
            val headerLength = input.readInt()
            if (headerLength !in 1..MAX_HEADER) {
                throw IOException("Ungültiger Patch-Header.")
            }
            val headerBytes = ByteArray(headerLength)
            input.readFully(headerBytes)
            val json = JSONObject(String(headerBytes, Charsets.UTF_8))
            val header = Header(
                fromVersionCode = json.optInt("fromVersionCode"),
                toVersionCode = json.optInt("toVersionCode"),
                fromVersionName = json.optString("fromVersionName"),
                toVersionName = json.optString("toVersionName"),
                fromSha256 = json.optString("fromSha256"),
                toSha256 = json.optString("toSha256"),
                toSize = json.optLong("toSize"),
            )
            if (header.toSize <= 0 || header.toSha256.length != 64) {
                throw IOException("Der Patch-Header ist unvollständig.")
            }

            val baseHash = UpdateChecker.sha256(baseApk)
            if (!header.fromSha256.equals(baseHash, ignoreCase = true)) {
                throw IOException(
                    "Die installierte APK passt nicht zu diesem Patch. " +
                        "Bitte lade die vollständige APK herunter."
                )
            }

            val ops = InflaterInputStream(input, java.util.zip.Inflater(), BUFFER)
            RandomAccessFile(baseApk, "r").use { base ->
                RandomAccessFile(output, "rw").use { target ->
                    target.setLength(0)
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val opcode = ops.read()
                        if (opcode < 0) break
                        when (opcode) {
                            OP_COPY -> {
                                val offset = readVarint(ops)
                                val length = readVarint(ops)
                                if (offset < 0 || length <= 0 || offset + length > base.length()) {
                                    throw IOException("Kopieranweisung liegt außerhalb der Basis-APK.")
                                }
                                base.seek(offset.toLong())
                                var remaining = length
                                while (remaining > 0) {
                                    val chunk = minOf(remaining.toLong(), buffer.size.toLong()).toInt()
                                    base.readFully(buffer, 0, chunk)
                                    target.write(buffer, 0, chunk)
                                    remaining -= chunk
                                }
                            }
                            OP_ADD -> {
                                val length = readVarint(ops)
                                if (length < 0 || length > header.toSize) {
                                    throw IOException("Ungültige Einfügeanweisung.")
                                }
                                var remaining = length
                                while (remaining > 0) {
                                    val chunk = minOf(remaining, buffer.size)
                                    var filled = 0
                                    while (filled < chunk) {
                                        val read = ops.read(buffer, filled, chunk - filled)
                                        if (read <= 0) throw IOException("Patch endet zu früh.")
                                        filled += read
                                    }
                                    target.write(buffer, 0, chunk)
                                    remaining -= chunk
                                }
                            }
                            else -> throw IOException("Unbekannter Patch-Befehl: $opcode")
                        }
                        onProgress(target.length(), header.toSize)
                    }
                }
            }

            if (output.length() != header.toSize) {
                output.delete()
                throw IOException(
                    "Patch-Ergebnis hat ${output.length()} statt ${header.toSize} Bytes."
                )
            }
            val actual = UpdateChecker.sha256(output)
            if (!actual.equals(header.toSha256, ignoreCase = true)) {
                output.delete()
                throw IOException(
                    "SHA-256 des gepatchten APK stimmt nicht. Erwartet: " +
                        "${header.toSha256}, erhalten: $actual"
                )
            }

            val verification = ApkVerifier.Builder(output).build().verify()
            if (!verification.isVerified) {
                output.delete()
                val issue = verification.errors.firstOrNull()?.toString() ?: "unbekannt"
                throw IOException("Signaturprüfung fehlgeschlagen: $issue")
            }
            return Applied(output, header)
        }
    }

    private fun readVarint(input: java.io.InputStream): Int {
        var value = 0
        var shift = 0
        while (shift < 35) {
            val byte = input.read()
            if (byte < 0) throw IOException("Patch endet mitten in einer Anweisung.")
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
        }
        throw IOException("Varint ist zu lang.")
    }
}
