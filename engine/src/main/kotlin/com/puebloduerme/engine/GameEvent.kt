package com.puebloduerme.engine

import com.puebloduerme.engine.model.Phase
import com.puebloduerme.engine.model.Team

sealed interface GameEvent

data class PlayerJoinedEvent(val playerId: String, val name: String) : GameEvent
data class PlayerLeftEvent(val playerId: String, val name: String) : GameEvent
data class RoleAssignedEvent(val playerId: String, val role: String, val team: Team, val abilities: List<String>) : GameEvent
data class TokenAssignedEvent(val playerId: String, val token: String) : GameEvent
data class PhaseChangedEvent(val phase: Phase, val endsAt: Long?, val round: Int) : GameEvent
data class DeathRevealedEvent(val deaths: List<DeathInfoEvent>) : GameEvent
data class DeathInfoEvent(val playerId: String, val playerName: String, val revealedRole: String?, val cause: String)
data class PlayerRevivedEvent(val playerId: String, val playerName: String) : GameEvent
data class PlayerSilencedEvent(val playerId: String) : GameEvent
data class VoteTallyEvent(val tally: Map<String, Int>) : GameEvent
data class LynchResultEvent(val playerId: String?, val playerName: String?, val revealedRole: String?) : GameEvent
data class SeerResultEvent(val playerId: String, val targetId: String, val revealedRole: String) : GameEvent
data class WolfSeerResultEvent(val targetId: String, val revealedRole: String) : GameEvent
data class WolfMutationEvent(val playerId: String) : GameEvent
data class WolfChannelUpdateEvent(val wolves: List<String>, val wolfSeerResult: WolfSeerResultEvent?) : GameEvent
data class ChivatoRevealedEvent(val targetId: String, val targetName: String, val role: String) : GameEvent
data class ChatBroadcastEvent(val channel: String, val fromId: String, val fromName: String, val text: String, val recipients: Set<String>) : GameEvent
data class GameOverEvent(val winningTeam: Team, val winners: List<String>, val rolesSummary: List<RoleSummaryEvent>) : GameEvent
data class RoleSummaryEvent(val playerId: String, val playerName: String, val role: String, val team: Team, val alive: Boolean)
data class ErrorEvent(val code: String, val message: String) : GameEvent
data class NightPromptEvent(val playerId: String, val actionType: String, val targets: List<String>, val deadlineMs: Long) : GameEvent
data class HunterPromptEvent(val playerId: String, val targets: List<String>) : GameEvent
data class ChivatoPromptEvent(val playerId: String, val targets: List<String>) : GameEvent
data class SilencedNotificationEvent(val playerId: String) : GameEvent
data class RoomStateEvent(
    val players: List<RoomPlayerInfo>,
    val roleConfig: Map<String, Any>,
    val phase: Phase,
    val hostId: String,
    val roomCode: String
) : GameEvent

data class RoomPlayerInfo(
    val id: String,
    val name: String,
    val alive: Boolean,
    val connected: Boolean
)
