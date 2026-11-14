package at.libmatti.mtztool.github

import android.content.Context
import at.libmatti.mtztool.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal GitHub REST client used by the in-app issue tracker and the
 * repository screens. It talks to the public API with the user's OAuth token,
 * so issues, PRs and notifications are created and read without leaving the
 * app.
 */
object GitHubApi {

    private const val BASE = "https://api.github.com"
    private const val USER_AGENT = "MTZTool-Android/2.0"
    private const val API_VERSION = "2022-11-28"

    data class User(
        val login: String,
        val name: String,
        val avatarUrl: String,
        val htmlUrl: String,
    )

    data class Repo(
        val fullName: String,
        val description: String,
        val privateRepo: Boolean,
        val stars: Int,
        val openIssues: Int,
        val defaultBranch: String,
    )

    data class Issue(
        val number: Int,
        val title: String,
        val state: String,
        val htmlUrl: String,
        val author: String,
        val labels: List<String>,
        val assignees: List<String>,
        val comments: Int,
        val updatedAt: String,
        val body: String,
    )

    data class PullRequest(
        val number: Int,
        val title: String,
        val state: String,
        val htmlUrl: String,
        val author: String,
        val head: String,
        val base: String,
        val draft: Boolean,
    )

    data class Notification(
        val id: String,
        val title: String,
        val reason: String,
        val repo: String,
        val url: String,
        val unread: Boolean,
    )

    data class WorkflowRun(
        val name: String,
        val branch: String,
        val status: String,
        val conclusion: String,
        val htmlUrl: String,
    )

    data class Label(val name: String, val color: String, val description: String)

    data class Workflow(
        val id: Long,
        val name: String,
        val path: String,
        val state: String,
    )

    private data class HttpResult(val code: Int, val body: String)

    fun isSignedIn(context: Context): Boolean = AppSettings.getGithubToken(context).isNotBlank()

    fun configuredRepo(context: Context): String = AppSettings.getGithubRepo(context)

    fun repoOwner(context: Context): String = configuredRepo(context).substringBefore('/')

    /** The repository owner sees every issue, PR, notification and action. */
    fun isOwner(context: Context): Boolean =
        AppSettings.getGithubLogin(context).equals(repoOwner(context), ignoreCase = true)

    // ── Account ───────────────────────────────────────────────

