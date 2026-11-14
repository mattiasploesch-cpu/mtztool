package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Small helpers for theme files. Applying a theme is handled by
 * [RootThemeApplier]; no Theme Manager activity is started from here.
 */
object ThemeApplier {

    /**
     * Resolves a content:// or file:// URI to a display name for the UI.
     */
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }
}
