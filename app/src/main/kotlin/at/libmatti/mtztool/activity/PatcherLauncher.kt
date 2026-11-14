package at.libmatti.mtztool.activity

import android.content.Context
import android.content.Intent
import at.libmatti.mtztool.UpdatePatch

object PatcherLauncher {

    fun start(context: Context, patch: UpdatePatch, versionName: String) {
        context.startActivity(
            Intent(context, AppPatcherActivity::class.java).apply {
                putExtra(AppPatcherActivity.EXTRA_VERSION_NAME, versionName)
                putExtra(AppPatcherActivity.EXTRA_FROM_NAME, patch.fromVersionName)
                putExtra(AppPatcherActivity.EXTRA_PATCH_URL, patch.url)
                putExtra(AppPatcherActivity.EXTRA_PATCH_SHA, patch.sha256)
                putExtra(AppPatcherActivity.EXTRA_PATCH_SIZE, patch.size)
                putExtra(AppPatcherActivity.EXTRA_FROM_CODE, patch.fromVersionCode)
                putExtra(AppPatcherActivity.EXTRA_TO_CODE, patch.toVersionCode)
            }
        )
    }
}
