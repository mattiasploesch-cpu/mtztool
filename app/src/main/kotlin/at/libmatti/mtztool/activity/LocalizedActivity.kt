package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Activity that applies the language selected in the settings.
 *
 * Recreates itself when the language changes while it is visible, so returning
 * from the settings page always shows the newly selected language.
 */
open class LocalizedActivity : ComponentActivity() {

    private var appliedLanguage: String? = null

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppSettings.getLanguage(newBase)
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (AppSettings.getLanguage(this) != appliedLanguage) {
            recreate()
        }
    }
}
