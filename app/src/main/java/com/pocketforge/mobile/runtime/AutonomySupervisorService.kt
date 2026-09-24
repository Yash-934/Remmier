package com.pocketforge.mobile.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pocketforge.mobile.MainActivity
import com.pocketforge.mobile.R
import com.pocketforge.mobile.data.ApiKeyVault
import com.pocketforge.mobile.data.AppPreferences
import com.pocketforge.mobile.model.AgentKind
import com.pocketforge.mobile.model.AutonomyServiceState
import com.pocketforge.mobile.model.AutonomySnapshot
import com.pocketforge.mobile.model.LocalIncident
import com.pocketforge.mobile.model.ProviderKind
import com.pocketforge.mobile.model.ProviderProfile
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local control-plane supervisor. Paperclip and OpenClaw are child Linux processes
 * inside PocketForge's existing Ubuntu/PRoot runtime; nothing listens outside loopback.
 */
class AutonomySupervisorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var installer: RuntimeInstaller
    private lateinit var store: AutonomyStore
    private var paperclipProcess: Process? = null
    private var openClawProcess: Process? = null
    private var bridge: AutonomyLocalBridge? = null
    private var lifecycleJob: Job? = null
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        installer = RuntimeInstaller(this)
        store = AutonomyStore(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            scope.launch { stopAll("Stopped from PocketForge") }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Starting local autonomy…", true))
        stopRequested = false
        if (lifecycleJob?.isActive != true) lifecycleJob = scope.launch { bootAndSupervise() }
        return START_STICKY
    }

    private suspend fun bootAndSupervise() {
        try {
            update(state = AutonomyServiceState.INSTALLING, progress = .02f, message = "Checking the local Linux runtime…", error = null)
            check(installer.isInstalled()) { "Install the PocketForge Linux runtime first" }
            ensureDirsAndUser()
            ensureInstructions()
            ensurePaperclipPackage()
            ensureOpenClawPackage()

            update(state = AutonomyServiceState.STARTING, progress = .42f, message = "Starting Paperclip…")
            startPaperclip()
            waitForPort(PAPERCLIP_PORT, 60_000)

            update(progress = .62f, message = "Configuring OpenClaw locally…")
            configureOpenClaw()
            startOpenClaw()
            waitForPort(OPENCLAW_PORT, 60_000)

            update(progress = .82f, message = "Provisioning local Coder and Tester…")
            provisionPaperclipOrg()
            startBridge()

            update(
                state = AutonomyServiceState.RUNNING,
                progress = 1f,
                message = "Paperclip + OpenClaw are running locally",
                paperclipRunning = true,
                openClawRunning = true,
                error = null,
            )
            updateNotification("Local autonomy is running", true)
            monitorLoop()
        } catch (t: Throwable) {
            val msg = (t.message ?: t::class.java.simpleName).takeLast(1200)
            update(state = AutonomyServiceState.FAILED, message = "Autonomy startup failed", error = msg)
            updateNotification("Autonomy failed: $msg", false)
            stopAll("Startup failure", finalState = AutonomyServiceState.FAILED, preserveError = true)
        }
    }

    private suspend fun monitorLoop() {
        while (scope.isActive && !stopRequested) {
            val paperAlive = paperclipProcess?.isAlive == true
            val clawAlive = openClawProcess?.isAlive == true
            if (paperAlive && clawAlive) {
                update(state = AutonomyServiceState.RUNNING, message = "Paperclip and OpenClaw are healthy", paperclipRunning = true, openClawRunning = true, error = null)
            } else {
                update(
                    state = AutonomyServiceState.DEGRADED,
                    message = when {
                        paperAlive -> "Paperclip is running; restarting OpenClaw…"
                        clawAlive -> "OpenClaw is running; restarting Paperclip…"
                        else -> "Autonomy services stopped; recovering…"
                    },
                    paperclipRunning = paperAlive,
                    openClawRunning = clawAlive,
                )
                if (!stopRequested) {
                    runCatching { if (!paperAlive) startPaperclip() }.onFailure { update(error = it.message.orEmpty()) }
                    delay(750)
                    runCatching { if (!clawAlive) startOpenClaw() }.onFailure { update(error = it.message.orEmpty()) }
                }
            }
            delay(3_000)
        }
    }

    private suspend fun ensureDirsAndUser() = withContext(Dispatchers.IO) {
        store.root.mkdirs(); store.logs.mkdirs(); store.paperclipHome.mkdirs(); store.openClawState.mkdirs(); store.workspace.mkdirs()
        val command = """
            set -e
            if ! id -u pocketforge >/dev/null 2>&1 && command -v useradd >/dev/null 2>&1; then
              useradd --create-home --shell /bin/bash pocketforge || true
            fi
            mkdir -p /pocket-autonomy/paperclip /pocket-autonomy/openclaw /workspace/pocketforge-autonomy
            chmod -R u+rwX /pocket-autonomy /workspace/pocketforge-autonomy || true
            if id -u pocketforge >/dev/null 2>&1; then chown -R pocketforge:pocketforge /pocket-autonomy || true; fi
        """.trimIndent()
        runGuest(listOf("/usr/bin/env", "bash", "-lc", command), "bootstrap-user.log").requireSuccess("Could not prepare autonomy directories")
    }

    private fun ensureInstructions() {
        val dir = File(store.workspace, ".pocketforge").apply { mkdirs() }
        File(dir, "CODER.md").writeText(
            """# PocketForge Autonomous Coder

You are the local Coder employee. Work only in the assigned repository/workspace.

1. Inspect and reproduce the assigned issue.
2. Implement the smallest correct fix and preserve unrelated work.
3. Run focused tests and review `git diff`.
4. Update the Paperclip issue with durable progress.
5. When implementation is ready for validation, assign the same issue to the **Tester** employee and leave it in the appropriate test/review state. Do not deploy production code.
6. Never modify credentials, broker/account settings, or unrelated projects.
""".trimIndent(),
        )
        File(dir, "TESTER.md").writeText(
            """# PocketForge Autonomous Tester

You are the local Tester employee.

1. Run deterministic repository tests for the assigned issue.
2. Record exact pass/fail evidence in the Paperclip issue.
3. If a code defect is exposed, return the issue to Coder with concrete failure details.
4. Do not deploy, modify credentials, or change unrelated files.
""".trimIndent(),
        )
    }

    private suspend fun ensurePaperclipPackage() {
        if (isGuestCommandAvailable("paperclipai")) {
            update(progress = .18f, message = "Paperclip is already installed", paperclipInstalled = true)
            return
        }
        update(progress = .08f, message = "Installing Paperclip ${PAPERCLIP_VERSION} locally…")
        runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", "npm install --global paperclipai@$PAPERCLIP_VERSION"),
            outputName = "paperclip-install.log",
        ).requireSuccess("Paperclip installation failed")
        check(isGuestCommandAvailable("paperclipai")) { "Paperclip CLI was not found after installation" }
        update(progress = .24f, message = "Paperclip installed", paperclipInstalled = true)
    }

    private suspend fun ensureOpenClawPackage() {
        if (isGuestCommandAvailable("openclaw")) {
            update(progress = .30f, message = "OpenClaw is already installed", openClawInstalled = true)
            return
        }
        update(progress = .25f, message = "Installing OpenClaw $OPENCLAW_VERSION locally…")
        runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", "npm install --global openclaw@$OPENCLAW_VERSION --allow-scripts=openclaw || npm install --global openclaw@$OPENCLAW_VERSION"),
            outputName = "openclaw-install.log",
        ).requireSuccess("OpenClaw installation failed")
        check(isGuestCommandAvailable("openclaw")) { "OpenClaw CLI was not found after installation" }
        update(progress = .36f, message = "OpenClaw installed", openClawInstalled = true)
    }

    private fun currentProvider(): Pair<ProviderProfile, String?> {
        val vault = ApiKeyVault(this)
        val profile = AppPreferences(this).loadProvider(vault, AgentKind.CLAUDE_CODE)
        return profile to vault.get(profile.kind.name)
    }

    private fun paperclipProviderEnv(): Map<String, String> {
        val (profile, secret) = currentProvider()
        if (secret.isNullOrBlank()) return emptyMap()
        val env = linkedMapOf<String, String>()
        when (profile.kind) {
            ProviderKind.CLAUDE -> env["CLAUDE_CODE_OAUTH_TOKEN"] = secret
            else -> {
                env["ANTHROPIC_API_KEY"] = secret
                env["ANTHROPIC_BASE_URL"] = profile.resolvedBaseUrl
                if (profile.model.isNotBlank()) env["ANTHROPIC_MODEL"] = profile.model
                if (profile.kind == ProviderKind.LLM_ROUTER) env["OPENROUTER_API_KEY"] = secret
            }
        }
        return env
    }

    private suspend fun configureOpenClaw() {
        val config = store.readConfig()
        val token = config.optString("openClawToken").ifBlank {
            val bytes = ByteArray(24); SecureRandom().nextBytes(bytes); bytes.joinToString("") { "%02x".format(it) }
        }
        config.put("openClawToken", token)
        store.writeConfig(config)
        if (File(store.openClawState, "openclaw.json").isFile) return

        val (profile, secret) = currentProvider()
        val env = linkedMapOf(
            "OPENCLAW_GATEWAY_TOKEN" to token,
            "OPENCLAW_STATE_DIR" to "/pocket-autonomy/openclaw",
            "HOME" to "/root",
        )
        val command = buildString {
            append("openclaw onboard --non-interactive --accept-risk --mode local --skip-skills ")
            append("--gateway-auth token --gateway-token-ref-env OPENCLAW_GATEWAY_TOKEN ")
            append("--gateway-port $OPENCLAW_PORT --gateway-bind loopback --auth-choice ")
            if (!secret.isNullOrBlank() && profile.kind != ProviderKind.CLAUDE) {
                env["CUSTOM_API_KEY"] = secret
                append("custom-api-key --secret-input-mode ref")
                append(" --custom-base-url ").append(shellQuote(profile.resolvedBaseUrl))
                append(" --custom-model-id ").append(shellQuote(profile.model.ifBlank { "default" }))
                append(" --custom-api-key \"\$CUSTOM_API_KEY\"")
                append(" --custom-compatibility ")
                append(if (profile.kind.protocol.name == "OPENAI_RESPONSES") "openai-responses" else if (profile.kind.protocol.name == "OPENAI_CHAT") "openai" else "anthropic")
            } else {
                append("skip")
            }
        }
        val result = runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", command),
            outputName = "openclaw-onboard.log",
            environment = env,
        )
        val exit = result.process.waitFor()
        if (exit != 0) error("OpenClaw onboarding failed (exit $exit): ${result.readOutput().takeLast(1200)}")
    }

    private suspend fun startPaperclip() {
        if (paperclipProcess?.isAlive == true) return
        val providerEnv = paperclipProviderEnv()
        val command = """
            export PAPERCLIP_NO_BROWSER=1
            export PAPERCLIP_OPEN_ON_LISTEN=false
            export HOST=127.0.0.1
            export PORT=$PAPERCLIP_PORT
            exec paperclipai run --bind loopback --data-dir /pocket-autonomy/paperclip
        """.trimIndent()
        paperclipProcess = runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", command),
            outputName = "paperclip.log",
            environment = providerEnv + mapOf(
                "PAPERCLIP_NO_BROWSER" to "1",
                "PAPERCLIP_OPEN_ON_LISTEN" to "false",
                "HOST" to "127.0.0.1",
                "PORT" to PAPERCLIP_PORT.toString(),
                "PAPERCLIP_API_URL" to "http://127.0.0.1:$PAPERCLIP_PORT",
            ),
        ).process
    }

    private suspend fun startOpenClaw() {
        if (openClawProcess?.isAlive == true) return
        val token = store.readConfig().optString("openClawToken").ifBlank { error("OpenClaw token is missing") }
        openClawProcess = runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", "exec openclaw gateway run --port $OPENCLAW_PORT --bind loopback --token \"\$OPENCLAW_GATEWAY_TOKEN\""),
            outputName = "openclaw.log",
            environment = mapOf(
                "OPENCLAW_STATE_DIR" to "/pocket-autonomy/openclaw",
                "HOME" to "/root",
                "OPENCLAW_GATEWAY_TOKEN" to token,
            ),
        ).process
    }

    private suspend fun provisionPaperclipOrg() {
        val config = store.readConfig()
        var companyId = config.optString("companyId").takeIf(String::isNotBlank)
        val listed = runPaperclipCli(listOf("company", "list", "--json"))
        if (listed.exitCode == 0) companyId = parseCompanyId(listed.output) ?: companyId
        if (companyId == null) {
            val payload = JSONObject().put("name", COMPANY_NAME).put("description", "PocketForge local autonomous engineering company")
            val created = runPaperclipCli(listOf("company", "create", "--payload-json", payload.toString(), "--json"))
            created.requireSuccess("Could not create local Paperclip company")
            companyId = parseEntityId(created.output) ?: error("Paperclip company id was not returned")
        }

        val agents = runPaperclipCli(listOf("agent", "list", "--company-id", companyId, "--json"))
        var coderId = if (agents.exitCode == 0) parseAgentIdByName(agents.output, "Coder") else null
        var testerId = if (agents.exitCode == 0) parseAgentIdByName(agents.output, "Tester") else null

        if (coderId == null) {
            val adapter = JSONObject()
                .put("cwd", "/workspace/pocketforge-autonomy")
                .put("instructionsFilePath", "/workspace/pocketforge-autonomy/.pocketforge/CODER.md")
                .put("maxTurnsPerRun", 80)
                .put("dangerouslySkipPermissions", true)
                .put("timeoutSec", 1800)
                .put("graceSec", 20)
            val payload = JSONObject()
                .put("name", "Coder")
                .put("role", "engineer")
                .put("title", "Autonomous Coder")
                .put("icon", "wrench")
                .put("capabilities", "Repository analysis, implementation, refactoring and bug fixing using the local Claude Code runtime.")
                .put("adapterType", "claude_local")
                .put("adapterConfig", adapter)
                .put("runtimeConfig", heartbeatConfig())
            val created = runPaperclipCli(listOf("agent", "create", "--company-id", companyId, "--payload-json", payload.toString(), "--json"))
            created.requireSuccess("Could not create Paperclip Coder")
            coderId = parseEntityId(created.output)
        }

        if (testerId == null) {
            val adapter = JSONObject()
                .put("command", "/bin/bash")
                .put("args", JSONArray().put("-lc").put("if [ -x ./gradlew ]; then ./gradlew test; elif [ -f package.json ]; then npm test -- --runInBand; elif [ -f pyproject.toml ] || [ -f pytest.ini ]; then pytest -q; else echo 'No standard test runner found; configure a project-specific test command in the issue.'; fi"))
                .put("cwd", "/workspace/pocketforge-autonomy")
                .put("instructionsFilePath", "/workspace/pocketforge-autonomy/.pocketforge/TESTER.md")
                .put("timeoutSec", 1800)
                .put("graceSec", 15)
            val payload = JSONObject()
                .put("name", "Tester")
                .put("role", "qa")
                .put("title", "Autonomous Tester")
                .put("icon", "check-circle")
                .put("reportsTo", coderId ?: JSONObject.NULL)
                .put("capabilities", "Runs repository tests and reports deterministic pass/fail evidence to the assigned Paperclip issue.")
                .put("adapterType", "process")
                .put("adapterConfig", adapter)
                .put("runtimeConfig", heartbeatConfig())
            val created = runPaperclipCli(listOf("agent", "create", "--company-id", companyId, "--payload-json", payload.toString(), "--json"))
            created.requireSuccess("Could not create Paperclip Tester")
            testerId = parseEntityId(created.output)
        }

        coderId?.let {
            val permissions = JSONObject().put("canAssignTasks", true).put("canCreateAgents", false).put("canCreateSkills", false)
            runPaperclipCli(listOf("agent", "permissions:update", it, "--payload-json", permissions.toString(), "--json"))
        }
        config.put("companyId", companyId).put("coderAgentId", coderId.orEmpty()).put("testerAgentId", testerId.orEmpty())
        store.writeConfig(config)
        update(companyId = companyId, coderAgentId = coderId, testerAgentId = testerId)
    }

    private fun heartbeatConfig() = JSONObject().put(
        "heartbeat",
        JSONObject().put("enabled", false).put("wakeOnAssignment", true).put("wakeOnOnDemand", true),
    )

    private suspend fun handleIncident(incident: LocalIncident): String = withContext(Dispatchers.IO) {
        val config = store.readConfig()
        val companyId = config.optString("companyId").takeIf(String::isNotBlank) ?: error("Paperclip company is not ready")
        val coderId = config.optString("coderAgentId").takeIf(String::isNotBlank) ?: error("Paperclip Coder is not ready")
        val description = buildString {
            append(incident.description)
            append("\n\n### PocketForge context\n")
            append("Workspace: /workspace/").append(incident.workspace).append('\n')
            incident.testCommand?.let { append("Test command: ").append(it).append('\n') }
            append("Event dedupe key: ").append(incident.dedupeKey)
        }
        val result = runPaperclipCli(
            listOf("issue", "create", "--company-id", companyId, "--title", incident.title, "--description", description, "--status", "todo", "--priority", incident.priority, "--assignee-agent-id", coderId, "--json"),
        )
        result.requireSuccess("Could not create Paperclip incident")
        val issueId = parseEntityId(result.output) ?: result.output.trim().takeLast(500)
        store.writeConfig(config.put("lastIncident", incident.title))
        update(lastIncident = incident.title, message = "Incident assigned to local Coder")
        issueId
    }

    private fun startBridge() {
        bridge?.stop()
        bridge = AutonomyLocalBridge(store.bridgeToken(), { handleIncident(it) }) {
            val s = store.readSnapshot()
            JSONObject().put("state", s.state.name).put("message", s.message).put("paperclipRunning", s.paperclipRunning).put("openClawRunning", s.openClawRunning).put("companyId", s.companyId.orEmpty()).put("coderAgentId", s.coderAgentId.orEmpty()).put("testerAgentId", s.testerAgentId.orEmpty())
        }.also { it.start() }
    }

    private data class CliResult(val exitCode: Int, val output: String) {
        fun requireSuccess(message: String) { if (exitCode != 0) error("$message (exit $exitCode): ${output.takeLast(1200)}") }
    }

    private suspend fun runPaperclipCli(args: List<String>): CliResult {
        val env = mapOf("PAPERCLIP_API_URL" to "http://127.0.0.1:$PAPERCLIP_PORT", "PAPERCLIP_NO_BROWSER" to "1")
        val execution = runGuest(
            command = listOf("/usr/bin/env", "bash", "-lc", "paperclipai ${args.joinToString(" ") { shellQuote(it) }} --data-dir /pocket-autonomy/paperclip"),
            outputName = "cli-${System.nanoTime()}.log",
            environment = env,
        )
        val exit = execution.process.waitFor()
        return CliResult(exit, execution.readOutput())
    }

    private suspend fun isGuestCommandAvailable(command: String): Boolean = runGuest(
        command = listOf("/usr/bin/env", "bash", "-lc", "command -v ${shellQuote(command)} >/dev/null 2>&1"),
        outputName = "which-$command.log",
    ).process.waitFor() == 0

    private suspend fun waitForPort(port: Int, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { Socket("127.0.0.1", port).use { true } }.getOrDefault(false)) return
            delay(250)
        }
        val log = when (port) { PAPERCLIP_PORT -> File(store.logs, "paperclip.log"); OPENCLAW_PORT -> File(store.logs, "openclaw.log"); else -> null }
        error("Local service on port $port did not become ready${log?.let { ": ${tail(it)}" }.orEmpty()}")
    }

    private suspend fun runGuest(
        command: List<String>,
        outputName: String,
        environment: Map<String, String> = emptyMap(),
    ): GuestExecution {
        val runtime = installer.installedRuntime()
        val workspaceRoot = File(filesDir, "workspaces").apply { mkdirs() }
        val output = File(store.logs, outputName).apply { parentFile?.mkdirs() }
        val process = installer.process(
            runtime.proot,
            runtime.rootfs,
            workspaceRoot,
            environment = environment,
            guestCommand = command,
            guestWorkspacePath = "/workspace",
            outputFile = output,
            extraBinds = listOf(store.root to "/pocket-autonomy"),
        )
        return GuestExecution(process, output)
    }

    private data class GuestExecution(val process: Process, val output: File) {
        fun readOutput(): String = tail(output)
        fun requireSuccess(message: String) {
            val exit = process.waitFor()
            if (exit != 0) error("$message (exit $exit): ${readOutput().takeLast(1200)}")
        }
    }

    private fun parseCompanyId(output: String): String? {
        val json = parseJson(output) ?: return null
        val array = when {
            json is JSONArray -> json
            json is JSONObject && json.opt("companies") is JSONArray -> json.optJSONArray("companies")!!
            json is JSONObject && json.opt("data") is JSONArray -> json.optJSONArray("data")!!
            else -> JSONArray().put(json)
        }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("name") == COMPANY_NAME) return item.optString("id").takeIf(String::isNotBlank)
        }
        return null
    }

    private fun parseAgentIdByName(output: String, name: String): String? {
        val json = parseJson(output) ?: return null
        val array = when {
            json is JSONArray -> json
            json is JSONObject && json.opt("agents") is JSONArray -> json.optJSONArray("agents")!!
            json is JSONObject && json.opt("data") is JSONArray -> json.optJSONArray("data")!!
            else -> JSONArray().put(json)
        }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("name") == name) return item.optString("id").takeIf(String::isNotBlank)
        }
        return null
    }

    private fun parseEntityId(output: String): String? {
        val json = parseJson(output) ?: return null
        if (json is JSONObject) {
            json.optString("id").takeIf(String::isNotBlank)?.let { return it }
            listOf("company", "agent", "issue").forEach { key ->
                when (val value = json.opt(key)) {
                    is JSONObject -> value.optString("id").takeIf(String::isNotBlank)?.let { return it }
                    is String -> value.takeIf(String::isNotBlank)?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseJson(output: String): Any? {
        output.lineSequence().map(String::trim).filter(String::isNotBlank).toList().asReversed().forEach { line ->
            try { return JSONObject(line) } catch (_: Throwable) { }
            try { return JSONArray(line) } catch (_: Throwable) { }
        }
        return null
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun update(
        state: AutonomyServiceState? = null,
        progress: Float? = null,
        message: String? = null,
        paperclipInstalled: Boolean? = null,
        openClawInstalled: Boolean? = null,
        paperclipRunning: Boolean? = null,
        openClawRunning: Boolean? = null,
        companyId: String? = null,
        coderAgentId: String? = null,
        testerAgentId: String? = null,
        lastIncident: String? = null,
        error: String? = null,
    ) {
        val old = store.readSnapshot()
        store.writeSnapshot(
            old.copy(
                state = state ?: old.state,
                progress = progress ?: old.progress,
                message = message ?: old.message,
                paperclipInstalled = paperclipInstalled ?: old.paperclipInstalled,
                openClawInstalled = openClawInstalled ?: old.openClawInstalled,
                paperclipRunning = paperclipRunning ?: old.paperclipRunning,
                openClawRunning = openClawRunning ?: old.openClawRunning,
                companyId = companyId ?: old.companyId,
                coderAgentId = coderAgentId ?: old.coderAgentId,
                testerAgentId = testerAgentId ?: old.testerAgentId,
                lastIncident = lastIncident ?: old.lastIncident,
                lastError = error ?: old.lastError,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun updateNotification(detail: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(detail, ongoing))
    }

    private fun notification(detail: String, ongoing: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("PocketForge Local Autonomy")
        .setContentText(detail)
        .setContentIntent(PendingIntent.getActivity(this, 71, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(ongoing)
        .also { builder ->
            if (ongoing) builder.addAction(0, "Stop", PendingIntent.getService(this, 72, Intent(this, AutonomySupervisorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        .build()

    private fun ensureNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Local autonomy", NotificationManager.IMPORTANCE_LOW))
    }

    private suspend fun stopAll(
        reason: String,
        finalState: AutonomyServiceState = AutonomyServiceState.STOPPED,
        preserveError: Boolean = false,
    ) {
        bridge?.stop(); bridge = null
        listOf(openClawProcess, paperclipProcess).forEach { p -> runCatching { p?.destroy(); delay(400); if (p?.isAlive == true) p.destroyForcibly() } }
        openClawProcess = null; paperclipProcess = null
        val existingError = store.readSnapshot().lastError
        update(
            state = finalState,
            progress = if (finalState == AutonomyServiceState.FAILED) 0f else 0f,
            message = reason,
            paperclipRunning = false,
            openClawRunning = false,
            error = if (preserveError) existingError else null,
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRequested = true
        bridge?.stop()
        paperclipProcess?.destroy()
        openClawProcess?.destroy()
        lifecycleJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.pocketforge.mobile.AUTONOMY_START"
        const val ACTION_STOP = "com.pocketforge.mobile.AUTONOMY_STOP"
        const val PAPERCLIP_PORT = 3100
        const val OPENCLAW_PORT = 18789
        const val PAPERCLIP_VERSION = "v2026.831.1"
        const val OPENCLAW_VERSION = "2026.9.6"
        private const val COMPANY_NAME = "PocketForge Local"
        private const val CHANNEL_ID = "local-autonomy"
        private const val NOTIFICATION_ID = 71

        fun start(context: Context) = androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, AutonomySupervisorService::class.java).setAction(ACTION_START))
        fun stop(context: Context) = context.startService(Intent(context, AutonomySupervisorService::class.java).setAction(ACTION_STOP))

        private fun tail(file: File): String {
            if (!file.isFile) return ""
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val length = raf.length()
                    val start = (length - 12_000).coerceAtLeast(0)
                    raf.seek(start)
                    val bytes = ByteArray((length - start).toInt())
                    raf.readFully(bytes)
                    String(bytes, Charsets.UTF_8)
                }
            }.getOrDefault("")
        }
    }
}
