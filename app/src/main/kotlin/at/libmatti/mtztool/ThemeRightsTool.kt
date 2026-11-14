package at.libmatti.mtztool

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Rights helper for themes.
 *
 * Important limitation: the MIUI Theme Manager only accepts `.mra` files that
 * are signed by Xiaomi. This tool cannot create such a signature; it validates
 * existing files, shows which components they cover and attaches the verified
 * files to the theme so a full theme can be packaged or imported.
 */
object ThemeRightsTool {

    data class LoadedMra(
        val fileName: String,
        val bytes: ByteArray,
        val valid: Boolean,
        val themeHash: String?,
        val assetCount: Int,
        val error: String?,
    )

    data class Result(
        val themeBytes: ByteArray,
        val attached: List<LoadedMra>,
        val rejected: List<LoadedMra>,
        val coverage: ThemeCoverage,
    )

    data class ThemeCoverage(
        val protectedComponents: Int,
        val coveredComponents: Int,
        val missing: List<String>,
        val validMras: Int,
        val totalMras: Int,
    ) {
        val fullTheme: Boolean get() = protectedComponents == 0 || missing.isEmpty()
        val summary: String
            get() = if (fullTheme) {
                "Alle $protectedComponents geschützten Bestandteile sind abgedeckt."
            } else {
                "$coveredComponents von $protectedComponents Bestandteilen abgedeckt · " +
                    "${missing.size} fehlen"
            }
    }

    fun inspect(fileName: String, bytes: ByteArray): LoadedMra {
        val rights = MraVerifier.verify(bytes)
        return LoadedMra(
            fileName = fileName,
            bytes = bytes,
            valid = rights.signatureValid,
            themeHash = rights.themeHash,
            assetCount = rights.assets.size,
            error = rights.error,
        )
    }

    fun inspectAll(files: List<Pair<String, ByteArray>>): List<LoadedMra> =
        files.map { (name, bytes) -> inspect(name, bytes) }

    /**
     * Adds the verified `.mra` files and a small `rights/rights.json` index to
     * the theme archive. Existing entries are preserved.
     */
    fun attach(
        themeBytes: ByteArray,
        mras: List<LoadedMra>,
        onProgress: (String) -> Unit = {},
    ): Result {
        val valid = mras.filter { it.valid }
        val rejected = mras.filterNot { it.valid }

        val output = ByteArrayOutputStream(themeBytes.size + 64 * 1024)
        ZipOutputStream(output).use { zip ->
            ZipInputStream(ByteArrayInputStream(themeBytes)).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (!name.startsWith("rights/")) {
                            zip.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
                            zip.write(input.readBytes())
                            zip.closeEntry()
                        }
                    }
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }

            valid.forEach { mra ->
                onProgress("Füge ${mra.fileName} hinzu …")
                val target = "rights/theme/" + ThemeComposer.sanitizeFileName(mra.fileName)
                    .let { if (it.endsWith(".mra", true)) it else "$it.mra" }
                zip.putNextEntry(ZipEntry(target).apply { time = System.currentTimeMillis() })
                zip.write(mra.bytes)
                zip.closeEntry()
            }

            val index = JSONObject().apply {
                put("tool", "MTZ Tool ThemeRightsTool")
                put("note", "Only Xiaomi-signed .mra files are accepted by the Theme Manager.")
                put("mras", JSONArray().apply {
                    valid.forEach { mra ->
                        put(
                            JSONObject().apply {
                                put("file", mra.fileName)
                                put("themeHash", mra.themeHash.orEmpty())
                                put("assets", mra.assetCount)
                                put("signatureValid", true)
                            }
                        )
                    }
                })
            }
            zip.putNextEntry(ZipEntry("rights/rights.json").apply { time = System.currentTimeMillis() })
            zip.write(index.toString(2).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val bytes = output.toByteArray()
        return Result(
            themeBytes = bytes,
            attached = valid,
            rejected = rejected,
            coverage = coverageOf(bytes),
        )
    }

    /** Re-reads the archive and evaluates the rights coverage. */
    fun coverageOf(themeBytes: ByteArray): ThemeCoverage {
        val temp = java.io.File.createTempFile("mtz-rights", ".mtz")
        return try {
            temp.writeBytes(themeBytes)
            val root = temp.parentFile ?: java.io.File(".")
            val parsed = ThemeSourceReader.read(temp, root, "theme", 0)
            val best = parsed.candidates.maxByOrNull { it.components.size }
                ?: return ThemeCoverage(0, 0, emptyList(), 0, 0)
            val assessment = ThemeRights.assess(best)
            if (assessment.fullTheme) {
                ThemeCoverage(
                    protectedComponents = assessment.protectedComponents.size,
                    coveredComponents = assessment.protectedComponents.size,
                    missing = emptyList(),
                    validMras = assessment.validMras.size,
                    totalMras = best.mras.size,
                )
            } else {
                ThemeCoverage(
                    protectedComponents = assessment.protectedComponents.size,
                    coveredComponents = assessment.protectedComponents.size -
                        assessment.missing.size,
                    missing = assessment.missing.map { ThemeComponents.displayName(it.code) },
                    validMras = assessment.validMras.size,
                    totalMras = best.mras.size,
                )
            }
        } catch (e: Exception) {
            ThemeCoverage(0, 0, listOf(e.message ?: "unlesbar"), 0, 0)
        } finally {
            temp.delete()
        }
    }
}
