package at.libmatti.mtztool

import android.util.Log

/**
 * Decides whether a theme may be imported in full or only in the reduced
 * (rights exempt) variant.
 *
 * A theme counts as "full" when every protected component is covered by at
 * least one .mra with a valid Xiaomi signature. The Theme Manager itself
 * checks each component hash against the files in rights/theme, so the union
 * of all valid asset lists is authoritative.
 */
object ThemeRights {

    data class VerifiedMra(
        val file: ThemeSourceReader.MraFile,
        val info: MraVerifier.Rights,
    )

    data class Assessment(
        val fullTheme: Boolean,
        val validMras: List<VerifiedMra>,
        val missing: List<ThemeSourceReader.Component>,
        val protectedComponents: List<ThemeSourceReader.Component>,
        val reason: String,
    )

    fun assess(candidate: ThemeSourceReader.Candidate): Assessment {
        val verified = candidate.mras.map {
            val info = MraVerifier.verify(it.data)
            Log.i(
                "ThemeRights",
                "mra=${it.name} valid=${info.signatureValid} themeHash=${info.themeHash} " +
                    "assets=${info.assets.size} error=${info.error}"
            )
            VerifiedMra(it, info)
        }
        val valid = verified.filter { it.info.signatureValid }
        val coveredHashes = valid.flatMap { it.info.assets }.toSet()

        val protectedComponents = candidate.components.filterNot {
            ThemeComponents.isExempt(it.code)
        }
        val missing = protectedComponents.filter { it.sha1 !in coveredHashes }
        val fullTheme = missing.isEmpty()

        val reason = when {
            fullTheme -> ""
            candidate.mras.isEmpty() ->
                "Im Theme ist keine Rechte-Datei (.mra) enthalten."
            valid.isEmpty() ->
                "Die enthaltene .mra-Datei hat keine gültige Xiaomi-Signatur."
            else ->
                "Die gültige .mra deckt ${missing.size} von ${protectedComponents.size} " +
                    "geschützten Bestandteilen nicht ab."
        }

        return Assessment(
            fullTheme = fullTheme,
            validMras = valid,
            missing = missing,
            protectedComponents = protectedComponents,
            reason = reason,
        )
    }
}
