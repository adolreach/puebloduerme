package com.puebloduerme.engine.model

import com.puebloduerme.engine.roles.Role

data class GameConfig(
    val enabledRoles: Set<String>,
    val roleCounts: Map<String, Int>,
    val revealRoleOnDeath: Boolean = true,
    val nightDurationMs: Long = 40_000L,
    val voteDurationMs: Long = 30_000L,
    val allowSelfSilence: Boolean = false,
    val usurperLosesIfTargetSurvives: Boolean = true
)

data class DeathInfo(
    val playerId: String,
    val playerName: String,
    val revealedRole: String?,
    val cause: String
)

enum class DeathCause {
    LOBOS,
    CAZADOR,
    SACERDOTE,
    LINCHAMIENTO
}

data class NightAction(
    val playerId: String,
    val actionType: String,
    val targetId: String
)

enum class DayActionType {
    PRIEST_HOLYWATER
}

data class DayAction(
    val playerId: String,
    val actionType: DayActionType,
    val targetId: String
)
