package at.libmatti.mtztool

/**
 * Privileged access methods supported (or planned) by the app.
 *
 * The selection is persisted in [AppSettings] so the rest of the app can
 * decide which backend to use.
 */
enum class PrivilegeProvider(val titleRes: Int, val descriptionRes: Int) {
    ADB(
        R.string.provider_adb,
        R.string.provider_adb_desc
    ),
    SHIZUKU(
        R.string.provider_shizuku,
        R.string.provider_shizuku_desc
    ),
    DHIZUKU(
        R.string.provider_dhizuku,
        R.string.provider_dhizuku_desc
    ),
    ROOT(
        R.string.provider_root,
        R.string.provider_root_desc
    );

    companion object {
        fun fromStorage(value: String?): PrivilegeProvider =
            PrivilegeProvider.entries.firstOrNull { it.name == value } ?: SHIZUKU
    }
}
