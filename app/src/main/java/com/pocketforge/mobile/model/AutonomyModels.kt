package com.pocketforge.mobile.model

import java.util.UUID

enum class AutonomyServiceState { STOPPED, INSTALLING, STARTING, RUNNING, DEGRADED, FAILED }

data class AutonomySnapshot(
    val state: AutonomyServiceState = AutonomyServiceState.STOPPED,
    val message: String = "Local autonomy is stopped",
    val progress: Float = 0f,
    val paperclipInstalled: Boolean = false,
    val openClawInstalled: Boolean = false,
    val paperclipRunning: Boolean = false,
    val openClawRunning: Boolean = false,
    val paperclipUrl: String = "http://127.0.0.1:3100",
    val openClawUrl: String = "http://127.0.0.1:18789",
    val bridgeUrl: String = "http://127.0.0.1:31900",
    val companyId: String? = null,
    val coderAgentId: String? = null,
    val testerAgentId: String? = null,
    val lastIncident: String? = null,
    val lastError: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class LocalIncident(
    val title: String,
    val description: String,
    val priority: String = "high",
    val workspace: String = "pocketforge-autonomy",
    val testCommand: String? = null,
    val dedupeKey: String = UUID.randomUUID().toString(),
)
