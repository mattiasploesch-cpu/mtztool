package at.libmatti.mtztool.activity

import android.content.Context
import android.content.Intent
import at.libmatti.mtztool.UpdatePatch
import org.json.JSONArray
import org.json.JSONObject

object PatcherLauncher {

    const val EXTRA_PATCHES_JSON = "patches_json"

    fun start(context: Context, patches: List<UpdatePatch>, versionName: String) {
        if (patches.isEmpty()) return
        val array = JSONArray()
        patches.forEach { patch ->
            array.put(
                JSONObject().apply {
                    put("fromVersionCode", patch.fromVersionCode)
                    put("toVersionCode", patch.toVersionCode)
                    put("fromVersionName", patch.fromVersionName)
                    put("toVersionName", patch.toVersionName)
                    put("url", patch.url)
                    put("size", patch.size)
                    put("sha256", patch.sha256 ?: JSONObject.NULL)
                },
            )
        }
        val first = patches.first()
        context.startActivity(
            Intent(context, AppPatcherActivity::class.java).apply {
                putExtra(AppPatcherActivity.EXTRA_VERSION_NAME, versionName)
                putExtra(AppPatcherActivity.EXTRA_FROM_NAME, first.fromVersionName)
                putExtra(AppPatcherActivity.EXTRA_PATCH_URL, first.url)
                putExtra(AppPatcherActivity.EXTRA_PATCH_SHA, first.sha256)
                putExtra(AppPatcherActivity.EXTRA_PATCH_SIZE, first.size)
                putExtra(AppPatcherActivity.EXTRA_FROM_CODE, first.fromVersionCode)
                putExtra(AppPatcherActivity.EXTRA_TO_CODE, patches.last().toVersionCode)
                putExtra(EXTRA_PATCHES_JSON, array.toString())
            },
        )
    }
}
