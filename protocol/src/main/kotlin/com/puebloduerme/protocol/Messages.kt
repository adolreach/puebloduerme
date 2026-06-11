package com.puebloduerme.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─── Cliente → Servidor ─────────────────────────────────────────────────────

@Serializable
sealed interface ClientMessage

@Serializable
@SerialName("CREATE_ROOM")
data class CreateRoom(val hostName: String, val roleConfig: RoleConfigDto) : ClientMessage

@Serializable
@SerialName("JOIN_ROOM")
data class JoinRoom(val roomCode: String, val playerName: String) : ClientMessage

@Serializable
@SerialName("LEAVE_ROOM")
data object LeaveRoom : ClientMessage

@Serializable
@SerialName("UPDATE_ROLE_CONFIG")
data class UpdateRoleConfig(val roleConfig: RoleConfigDto) : ClientMessage

@Serializable
@SerialName("START_GAME")
data object StartGame : ClientMessage

@Serializable
@SerialName("NIGHT_ACTION")
data class NightActionMsg(val actionType: String, val targetId: String) : ClientMessage

@Serializable
@SerialName("DAY_ACTION")
data class DayActionMsg(val actionType: String, val targetId: String) : ClientMessage

@Serializable
@SerialName("HUNTER_SHOOT")
data class HunterShoot(val targetId: String) : ClientMessage

@Serializable
@SerialName("CHIVATO_REVEAL")
data class ChivatoRevealMsg(val targetId: String) : ClientMessage

@Serializable
@SerialName("CAST_VOTE")
data class CastVote(val targetId: String? = null) : ClientMessage

@Serializable
@SerialName("CHAT_MESSAGE")
data class ChatMessageMsg(val channel: String, val text: String) : ClientMessage

@Serializable
@SerialName("RECONNECT")
data class Reconnect(val roomCode: String, val playerToken: String) : ClientMessage

// ─── Servidor → Cliente ─────────────────────────────────────────────────────

@Serializable
sealed interface ServerMessage

@Serializable
@SerialName("ROOM_STATE")
data class RoomState(
    val players: List<PlayerDto>,
    val roleConfig: RoleConfigDto,
    val phase: String,
    val hostId: String,
    val roomCode: String
) : ServerMessage

@Serializable
@SerialName("ASSIGN_ROLE")
data class AssignRole(
    val role: String,
    val team: String,
    val abilities: List<String>
) : ServerMessage

@Serializable
@SerialName("PLAYER_TOKEN")
data class PlayerToken(val playerToken: String) : ServerMessage

@Serializable
@SerialName("PHASE_CHANGE")
data class PhaseChange(
    val phase: String,
    val endsAt: Long? = null,
    val publicInfo: String? = null
) : ServerMessage

@Serializable
@SerialName("PRIVATE_PROMPT")
data class PrivatePrompt(
    val actionType: String,
    val eligibleTargets: List<String>,
    val deadlineMs: Long
) : ServerMessage

@Serializable
@SerialName("SEER_RESULT")
data class SeerResult(val targetId: String, val revealedRole: String) : ServerMessage

@Serializable
@SerialName("WOLF_CHANNEL_UPDATE")
data class WolfChannelUpdate(
    val wolves: List<String>,
    val wolfSeerResult: WolfSeerResultDto? = null
) : ServerMessage

@Serializable
data class WolfSeerResultDto(val targetId: String, val revealedRole: String)

@Serializable
@SerialName("DEATH_REVEAL")
data class DeathReveal(
    val deaths: List<DeathInfoDto>
) : ServerMessage

@Serializable
data class DeathInfoDto(
    val playerId: String,
    val playerName: String,
    val revealedRole: String?,
    val cause: String
)

@Serializable
@SerialName("REVIVE")
data class Revive(val playerId: String, val playerName: String) : ServerMessage

@Serializable
@SerialName("SILENCED")
data class Silenced(val playerId: String) : ServerMessage

@Serializable
@SerialName("VOTE_UPDATE")
data class VoteUpdate(val tally: Map<String, Int>) : ServerMessage

@Serializable
@SerialName("LYNCH_RESULT")
data class LynchResult(
    val playerId: String? = null,
    val playerName: String? = null,
    val revealedRole: String? = null
) : ServerMessage

@Serializable
@SerialName("CHIVATO_REVEAL_PUBLIC")
data class ChivatoRevealPublic(val targetId: String, val targetName: String, val role: String) : ServerMessage

@Serializable
@SerialName("CHAT_BROADCAST")
data class ChatBroadcast(
    val channel: String,
    val fromId: String,
    val fromName: String,
    val text: String
) : ServerMessage

@Serializable
@SerialName("GAME_OVER")
data class GameOver(
    val winningTeam: String,
    val winners: List<String>,
    val rolesSummary: List<RoleSummaryDto>
) : ServerMessage

@Serializable
data class RoleSummaryDto(
    val playerId: String,
    val playerName: String,
    val role: String,
    val team: String,
    val alive: Boolean
)

@Serializable
@SerialName("ERROR")
data class ErrorMsg(val code: String, val message: String) : ServerMessage

// ─── DTOs compartidos ───────────────────────────────────────────────────────

@Serializable
data class PlayerDto(
    val id: String,
    val name: String,
    val alive: Boolean = true,
    val connected: Boolean = true
)

@Serializable
data class RoleConfigDto(
    val enabledRoles: List<String>,
    val roleCounts: Map<String, Int>,
    val revealRoleOnDeath: Boolean = true,
    val nightDurationSeconds: Int = 40,
    val voteDurationSeconds: Int = 30
)
