package com.pocketforge.mobile.runtime

import com.pocketforge.mobile.model.LocalIncident
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

internal class AutonomyLocalBridge(
    private val token: String,
    private val onIncident: suspend (LocalIncident) -> String,
    private val status: () -> JSONObject,
) {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "pocketforge-autonomy-http").apply { isDaemon = true } }

    fun start() {
        if (running) return
        running = true
        server = ServerSocket(BRIDGE_PORT, 32, InetAddress.getByName("127.0.0.1"))
        executor.execute {
            while (running) {
                val client = runCatching { server?.accept() }.getOrNull() ?: break
                executor.execute { handle(client) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 8_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: return
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 256_000) ?: 0
            val body = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(body, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')
            if (headers["x-pocketforge-token"] != token) {
                respond(client, 401, "{\"error\":\"unauthorized\"}")
                return
            }
            when {
                method == "GET" && path == "/health" -> respond(client, 200, JSONObject().put("ok", true).toString())
                method == "GET" && path == "/status" -> respond(client, 200, status().toString())
                method == "POST" && path == "/incident" -> {
                    val json = runCatching { JSONObject(String(body, 0, read)) }.getOrNull()
                    if (json == null) {
                        respond(client, 400, "{\"error\":\"invalid_json\"}")
                        return
                    }
                    val priority = json.optString("priority", "high").lowercase()
                    val safePriority = if (priority in setOf("critical", "high", "medium", "low")) priority else "high"
                    val incident = LocalIncident(
                        title = json.optString("title").trim().ifBlank { "Unnamed local incident" },
                        description = json.optString("description").trim().ifBlank { "No description supplied." },
                        priority = safePriority,
                        workspace = json.optString("workspace", "pocketforge-autonomy").trim().ifBlank { "pocketforge-autonomy" },
                        testCommand = json.optString("testCommand").takeIf(String::isNotBlank),
                        dedupeKey = json.optString("dedupeKey").ifBlank { UUID.randomUUID().toString() },
                    )
                    val result = runBlocking { onIncident(incident) }
                    respond(client, 202, JSONObject().put("accepted", true).put("issue", result).toString())
                }
                else -> respond(client, 404, "{\"error\":\"not_found\"}")
            }
        }
    }

    private fun respond(socket: Socket, code: Int, body: String) {
        val reason = when (code) { 200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 401 -> "Unauthorized"; else -> "Not Found" }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $code $reason\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    companion object { const val BRIDGE_PORT = 31900 }
}
