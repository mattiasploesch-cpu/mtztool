package at.libmatti.mtztool

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Helper for all Shizuku-related operations:
 *  - Permission checking/requesting
 *  - Copying files to protected directories via shell commands
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    /**
     * Currently selected privileged access method. Set from [AppSettings]
     * whenever the app starts or the selection changes.
     */
    @Volatile
    var selectedProvider: PrivilegeProvider = PrivilegeProvider.SHIZUKU

    /** Result of the last root check; null means it has not been tested yet. */
    @Volatile
    private var rootAvailable: Boolean? = null

    fun setRootAvailable(available: Boolean) {
        rootAvailable = available
    }

    fun isRootAvailable(): Boolean = rootAvailable == true

    /**
     * True when the selected provider is able to execute shell commands.
     */
    fun isReady(): Boolean = when (selectedProvider) {
        PrivilegeProvider.SHIZUKU -> getStatus() == Status.READY
        PrivilegeProvider.ROOT -> rootAvailable == true
        PrivilegeProvider.ADB,
        PrivilegeProvider.DHIZUKU -> false
    }

    // ── Status ────────────────────────────────────────────────

    enum class Status {
        NOT_INSTALLED,
        NOT_RUNNING,
        NO_PERMISSION,
        READY
    }

    fun getStatus(): Status {
        return try {
            if (!Shizuku.pingBinder()) {
                Status.NOT_RUNNING
            } else if (!hasPermission()) {
                Status.NO_PERMISSION
            } else {
                Status.READY
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku check failed", e)
            Status.NOT_INSTALLED
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    // ── Permission Listener ──────────────────────────────────

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    fun addBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.addBinderReceivedListener(listener)
    }

    fun removeBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.removeBinderReceivedListener(listener)
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.addBinderDeadListener(listener)
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.removeBinderDeadListener(listener)
    }

    // ── Library import ──────────────────────────────────────

    /**
     * Imports a .mtz/.bak theme into the Theme Manager's offline catalog (.data).
     *
     * The theme is imported in full only when a genuine, signature valid .mra
     * covers every protected component. Otherwise only the rights exempt
     * components (wallpaper, lockscreen, ...) are written and the UI shows a
     * warning dialog.
     */
    fun importMtzToLibrary(
        context: Context,
        sourceUri: Uri,
        title: String,
    ): Result<LibraryImport> {
        val cacheDir = File(context.cacheDir, "theme-import")
        return try {
            cacheDir.deleteRecursively()
            if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
                return Result.failure(Exception("Cache-Verzeichnis konnte nicht erstellt werden"))
            }

            val parsed = ThemeSourceReader.read(context, sourceUri, cacheDir, title)
            completeLibraryImport(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Import to library failed", e)
            Result.failure(e)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    /**
     * Same as [importMtzToLibrary], but uses the already imported in-memory
     * theme bytes. This makes the library import independent of URI
     * permissions and keeps the "import once" workflow consistent.
     */
    fun importMtzBytesToLibrary(
        context: Context,
        bytes: ByteArray,
        title: String,
    ): Result<LibraryImport> {
        val cacheDir = File(context.cacheDir, "theme-import")
        return try {
            cacheDir.deleteRecursively()
            if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
                return Result.failure(Exception("Cache-Verzeichnis konnte nicht erstellt werden"))
            }
            val source = File(cacheDir, "source.mtz")
            source.writeBytes(bytes)
            val parsed = ThemeSourceReader.read(source, cacheDir, title)
            completeLibraryImport(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Import to library failed", e)
            Result.failure(e)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    private fun completeLibraryImport(
        parsed: ThemeSourceReader.ReadResult,
    ): Result<LibraryImport> {
        parsed.notes.forEach { Log.i(TAG, it) }

        val assessed = parsed.candidates.map { it to ThemeRights.assess(it) }
        val best = assessed.maxWithOrNull(
            compareBy<Pair<ThemeSourceReader.Candidate, ThemeRights.Assessment>> {
                if (it.second.fullTheme) 1 else 0
            }.thenBy { it.first.components.size }
        ) ?: return Result.failure(Exception("Keine Theme-Daten gefunden."))

        val (candidate, assessment) = best
        Log.i(
            TAG,
            "Import candidate: ${candidate.title} (${candidate.kind}), " +
                "full=${assessment.fullTheme}, components=${candidate.components.size}"
        )

        return ThemeLibraryWriter.write(candidate, assessment)
    }

    /**
     * Streams an InputStream into a target file via Shizuku shell (`cat > targetPath`).
     */
    fun copyStreamToPath(input: InputStream, targetPath: String): Result<Unit> {
        return try {
            val process = newProcess(arrayOf("sh", "-c", "cat > '$targetPath'"), null, null)
            val stderrBuffer = StringBuilder()

            val errThread = Thread {
                try {
                    val err = BufferedReader(InputStreamReader(process.errorStream)).readText()
                    if (err.isNotBlank()) stderrBuffer.append(err)
                } catch (_: Exception) {}
            }
            errThread.start()

            input.use { inStream ->
                process.outputStream.use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }

            val exitCode = process.waitFor()
            errThread.join(1000)

            if (exitCode == 0) {
                executeShellCommand("chmod 644 '$targetPath'")
                Result.success(Unit)
            } else {
                Result.failure(Exception("cat fehlgeschlagen (exit $exitCode): $stderrBuffer"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Writes text directly to a file via Shizuku shell.
     */
    fun writeTextFile(targetPath: String, content: String): Result<Unit> {
        return try {
            val process = newProcess(arrayOf("sh", "-c", "cat > '$targetPath'"), null, null)
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(content)
                writer.flush()
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                executeShellCommand("chmod 644 '$targetPath'")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Schreiben fehlgeschlagen (exit $exitCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Installs a generated APK (for example the icon pack) through the shell
     * user. The APK is streamed to /data/local/tmp first.
     */
    fun installGeneratedApk(file: File, packageName: String): Result<String> {
        val remote = "/data/local/tmp/mtz-iconpack-${System.currentTimeMillis()}.apk"
        return try {
            val copy = copyStreamToPath(file.inputStream(), remote)
            val copyError = copy.exceptionOrNull()
            if (copyError != null) {
                return Result.failure(copyError)
            }

            val install = executeShellCommand("pm install -r -d '$remote'")
            executeShellCommand("rm -f '$remote'")
            val output = (install.stdout + "\n" + install.stderr).trim()

            if (install.exitCode == 0 && install.stdout.contains("Success", ignoreCase = true)) {
                Result.success("Icon-Pack installiert ($packageName)")
            } else {
                Result.failure(Exception("Installation fehlgeschlagen: $output"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Shell Command Execution ──────────────────────────────

    /**
     * Reads a file through the privileged shell as raw bytes. Used by the
     * theme puller to copy `/data/system/theme` into an .mtz.
     */
    fun readFileBytes(path: String, maxBytes: Long = 48L * 1024 * 1024): Result<ByteArray> {
        return try {
            val process = newProcess(arrayOf("sh", "-c", "cat '$path'"), null, null)
            val bytes = process.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw java.io.IOException("Datei zu groß: $path")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(bytes)
            } else {
                Result.failure(
                    Exception(error.ifBlank { "Lesen fehlgeschlagen (exit $exitCode)" })
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    /** True when [path] exists as a directory for the Shizuku shell user. */
    fun directoryExists(path: String): Boolean =
        executeShellCommand("[ -d '$path' ]").exitCode == 0

    fun executeShellCommand(command: String): ShellResult {
        val process = newProcess(arrayOf("sh", "-c", command), null, null)
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()
        return ShellResult(exitCode, stdout.trim(), stderr.trim())
    }

    private fun newProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        if (selectedProvider == PrivilegeProvider.ROOT) {
            // All internal callers either use `sh -c <command>` or simple
            // argument arrays, so executing through su is straightforward.
            val shellCommand = if (cmd.size >= 3 && cmd[0] == "sh" && cmd[1] == "-c") {
                cmd[2]
            } else {
                cmd.joinToString(" ") { quote(it) }
            }
            return ProcessBuilder("su", "-c", shellCommand).start()
        }

        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
