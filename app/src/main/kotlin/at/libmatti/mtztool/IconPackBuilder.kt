package at.libmatti.mtztool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Converts the icons of an .mtz theme into a regular launcher icon pack APK.
 *
 * The APK is assembled on-device (binary manifest from assets, generated
 * resources.arsc, appfilter.xml/drawable.xml) and signed with the bundled
 * PKCS12 key. It is then installed via Shizuku so that launchers such as
 * Smart Launcher can pick it up.
 */
object IconPackBuilder {

    private const val TAG = "IconPackBuilder"

    // Must match the package declared in assets/iconpack_manifest.bin.
    const val ICON_PACK_PACKAGE = "com.niklas.mtztool.iconpack"
    private const val KEYSTORE_ASSET = "iconpack.p12"
    private const val KEY_ASSET = "iconpack.key"
    private const val CERT_ASSET = "iconpack.crt"
    private const val MANIFEST_ASSET = "iconpack_manifest.bin"
    private const val KEY_ALIAS = "iconpack"
    private const val KEY_PASSWORD = "mtztool"

    /** File name of the generated icon pack in the app cache. */
    private const val RESULT_NAME = "mtz-iconpack.apk"

    data class BuildResult(
        val file: File,
        val packageName: String,
        val iconCount: Int,
        val skippedCount: Int,
    )

    private data class RawIcon(
        val packageName: String,
        val extension: String,
        val bytes: ByteArray,
        val densityScore: Int,
    )

    /**
     * Reads [uri], extracts the icon ZIP and writes a signed icon pack APK.
     * Must be called from a background thread (file and network I/O).
     */
    fun build(
        context: Context,
        uri: Uri,
        title: String,
        onLog: (String) -> Unit = {},
    ): Result<BuildResult> = buildInternal(context, title, onLog) { workDir ->
        ThemeSourceReader.read(context, uri, workDir, title)
    }

    /** Same as [build], but reads the theme from the in-memory workspace. */
    fun buildFromBytes(
        context: Context,
        bytes: ByteArray,
        title: String,
        onLog: (String) -> Unit = {},
    ): Result<BuildResult> = buildInternal(context, title, onLog) { workDir ->
        val source = File(workDir, "workspace-theme.mtz")
        source.writeBytes(bytes)
        ThemeSourceReader.read(source, workDir, title)
    }

