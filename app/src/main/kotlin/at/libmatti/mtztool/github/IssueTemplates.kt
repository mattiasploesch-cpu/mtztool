package at.libmatti.mtztool.github

import android.content.Context
import android.os.Build
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.ShizukuHelper
import at.libmatti.mtztool.ThemeSession
import at.libmatti.mtztool.UpdateChecker
import at.libmatti.mtztool.device.DeviceProfiles

/**
 * Issue types and their mandatory fields.
 *
 * Bug reports and feature requests deliberately ask different questions: a
 * bug report needs reproduction steps and an observed result, a feature
 * request needs the underlying problem, a proposal and a use case. The
 * templates mirror the forms in `.github/ISSUE_TEMPLATE` so in-app issues look the
 * same as issues created on github.com.
 **/
enum class IssueKind(val label: String, val labels: List<String>, val prefix: String) {
    BUG("Bug", listOf("bug", "needs-triage"), "[Bug] "),
    FEATURE("Feature", listOf("enhancement"), "[Feature] "),
    CRASH("Crash", listOf("bug", "crash"), "[Crash] "),
}

enum class IssueField(val label: String, val hint: String, val minLength: Int) {
    WHAT_HAPPENED(
        "Was ist passiert?",
        "Beschreibe das beobachtete Verhalten so genau wie möglich.",
        20,
    ),
    STEPS(
        "Schritte zum Reproduzieren",
        "1. App öffnen\n2. …\n3. Fehler tritt auf",
        10,
    ),
    EXPECTED(
        "Erwartetes Verhalten",
        "Was hätte stattdessen passieren sollen?",
        10,
    ),
    ACTUAL(
        "Tatsächliches Verhalten",
        "Was passiert wirklich? Fehlermeldungen bitte wörtlich zitieren.",
        10,
    ),
    FREQUENCY(
        "Häufigkeit",
        "Immer / manchmal (z. B. 3 von 5 Versuchen)",
        3,
    ),
    PROBLEM(
        "Welches Problem löst dein Vorschlag?",
        "Beschreibe die Situation, in der dir etwas fehlt.",
        20,
    ),
    PROPOSAL(
        "Dein Vorschlag",
        "Wie sollte die Funktion funktionieren?",
        20,
    ),
    USE_CASE(
        "Anwendungsfall",
        "Wer profitiert und wann wird die Funktion genutzt?",
        10,
    ),
    ALTERNATIVES(
        "Alternativen",
        "Andere Wege, die du bereits ausprobiert hast (optional).",
        0,
    ),
    AFFECTED(
        "Betroffene Theme-Teile",
        "z. B. Symbole, Sperrbildschirm, Schriftart (optional).",
        0,
    );

    companion object {
        fun fieldsFor(kind: IssueKind): List<IssueField> = when (kind) {
            IssueKind.BUG -> listOf(
                WHAT_HAPPENED,
                STEPS,
                EXPECTED,
                ACTUAL,
                FREQUENCY,
                AFFECTED,
            )
            IssueKind.FEATURE -> listOf(
                PROBLEM,
                PROPOSAL,
                USE_CASE,
                ALTERNATIVES,
            )
            IssueKind.CRASH -> listOf(
                WHAT_HAPPENED,
                STEPS,
                FREQUENCY,
            )
        }
    }
}

object IssueTemplates {

    fun fields(kind: IssueKind): List<IssueField> = IssueField.fieldsFor(kind)

    /** Returns human readable problems; empty means the draft can be sent. */
    fun validate(
        kind: IssueKind,
        title: String,
        values: Map<IssueField, String>,
    ): List<String> {
        val problems = mutableListOf<String>()
        if (title.trim().length < 8) {
            problems += "Der Titel muss mindestens 8 Zeichen haben."
        }
        if (kind == IssueKind.FEATURE && title.contains("[Bug]", ignoreCase = true)) {
            problems += "Ein Feature-Request darf nicht als Bug tituliert werden."
        }
        fields(kind).forEach { field ->
            val value = values[field].orEmpty().trim()
            if (value.isEmpty()) {
                problems += "„${field.label}“ ist ein Pflichtfeld."
            } else if (value.length < field.minLength) {
                problems += "„${field.label}“ braucht mindestens ${field.minLength} Zeichen."
            }
        }
        return problems
    }

    fun buildTitle(kind: IssueKind, title: String): String =
        kind.prefix + title.trim()

    fun buildBody(
        context: Context,
        kind: IssueKind,
        values: Map<IssueField, String>,
        includeDiagnostics: Boolean = true,
        includeLogs: Boolean = true,
        includeSettings: Boolean = false,
        crashReport: String? = null,
    ): String = buildString {
        appendLine("### ${kind.label}")
        appendLine()
        appendLine("_Erstellt mit dem integrierten Issue-Tracker von MTZ Tool._")
        appendLine()

        fields(kind).forEach { field ->
            val value = values[field].orEmpty().trim()
            if (value.isEmpty()) return@forEach
            appendLine("### ${field.label}")
            appendLine(value)
            appendLine()
        }

        if (kind != IssueKind.FEATURE) {
            appendLine("### Checkliste")
            appendLine("- [x] Ich habe nach einem bestehenden Issue gesucht")
            appendLine("- [x] Das Problem lässt sich mit der aktuellen Version reproduzieren")
            appendLine()
        }

        if (crashReport != null) {
            appendLine("### Stacktrace")
            appendLine("```")
            appendLine(crashReport)
            appendLine("```")
            appendLine()
        }

        if (includeDiagnostics) {
            appendLine(diagnostics(context))
            appendLine()
        }
        if (includeSettings) {
            appendLine("### Einstellungen")
            appendLine("```json")
            appendLine(AppSettings.exportJson(context))
            appendLine("```")
            appendLine()
        }
        if (includeLogs) {
            appendLine("### Log")
            appendLine("```")
            appendLine(AppLog.asText().ifBlank { "(keine Einträge)" })
            appendLine("```")
        }
    }

    fun diagnostics(context: Context): String {
        val profile = DeviceProfiles.detect(context)
        val theme = ThemeSession.current
        return buildString {
            appendLine("### Umgebung")
            appendLine("- App: ${UpdateChecker.currentVersionName(context)} (${UpdateChecker.currentVersionCode(context)})")
            appendLine("- Paket: ${context.packageName}")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Gerät: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Formfaktor: ${profile.description} (erkannt: ${profile.detectedClass.label})")
            appendLine("- Build: ${Build.DISPLAY}")
            appendLine("- Zugriffsmethode: ${AppSettings.getPrivilegeProvider(context).name}")
            appendLine("- Shizuku: ${ShizukuHelper.getStatus().name}")
            appendLine("- Theme-Familie: ${AppSettings.getThemeFamily(context).name}")
            appendLine("- Darstellung: ${AppSettings.getThemeMode(context).name}")
            appendLine("- Update-Kanal: ${AppSettings.getUpdateChannel(context).name}")
            appendLine(
                "- Workspace: " + (
                    theme?.let { "${it.name} (${it.size} Bytes)" } ?: "leer"
                    )
            )
        }
    }
}
