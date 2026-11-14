package at.libmatti.mtztool

import android.content.Context
import java.io.ByteArrayInputStream

/**
 * Applies themes without root by handing the .mtz to the Xiaomi Theme Manager
 * through its exported ApplyThemeForScreenshot entry point.
 *
 * The file is copied to /sdcard/Download/MTZTool first (the Theme Manager
 * requires a readable .mtz path below /sdcard) and the activity is started as
 * the shell user through Shizuku. This is intentionally the "good enough"
 * path; a cleaner ThemeService integration can replace it later without
 * changing the UI.
 */
object ShizukuThemeApplier {

    const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
    const val APPLY_ACTIVITY = "$THEME_MANAGER_PACKAGE/.ApplyThemeForScreenshot"
    private const val REMOTE_DIR = "/sdcard/Download/MTZTool"

    data class Result(
        val message: String,
        val remotePath: String,
        val commandOutput: String,
    )

    /**
     * Never use this when the user selected ROOT: the root path is handled by
     * [RootThemeApplier] and writes directly to /data/system/theme.
     */
    fun apply(
        context: Context,
        theme: ThemeSession.Theme,
        onLog: (String) -> Unit = {},
    ): kotlin.Result<Result> {
        if (AppSettings.getPrivilegeProvider(context) == PrivilegeProvider.ROOT) {
            return kotlin.Result.failure(
                IllegalStateException("ROOT ist ausgewählt – RootThemeApplier verwenden.")
            )
        }
        if (ShizukuHelper.getStatus() != ShizukuHelper.Status.READY) {
            return kotlin.Result.failure(
                IllegalStateException(
                    "Shizuku ist nicht bereit. Bitte Shizuku starten und die Berechtigung erteilen."
                )
            )
        }

        return try {
            val safeName = sanitizeFileName(theme.name.substringBeforeLast('.'))
                .ifBlank { "theme" }
            val remote = "$REMOTE_DIR/$safeName-${System.currentTimeMillis()}.mtz"

            onLog("Bereite Übergabe an den Theme Manager vor …")
            val prep = ShizukuHelper.executeShellCommand(
                "mkdir -p '$REMOTE_DIR' && rm -f '$REMOTE_DIR/'*.mtz"
            )
            if (prep.exitCode != 0) {
                return kotlin.Result.failure(
                    IllegalStateException(
                        "Download-Verzeichnis konnte nicht vorbereitet werden: ${prep.stderr}"
                    )
                )
            }

            onLog("Kopiere Theme nach $remote …")
            val copy = ShizukuHelper.copyStreamToPath(
                ByteArrayInputStream(theme.bytes),
                remote,
            )
            copy.exceptionOrNull()?.let {
                return kotlin.Result.failure(it)
            }

            val command = "am start -n '$APPLY_ACTIVITY' " +
                "--es theme_file_path '$remote' " +
                "--es api_called_from 'mtz_tool'"
            onLog("Starte Theme-Manager-Übergabe …")
            val start = ShizukuHelper.executeShellCommand(command)
            val output = (start.stdout + "\n" + start.stderr).trim()

            val failed = start.exitCode != 0 ||
                output.contains("Error:", ignoreCase = true) ||
                output.contains("Exception", ignoreCase = true) ||
                output.contains("does not exist", ignoreCase = true)

            if (failed) {
                kotlin.Result.failure(
                    IllegalStateException("Theme Manager konnte nicht gestartet werden: $output")
                )
            } else {
                onLog("Theme Manager übernimmt das Theme.")
                kotlin.Result.success(
                    Result(
                        message = "Theme an den Theme Manager übergeben. " +
                            "Die Anwendung wird dort abgeschlossen.",
                        remotePath = remote,
                        commandOutput = output,
                    )
                )
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    fun cleanup(onLog: (String) -> Unit = {}) {
        val result = ShizukuHelper.executeShellCommand("rm -f '$REMOTE_DIR/'*.mtz")
        if (result.exitCode == 0) {
            onLog("Temporäre Theme-Dateien entfernt.")
        } else {
            onLog("Aufräumen fehlgeschlagen: ${result.stderr}")
        }
    }
}