    private fun buildInternal(
        context: Context,
        title: String,
        onLog: (String) -> Unit,
        reader: (File) -> ThemeSourceReader.ReadResult,
    ): Result<BuildResult> {
        val workDir = File(context.cacheDir, "iconpack")
        return try {
            workDir.deleteRecursively()
            if (!workDir.mkdirs() && !workDir.isDirectory) {
                return Result.failure(Exception("Cache-Verzeichnis konnte nicht erstellt werden"))
            }

            onLog("📂 Lese Theme…")
            val parsed = reader(workDir)
            val withIcons = parsed.candidates.filter { candidate ->
                candidate.components.any { it.code == "icons" }
            }
            val candidate = withIcons.maxByOrNull { it.components.size }
                ?: return Result.failure(
                    Exception(
                        "Dieses Theme enthält keine Symbole (icons). " +
                            "Ein Icon-Pack kann daraus nicht erstellt werden."
                    )
                )

            val iconsZip = candidate.components.first { it.code == "icons" }.data
            onLog("🎨 Suche Symbol-Dateien…")
            val rawIcons = extractIcons(iconsZip)
            if (rawIcons.isEmpty()) {
                return Result.failure(
                    Exception("Im Theme wurden keine passenden App-Symbole gefunden.")
                )
            }
            onLog("🔎 ${rawIcons.size} Symbole gefunden, prüfe installierte Apps…")

            val packageManager = context.packageManager
            val matched = mutableListOf<Pair<String, RawIcon>>() // component -> icon
            var skipped = 0

            for ((index, icon) in rawIcons.withIndex()) {
                val components = launcherComponents(packageManager, icon.packageName)
                if (components.isEmpty()) {
                    skipped++
                } else {
                    components.forEach { componentName -> matched += componentName to icon }
                }
                if ((index + 1) % 100 == 0) {
                    onLog("… ${index + 1}/${rawIcons.size} geprüft")
                }
            }

            if (matched.isEmpty()) {
                return Result.failure(
                    Exception(
                        "Keine der Symbole im Theme gehört zu einer installierten App. " +
                            "Installiere die gewünschten Apps und versuche es erneut."
                    )
                )
            }

            val unique = matched.distinctBy { it.first }
            onLog("📦 Baue Icon-Pack mit ${unique.size} Symbolen…")

            val drawables = unique.mapIndexed { index, (_, icon) ->
                ResourceTable.Drawable(
                    name = drawableName(index),
                    path = "res/drawable/${drawableName(index)}.${icon.extension}",
                )
            }

            // res/drawable/<name>.<ext>
            val drawableDir = File(workDir, "res/drawable").apply { mkdirs() }
            unique.forEachIndexed { index, (_, icon) ->
                File(drawableDir, "${drawableName(index)}.${icon.extension}").writeBytes(icon.bytes)
            }

            // assets/appfilter.xml + assets/drawable.xml
            val assetsDir = File(workDir, "assets").apply { mkdirs() }
            File(assetsDir, "appfilter.xml").writeText(
                buildAppFilter(unique, drawables)
            )
            File(assetsDir, "drawable.xml").writeText(buildDrawableList(drawables))

            val resources = ResourceTable.build(ICON_PACK_PACKAGE, drawables)
            val manifest = context.assets.open(MANIFEST_ASSET).use { it.readBytes() }

            val unsigned = File(workDir, "unsigned.apk")
            onLog("🗜️ Packe APK…")
            writeUnsignedApk(unsigned, manifest, resources, drawableDir, assetsDir, drawables)

            onLog("🔐 Signiere APK…")
            val signed = File(workDir, RESULT_NAME)
            sign(context, unsigned, signed)

            onLog("✅ Icon-Pack fertig: ${unique.size} Symbole")
            Result.success(
                BuildResult(
                    file = signed,
                    packageName = ICON_PACK_PACKAGE,
                    iconCount = unique.size,
                    skippedCount = skipped,
                )
            )
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Icon pack build failed", e)
            Result.failure(
                IllegalStateException(
                    "Icon-Pack konnte nicht erstellt werden: " +
                        (e.message ?: e.javaClass.simpleName),
                    e,
                )
            )
        }
    }

