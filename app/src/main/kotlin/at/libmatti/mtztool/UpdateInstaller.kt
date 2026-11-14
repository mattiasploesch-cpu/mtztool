package at.libmatti.mtztool

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs a downloaded update without opening a third party installer.
 *
 * Preferred path: stream the APK through the selected privilege backend and
 * run `pm install -r` as root/shell. This is a silent, truly internal update.
 * If no shell is available, the system package installer is used as a
 * fallback so the app never becomes impossible to update.
 */
object UpdateInstaller {

    data class InstallResult(
        val internalInstall: Boolean,
        val message: String,
    )

    fun canInstallInternally(context: Context): Boolean {
        if (!AppSettings.isShellInstallEnabled(context)) return false
        return when (AppSettings.getPrivilegeProvider(context)) {
            PrivilegeProvider.ROOT -> true
            else -> ShizukuHelper.getStatus() == ShizukuHelper.Status.READY
        }
    }

    fun install(
        context: Context,
        apk: File,
        onLog: (String) -> Unit = {},
    ): Result<InstallResult> {
        if (!apk.isFile || apk.length() == 0L) {
            return Result.failure(IllegalArgumentException("Die Update-Datei fehlt."))
        }
        if (!canInstallInternally(context)) {
            onLog("Kein Root/Shizuku verfügbar – öffne den System-Installer als Fallback.")
            return systemInstaller(context, apk)
        }

        val remote = "/data/local/tmp/mtz-update-${System.currentTimeMillis()}.apk"
        return try {
            onLog("Kopiere Update nach $remote …")
            val copy = ShizukuHelper.copyStreamToPath(apk.inputStream(), remote)
            copy.exceptionOrNull()?.let { return Result.failure(it) }

            val downgrade = if (AppSettings.isDowngradeAllowed(context)) " -d" else ""
            onLog("Installiere Update intern (pm install) …")
            val install = ShizukuHelper.executeShellCommand("pm install -r$downgrade '$remote'")
            ShizukuHelper.executeShellCommand("rm -f '$remote'")

            val output = (install.stdout + "\n" + install.stderr).trim()
            if (install.exitCode == 0 &&
                (output.contains("Success", ignoreCase = true) || output.isBlank())
            ) {
                onLog("Update erfolgreich installiert.")
                Result.success(InstallResult(true, "Update installiert. Bitte App neu starten."))
            } else {
                Result.failure(
                    IllegalStateException(
                        "Interne Installation fehlgeschlagen: ${
                            output.ifBlank { "exit ${install.exitCode}" }
                        }"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun systemInstaller(context: Context, apk: File): Result<InstallResult> {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(
                InstallResult(
                    internalInstall = false,
                    message = "System-Installer geöffnet. Die Installation muss dort bestätigt werden.",
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Kein Installationsweg verfügbar. Bitte Root/Shizuku aktivieren. " +
                        (e.message ?: ""),
                    e,
                )
            )
        }
    }
}
