package com.pocketforge.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketforge.mobile.model.AutonomyServiceState

@Composable
fun AutonomyScreen(viewModel: AutonomyViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    var dashboardUrl by rememberSaveable { mutableStateOf<String?>(null) }

    if (dashboardUrl != null) {
        BackHandler { dashboardUrl = null }
        LocalDashboardScreen(
            url = dashboardUrl!!,
            title = if (dashboardUrl!!.contains(":3100")) "Paperclip" else "OpenClaw",
            onBack = { dashboardUrl = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Autonomy", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = viewModel::start) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            StatusHeader(snapshot)
            Spacer(Modifier.height(12.dp))

            if (snapshot.state == AutonomyServiceState.INSTALLING || snapshot.state == AutonomyServiceState.STARTING) {
                LinearProgressIndicator(
                    progress = { snapshot.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }

            actionMessage?.let {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(it, Modifier.padding(14.dp), fontSize = 12.sp) }
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (snapshot.state == AutonomyServiceState.RUNNING || snapshot.state == AutonomyServiceState.DEGRADED) {
                    OutlinedButton(onClick = viewModel::stop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(7.dp)); Text("Stop")
                    }
                } else {
                    Button(onClick = viewModel::start, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("Install & Start")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::submitTestIncident,
                    enabled = snapshot.paperclipRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("Test Loop")
                }
            }

            Spacer(Modifier.height(16.dp))
            ServiceCard("Paperclip", "Local control plane • goals, employees, assignments and runs", snapshot.paperclipRunning, snapshot.paperclipInstalled, snapshot.paperclipUrl) { dashboardUrl = snapshot.paperclipUrl }
            Spacer(Modifier.height(10.dp))
            ServiceCard("OpenClaw", "Local gateway • events, automations, channels and tools", snapshot.openClawRunning, snapshot.openClawInstalled, snapshot.openClawUrl) { dashboardUrl = snapshot.openClawUrl }

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Employees", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    EmployeeRow("Coder", "Claude Code", snapshot.coderAgentId)
                    Spacer(Modifier.height(10.dp))
                    EmployeeRow("Tester", "Deterministic local test runner", snapshot.testerAgentId)
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Local architecture", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Trading/data process → localhost bridge → Paperclip Coder → Tester → optional review", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Bridge: ${snapshot.bridgeUrl}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Workspace: /workspace/pocketforge-autonomy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Paperclip and OpenClaw stay supervised by PocketForge and bind only to loopback.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            snapshot.lastIncident?.let {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                    Text("Last incident: $it", Modifier.padding(14.dp), fontSize = 12.sp)
                }
            }
            snapshot.lastError?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusHeader(snapshot: com.pocketforge.mobile.model.AutonomySnapshot) {
    val (label, icon, tint) = when (snapshot.state) {
        AutonomyServiceState.RUNNING -> Triple("RUNNING", Icons.Default.CheckCircle, Color(0xFF2E7D32))
        AutonomyServiceState.INSTALLING, AutonomyServiceState.STARTING -> Triple("STARTING", Icons.Default.Refresh, MaterialTheme.colorScheme.primary)
        AutonomyServiceState.DEGRADED -> Triple("DEGRADED", Icons.Default.ErrorOutline, MaterialTheme.colorScheme.tertiary)
        AutonomyServiceState.FAILED -> Triple("FAILED", Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error)
        AutonomyServiceState.STOPPED -> Triple("STOPPED", Icons.Default.Stop, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = .12f), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PocketForge Autonomy", fontWeight = FontWeight.Bold)
                Text(snapshot.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AssistChip(onClick = {}, label = { Text(label, fontSize = 10.sp) }, leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) })
        }
    }
}

@Composable
private fun ServiceCard(title: String, subtitle: String, running: Boolean, installed: Boolean, url: String, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when { running -> "RUNNING"; installed -> "INSTALLED"; else -> "NOT INSTALLED" },
                    color = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpen, enabled = running, modifier = Modifier.fillMaxWidth()) { Text("Open local dashboard") }
        }
    }
}

@Composable
private fun EmployeeRow(name: String, role: String, id: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(role, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (id == null) "Pending" else "Ready", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalDashboardScreen(url: String, title: String, onBack: () -> Unit) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webViewState.value?.destroy() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            return host != "127.0.0.1" && host != "localhost"
                        }
                    }
                    loadUrl(url)
                    webViewState.value = this
                }
            },
        )
    }
}