    /**
     * Returns every MAIN/LAUNCHER component of [packageName]. Launchers such as
     * Nova and Smart Launcher match appfilter entries per component, so using
     * only the default launch intent leaves some icons unthemed.
     */
    private fun launcherComponents(
        packageManager: PackageManager,
        packageName: String,
    ): List<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val resolved = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        val components = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            "ComponentInfo{${activity.packageName}/${activity.name}}"
        }.distinct()
        if (components.isNotEmpty()) return components

        val launch = packageManager.getLaunchIntentForPackage(packageName)?.component
            ?: return emptyList()
        return listOf("ComponentInfo{${launch.packageName}/${launch.className}}")
    }

    // ── Icon extraction ─────────────────────────────────────

    private fun extractIcons(zipBytes: ByteArray): List<RawIcon> {
        val best = linkedMapOf<String, RawIcon>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    parseIcon(name, zip.readBytes())?.let { icon ->
                        val previous = best[icon.packageName]
                        if (previous == null || icon.densityScore > previous.densityScore) {
                            best[icon.packageName] = icon
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return best.values.sortedBy { it.packageName }
    }

    private fun parseIcon(path: String, bytes: ByteArray): RawIcon? {
        val fileName = path.substringAfterLast('/')
        val extension = when {
            fileName.endsWith(".png", true) -> "png"
            fileName.endsWith(".webp", true) -> "webp"
            else -> return null
        }
        if (bytes.isEmpty()) return null
        val packageName = fileName.substringBeforeLast('.')
        if (!PACKAGE_PATTERN.matches(packageName)) return null
        return RawIcon(packageName, extension, bytes, densityScore(path))
    }

    private val PACKAGE_PATTERN =
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    private fun densityScore(path: String): Int {
        val lower = path.lowercase(Locale.US)
        return when {
            lower.contains("drawable-xxxhdpi") -> 640
            lower.contains("drawable-xxhdpi") -> 480
            lower.contains("drawable-xhdpi") -> 320
            lower.contains("drawable-hdpi") -> 240
            lower.contains("drawable-mdpi") -> 160
            lower.contains("drawable-ldpi") -> 120
            lower.contains("drawable-nodpi") -> 80
            else -> 100
        }
    }

    private fun drawableName(index: Int): String =
        String.format(Locale.US, "ic_%04d", index)

    // ── XML assets ──────────────────────────────────────────

    private fun buildAppFilter(
        unique: List<Pair<String, RawIcon>>,
        drawables: List<ResourceTable.Drawable>,
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<resources>\n")
        unique.forEachIndexed { index, (component, _) ->
            append("  <item component=\"")
            append(escapeXml(component))
            append("\" drawable=\"")
            append(drawables[index].name)
            append("\"/>\n")
        }
        append("</resources>\n")
    }

    private fun buildDrawableList(drawables: List<ResourceTable.Drawable>): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<resources>\n")
            drawables.forEach {
                append("  <item drawable=\"")
                append(it.name)
                append("\"/>\n")
            }
            append("</resources>\n")
        }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // ── APK writing ─────────────────────────────────────────

    private fun writeUnsignedApk(
        target: File,
        manifest: ByteArray,
        resources: ByteArray,
        drawableDir: File,
        assetsDir: File,
        drawables: List<ResourceTable.Drawable>,
    ) {
        val counter = CountingOutputStream(BufferedOutputStream(FileOutputStream(target)))
        ZipOutputStream(counter).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifest)
            zip.closeEntry()

            // resources.arsc must not be compressed and should be 4-byte aligned
            // so that Android can map it directly.
            putStoredEntry(zip, counter, "resources.arsc", resources, alignment = 4)

            for (drawable in drawables) {
                val file = File(drawableDir, drawable.path.substringAfterLast('/'))
                zip.putNextEntry(ZipEntry(drawable.path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            for (name in listOf("appfilter.xml", "drawable.xml")) {
                zip.putNextEntry(ZipEntry("assets/$name"))
                File(assetsDir, name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Adds an uncompressed entry and aligns its content by inserting a padding
     * extra field into the local header.
     */
    private fun putStoredEntry(
        zip: ZipOutputStream,
        counter: CountingOutputStream,
        name: String,
        data: ByteArray,
        alignment: Int,
    ) {
        val nameLength = name.toByteArray(Charsets.UTF_8).size
        val headerBase = counter.count + 30 + nameLength
        val padding = if (alignment > 1) {
            ((alignment - ((headerBase + 4) % alignment)) % alignment).toInt()
        } else {
            0
        }
        val extraLength = if (alignment > 1) 4 + padding else 0

        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().apply { update(data) }.value
            if (extraLength > 0) {
                val extra = ByteArray(extraLength)
                extra[0] = 0x35                     // header id 0xD935
                extra[1] = 0xD9.toByte()
                extra[2] = (padding and 0xff).toByte()
                extra[3] = ((padding shr 8) and 0xff).toByte()
                setExtra(extra)
            }
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private fun sign(context: Context, input: File, output: File) {
        val (privateKey, certificate) = signingIdentity(context)

        val signer = ApkSigner.SignerConfig.Builder(
            KEY_ALIAS,
            privateKey,
            listOf(certificate),
        ).build()

        try {
            ApkSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .setMinSdkVersion(23)
                .build()
                .sign()
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            throw IllegalStateException(
                "APK-Signierung fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName),
                e,
            )
        }
    }

    /**
     * Loads the bundled signing identity as PKCS#8/X.509 first. Android's
     * PKCS#12 implementation rejects some modern keytool encodings, which made
     * builds fail only on the device. The old PKCS#12 asset stays as fallback.
     */
    private fun signingIdentity(context: Context): Pair<PrivateKey, X509Certificate> {
        val direct = runCatching {
            val keyBytes = context.assets.open(KEY_ASSET).use { it.readBytes() }
            val certBytes = context.assets.open(CERT_ASSET).use { it.readBytes() }
            val key = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            key to certificate
        }.getOrNull()
        if (direct != null) return direct

        Log.w(TAG, "PKCS#8 key asset unavailable; falling back to iconpack.p12")
        val password = KEY_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(KEYSTORE_ASSET).use { keyStore.load(it, password) }
        val privateKey = keyStore.getKey(KEY_ALIAS, password) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        return privateKey to certificate
    }
}
