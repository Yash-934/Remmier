package com.pocketforge.mobile.runtime

import android.content.Context
import com.pocketforge.mobile.model.AutonomyServiceState
import com.pocketforge.mobile.model.AutonomySnapshot
import java.io.File
import java.security.SecureRandom
import org.json.JSONObject

class AutonomyStore(context: Context) {
    val root = File(context.filesDir, "autonomy")
    val logs = File(root, "logs")
    val paperclipHome = File(root, "paperclip")
    val openClawState = File(root, "openclaw")
    val workspace = File(context.filesDir, "workspaces/pocketforge-autonomy")
    private val configFile = File(root, "config.json")
    private val statusFile = File(root, "status.json")
    private val bridgeTokenFile = File(root, "bridge-token")

    init {
        root.mkdirs(); logs.mkdirs(); paperclipHome.mkdirs(); openClawState.mkdirs(); workspace.mkdirs()
    }

    fun readConfig(): JSONObject = runCatching { JSONObject(configFile.readText()) }.getOrDefault(JSONObject())
    fun writeConfig(json: JSONObject) { root.mkdirs(); configFile.writeText(json.toString(2)) }

    @Synchronized
    fun readSnapshot(): AutonomySnapshot {
        if (!statusFile.isFile) return AutonomySnapshot()
        return runCatching {
            val o = JSONObject(statusFile.readText())
            AutonomySnapshot(
                state = runCatching { AutonomyServiceState.valueOf(o.optString("state")) }.getOrDefault(AutonomyServiceState.STOPPED),
                message = o.optString("message", ""),
                progress = o.optDouble("progress", 0.0).toFloat(),
                paperclipInstalled = o.optBoolean("paperclipInstalled"),
                openClawInstalled = o.optBoolean("openClawInstalled"),
                paperclipRunning = o.optBoolean("paperclipRunning"),
                openClawRunning = o.optBoolean("openClawRunning"),
                paperclipUrl = o.optString("paperclipUrl", "http://127.0.0.1:3100"),
                openClawUrl = o.optString("openClawUrl", "http://127.0.0.1:18789"),
                bridgeUrl = o.optString("bridgeUrl", "http://127.0.0.1:31900"),
                companyId = o.optString("companyId").takeIf(String::isNotBlank),
                coderAgentId = o.optString("coderAgentId").takeIf(String::isNotBlank),
                testerAgentId = o.optString("testerAgentId").takeIf(String::isNotBlank),
                lastIncident = o.optString("lastIncident").takeIf(String::isNotBlank),
                lastError = o.optString("lastError").takeIf(String::isNotBlank),
                updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
            )
        }.getOrDefault(AutonomySnapshot(message = "Autonomy state is unreadable"))
    }

    @Synchronized
    fun writeSnapshot(snapshot: AutonomySnapshot) {
        val json = JSONObject()
            .put("state", snapshot.state.name)
            .put("message", snapshot.message)
            .put("progress", snapshot.progress)
            .put("paperclipInstalled", snapshot.paperclipInstalled)
            .put("openClawInstalled", snapshot.openClawInstalled)
            .put("paperclipRunning", snapshot.paperclipRunning)
            .put("openClawRunning", snapshot.openClawRunning)
            .put("paperclipUrl", snapshot.paperclipUrl)
            .put("openClawUrl", snapshot.openClawUrl)
            .put("bridgeUrl", snapshot.bridgeUrl)
            .put("companyId", snapshot.companyId.orEmpty())
            .put("coderAgentId", snapshot.coderAgentId.orEmpty())
            .put("testerAgentId", snapshot.testerAgentId.orEmpty())
            .put("lastIncident", snapshot.lastIncident.orEmpty())
            .put("lastError", snapshot.lastError.orEmpty())
            .put("updatedAtMillis", snapshot.updatedAtMillis)
        val tmp = File(root, "status.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(statusFile)) {
            statusFile.writeText(json.toString())
            tmp.delete()
        }
    }

    @Synchronized
    fun bridgeToken(): String {
        if (bridgeTokenFile.isFile) return bridgeTokenFile.readText().trim()
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        bridgeTokenFile.writeText(token)
        return token
    }
}