    suspend fun user(context: Context): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(requireOk(http("GET", "$BASE/user", token(context))))
            User(
                login = json.optString("login"),
                name = json.optString("name"),
                avatarUrl = json.optString("avatar_url"),
                htmlUrl = json.optString("html_url"),
            )
        }
    }

    suspend fun repositories(context: Context, limit: Int = 50): Result<List<Repo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = requireOk(
                    http(
                        "GET",
                        "$BASE/user/repos?per_page=${limit.coerceIn(1, 100)}&sort=updated",
                        token(context),
                    )
                )
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { json ->
                        Repo(
                            fullName = json.optString("full_name"),
                            description = json.optString("description"),
                            privateRepo = json.optBoolean("private"),
                            stars = json.optInt("stargazers_count"),
                            openIssues = json.optInt("open_issues_count"),
                            defaultBranch = json.optString("default_branch", "main"),
                        )
                    }
                }
            }
        }

    // ── Issues ────────────────────────────────────────────────

    suspend fun issues(
        context: Context,
        repo: String = configuredRepo(context),
        state: String = "open",
        label: String? = null,
        limit: Int = 40,
    ): Result<List<Issue>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildString {
                append("?state=").append(enc(state))
                append("&per_page=").append(limit.coerceIn(1, 100))
                append("&sort=updated")
                if (!label.isNullOrBlank()) append("&labels=").append(enc(label))
            }
            val array = JSONArray(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/issues$query",
                        token(context),
                    )
                )
            )
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                if (json.has("pull_request")) null else parseIssue(json)
            }
        }
    }

    /** Issues created by the signed-in user across all repositories. */
    suspend fun myIssues(context: Context, limit: Int = 40): Result<List<Issue>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray(
                    requireOk(
                        http(
                            "GET",
                            "$BASE/issues?filter=created&state=all&per_page=${limit.coerceIn(1, 100)}",
                            token(context),
                        )
                    )
                )
                (0 until array.length()).mapNotNull { index ->
                    val json = array.optJSONObject(index) ?: return@mapNotNull null
                    if (json.has("pull_request")) null else parseIssue(json)
                }
            }
        }

    suspend fun issue(context: Context, repo: String, number: Int): Result<Issue> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseIssue(
                    JSONObject(
                        requireOk(
                            http(
                                "GET",
                                "$BASE/repos/${cleanRepo(repo)}/issues/$number",
                                token(context),
                            )
                        )
                    )
                )
            }
        }

    suspend fun createIssue(
        context: Context,
        repo: String = configuredRepo(context),
        title: String,
        body: String,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ): Result<Issue> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("title", title)
                put("body", body)
                put("labels", JSONArray(labels))
                if (assignees.isNotEmpty()) put("assignees", JSONArray(assignees))
            }
            val json = JSONObject(
                requireOk(
                    http(
                        "POST",
                        "$BASE/repos/${cleanRepo(repo)}/issues",
                        token(context),
                        payload.toString(),
                    )
                )
            )
            parseIssue(json)
        }
    }

    suspend fun comment(
        context: Context,
        repo: String,
        number: Int,
        body: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("body", body)
            requireOk(
                http(
                    "POST",
                    "$BASE/repos/${cleanRepo(repo)}/issues/$number/comments",
                    token(context),
                    payload.toString(),
                )
            )
            Unit
        }
    }

    suspend fun setIssueState(
        context: Context,
        repo: String,
        number: Int,
        state: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireOk(
                http(
                    "PATCH",
                    "$BASE/repos/${cleanRepo(repo)}/issues/$number",
                    token(context),
                    JSONObject().put("state", state).toString(),
                )
            )
            Unit
        }
    }

    suspend fun labels(context: Context, repo: String = configuredRepo(context)): Result<List<Label>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray(
                    requireOk(
                        http("GET", "$BASE/repos/${cleanRepo(repo)}/labels?per_page=100", token(context))
                    )
                )
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let {
                        Label(
                            name = it.optString("name"),
                            color = it.optString("color"),
                            description = it.optString("description"),
                        )
                    }
                }
            }
        }

    /** Updates labels, assignees and/or state in one PATCH request. */
    suspend fun updateIssue(
        context: Context,
        repo: String = configuredRepo(context),
        number: Int,
        labels: List<String>? = null,
        assignees: List<String>? = null,
        state: String? = null,
    ): Result<Issue> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                labels?.let { put("labels", JSONArray(it)) }
                assignees?.let { put("assignees", JSONArray(it)) }
                state?.let { put("state", it) }
            }
            val text = requireOk(
                http(
                    "PATCH",
                    "$BASE/repos/${cleanRepo(repo)}/issues/$number",
                    token(context),
                    payload.toString(),
                )
            )
            parseIssue(JSONObject(text))
        }
    }

    /** People who may be assigned to an issue in this repository. */
    suspend fun assignableUsers(
        context: Context,
        repo: String = configuredRepo(context),
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/assignees?per_page=100",
                        token(context),
                    )
                )
            )
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.optString("login")?.takeIf { it.isNotBlank() }
            }
        }
    }

    suspend fun workflows(
        context: Context,
        repo: String = configuredRepo(context),
    ): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/actions/workflows?per_page=100",
                        token(context),
                    )
                )
            )
            val array = json.optJSONArray("workflows") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val workflow = array.optJSONObject(index) ?: return@mapNotNull null
                Workflow(
                    id = workflow.optLong("id"),
                    name = workflow.optString("name"),
                    path = workflow.optString("path"),
                    state = workflow.optString("state"),
                )
            }
        }
    }

    /** Triggers workflow_dispatch with arbitrary inputs. */
    suspend fun dispatchWorkflow(
        context: Context,
        repo: String = configuredRepo(context),
        workflowId: Long,
        ref: String = "main",
        inputs: Map<String, String> = emptyMap(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("ref", ref.ifBlank { "main" })
                if (inputs.isNotEmpty()) {
                    put("inputs", JSONObject().apply {
                        inputs.forEach { (key, value) -> put(key, value) }
                    })
                }
            }
            requireOk(
                http(
                    "POST",
                    "$BASE/repos/${cleanRepo(repo)}/actions/workflows/$workflowId/dispatches",
                    token(context),
                    payload.toString(),
                )
            )
            Unit
        }
    }

    // ── Pull requests ─────────────────────────────────────────

    suspend fun pullRequests(
        context: Context,
        repo: String = configuredRepo(context),
        state: String = "open",
    ): Result<List<PullRequest>> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/pulls?state=${enc(state)}&per_page=40",
                        token(context),
                    )
                )
            )
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                PullRequest(
                    number = json.optInt("number"),
                    title = json.optString("title"),
                    state = json.optString("state"),
                    htmlUrl = json.optString("html_url"),
                    author = json.optJSONObject("user")?.optString("login").orEmpty(),
                    head = json.optJSONObject("head")?.optString("label").orEmpty(),
                    base = json.optJSONObject("base")?.optString("label").orEmpty(),
                    draft = json.optBoolean("draft"),
                )
            }
        }
    }

    suspend fun createPullRequest(
        context: Context,
        repo: String = configuredRepo(context),
        title: String,
        head: String,
        base: String,
        body: String,
        draft: Boolean = true,
    ): Result<PullRequest> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("title", title)
                put("head", head)
                put("base", base)
                put("body", body)
                put("draft", draft)
            }
            val json = JSONObject(
                requireOk(
                    http(
                        "POST",
                        "$BASE/repos/${cleanRepo(repo)}/pulls",
                        token(context),
                        payload.toString(),
                    )
                )
            )
            PullRequest(
                number = json.optInt("number"),
                title = json.optString("title"),
                state = json.optString("state"),
                htmlUrl = json.optString("html_url"),
                author = json.optJSONObject("user")?.optString("login").orEmpty(),
                head = json.optJSONObject("head")?.optString("label").orEmpty(),
                base = json.optJSONObject("base")?.optString("label").orEmpty(),
                draft = json.optBoolean("draft"),
            )
        }
    }

    // ── Notifications & Actions ───────────────────────────────

    suspend fun notifications(context: Context): Result<List<Notification>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray(
                    requireOk(
                        http(
                            "GET",
                            "$BASE/notifications?all=false&per_page=30",
                            token(context),
                            accept = "application/vnd.github+json",
                        )
                    )
                )
                (0 until array.length()).mapNotNull { index ->
                    val json = array.optJSONObject(index) ?: return@mapNotNull null
                    val subject = json.optJSONObject("subject")
                    Notification(
                        id = json.optString("id"),
                        title = subject?.optString("title").orEmpty(),
                        reason = json.optString("reason"),
                        repo = json.optJSONObject("repository")?.optString("full_name").orEmpty(),
                        url = subject?.optString("latest_comment_url")
                            .takeUnless { it.isNullOrBlank() }
                            ?: subject?.optString("url").orEmpty(),
                        unread = json.optBoolean("unread"),
                    )
                }
            }
        }

    suspend fun workflowRuns(
        context: Context,
        repo: String = configuredRepo(context),
    ): Result<List<WorkflowRun>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/actions/runs?per_page=10",
                        token(context),
                    )
                )
            )
            val runs = json.optJSONArray("workflow_runs") ?: JSONArray()
            (0 until runs.length()).mapNotNull { index ->
                val run = runs.optJSONObject(index) ?: return@mapNotNull null
                WorkflowRun(
                    name = run.optString("name"),
                    branch = run.optString("head_branch"),
                    status = run.optString("status"),
                    conclusion = run.optString("conclusion"),
                    htmlUrl = run.optString("html_url"),
                )
            }
        }
    }

    // ── Repository contents ───────────────────────────────────

    data class DirEntry(
        val name: String,
        val path: String,
        val downloadUrl: String,
        val size: Long,
        val sha: String,
        val type: String,
    )

    data class FileContent(
        val bytes: ByteArray,
        val sha: String,
        val size: Long,
    )

    suspend fun directory(
        context: Context,
        path: String,
        repo: String = configuredRepo(context),
    ): Result<List<DirEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/contents/${path.trim('/')}",
                        token(context),
                    )
                )
            )
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                DirEntry(
                    name = json.optString("name"),
                    path = json.optString("path"),
                    downloadUrl = json.optString("download_url"),
                    size = json.optLong("size"),
                    sha = json.optString("sha"),
                    type = json.optString("type"),
                )
            }
        }
    }

    /** Reads one file (optionally from another branch) with its blob SHA. */
    suspend fun readFile(
        context: Context,
        path: String,
        ref: String? = null,
        repo: String = configuredRepo(context),
    ): Result<FileContent> = withContext(Dispatchers.IO) {
        runCatching {
            val suffix = if (ref.isNullOrBlank()) "" else "?ref=" + enc(ref)
            val json = JSONObject(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/contents/${path.trim('/')}$suffix",
                        token(context),
                    )
                )
            )
            val encoded = json.optString("content").replace("\n", "")
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            FileContent(
                bytes = bytes,
                sha = json.optString("sha"),
                size = json.optLong("size", bytes.size.toLong()),
            )
        }
    }

    suspend fun branchExists(
        context: Context,
        branch: String,
        repo: String = configuredRepo(context),
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val result = http(
                "GET",
                "$BASE/repos/${cleanRepo(repo)}/git/ref/heads/${enc(branch)}",
                token(context),
            )
            result.code in 200..299
        }
    }

    suspend fun createBranch(
        context: Context,
        branch: String,
        from: String = "main",
        repo: String = configuredRepo(context),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = JSONObject(
                requireOk(
                    http(
                        "GET",
                        "$BASE/repos/${cleanRepo(repo)}/git/ref/heads/${enc(from)}",
                        token(context),
                    )
                )
            ).getJSONObject("object").getString("sha")
            val payload = JSONObject().apply {
                put("ref", "refs/heads/$branch")
                put("sha", base)
            }
            requireOk(
                http(
                    "POST",
                    "$BASE/repos/${cleanRepo(repo)}/git/refs",
                    token(context),
                    payload.toString(),
                )
            )
            Unit
        }
    }

    /**
     * Commits a file on a branch. The contents API does not create a missing
     * branch by itself (it returns 404 for an unknown ref), so a new branch is
     * created from the default branch first. Pass [sha] to update an existing
     * file on a sync branch.
     */
    suspend fun uploadFile(
        context: Context,
        path: String,
        bytes: ByteArray,
        branch: String,
        message: String,
        sha: String? = null,
        repo: String = configuredRepo(context),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (sha.isNullOrBlank() && !branchExists(context, branch, repo).getOrThrow()) {
                createBranch(context, branch, repo = repo).getOrThrow()
            }
            val payload = JSONObject().apply {
                put("message", message)
                put("content", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                put("branch", branch)
                if (!sha.isNullOrBlank()) put("sha", sha)
            }
            requireOk(
                http(
                    "PUT",
                    "$BASE/repos/${cleanRepo(repo)}/contents/${path.trim('/')}",
                    token(context),
                    payload.toString(),
                )
            )
            Unit
        }
    }

    // ── Internals ─────────────────────────────────────────────

    private fun token(context: Context): String? =
        AppSettings.getGithubToken(context).takeIf { it.isNotBlank() }

    private fun cleanRepo(repo: String): String = repo
        .trim()
        .removePrefix("https://github.com/")
        .removeSuffix(".git")
        .trim('/')

    private fun parseIssue(json: JSONObject): Issue {
        val labelsArray = json.optJSONArray("labels") ?: JSONArray()
        val labels = (0 until labelsArray.length()).mapNotNull { index ->
            labelsArray.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
        }
        val assigneesArray = json.optJSONArray("assignees") ?: JSONArray()
        val assignees = (0 until assigneesArray.length()).mapNotNull { index ->
            assigneesArray.optJSONObject(index)?.optString("login")?.takeIf { it.isNotBlank() }
        }
        return Issue(
            number = json.optInt("number"),
            title = json.optString("title"),
            state = json.optString("state"),
            htmlUrl = json.optString("html_url"),
            author = json.optJSONObject("user")?.optString("login").orEmpty(),
            labels = labels,
            assignees = assignees,
            comments = json.optInt("comments"),
            updatedAt = json.optString("updated_at"),
            body = json.optString("body"),
        )
    }

    private fun requireOk(result: HttpResult): String {
        if (result.code in 200..299) return result.body
        val message = runCatching {
            JSONObject(result.body).optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val hint = when (result.code) {
            401 -> "Anmeldung abgelaufen. Bitte erneut mit GitHub anmelden."
            403 -> "Keine Berechtigung oder API-Limit erreicht."
            404 -> buildString {
                append("Repository, Branch oder Issue nicht gefunden.")
                if (message != null) append(" GitHub: ").append(message)
            }
            422 -> message ?: "Die Anfrage wurde von GitHub abgelehnt."
            else -> message ?: "GitHub HTTP ${result.code}"
        }
        throw IOException(hint)
    }

    private fun http(
        method: String,
        url: String,
        token: String?,
        body: String? = null,
        accept: String = "application/vnd.github+json",
    ): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResult(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
