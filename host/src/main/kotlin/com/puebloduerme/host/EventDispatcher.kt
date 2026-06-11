package com.puebloduerme.host

import com.puebloduerme.engine.*
import com.puebloduerme.engine.model.Phase
import com.puebloduerme.protocol.*

class EventDispatcher(private val host: GameHost) {

    fun dispatch(event: GameEvent) {
        when (event) {
            is PlayerJoinedEvent -> host.sendToAll(buildRoomState())
            is PlayerLeftEvent -> host.sendToAll(buildRoomState())
            is TokenAssignedEvent -> host.sendToPlayer(event.playerId, PlayerToken(event.token))
            is RoleAssignedEvent -> host.sendToPlayer(event.playerId,
                AssignRole(event.role, event.team.name, event.abilities))
            is PhaseChangedEvent -> host.sendToAll(
                PhaseChange(event.phase.name, event.endsAt, "Ronda ${event.round}"))
            is DeathRevealedEvent -> {
                val deaths = event.deaths.map {
                    DeathInfoDto(it.playerId, it.playerName, it.revealedRole, it.cause)
                }
                host.sendToAll(DeathReveal(deaths))
            }
            is PlayerRevivedEvent -> host.sendToAll(Revive(event.playerId, event.playerName))
            is PlayerSilencedEvent -> { /* Solo notificar al silenciado, ya se hace en SilencedNotificationEvent */ }
            is SilencedNotificationEvent -> host.sendToPlayer(event.playerId, Silenced(event.playerId))
            is SeerResultEvent -> host.sendToPlayer(event.playerId,
                SeerResult(event.targetId, event.revealedRole))
            is WolfSeerResultEvent -> host.sendToWolves(
                WolfChannelUpdate(host.getWolves(), WolfSeerResultDto(event.targetId, event.revealedRole)))
            is WolfMutationEvent -> host.sendToWolves(WolfChannelUpdate(host.getWolves(), null))
            is WolfChannelUpdateEvent -> {
                val result = event.wolfSeerResult?.let {
                    WolfSeerResultDto(it.targetId, it.revealedRole)
                }
                host.sendToWolves(WolfChannelUpdate(host.getWolves(), result))
            }
            is ChatBroadcastEvent -> {
                event.recipients.forEach { recipientId ->
                    host.sendToPlayer(recipientId,
                        ChatBroadcast(event.channel, event.fromId, event.fromName, event.text))
                }
            }
            is VoteTallyEvent -> host.sendToAll(VoteUpdate(event.tally))
            is LynchResultEvent -> host.sendToAll(
                LynchResult(event.playerId, event.playerName, event.revealedRole))
            is ChivatoRevealedEvent -> host.sendToAll(
                ChivatoRevealPublic(event.targetId, event.targetName, event.role))
            is GameOverEvent -> {
                val summary = event.rolesSummary.map {
                    RoleSummaryDto(it.playerId, it.playerName, it.role, it.team.name, it.alive)
                }
                host.sendToAll(GameOver(event.winningTeam.name, event.winners, summary))
            }
            is ErrorEvent -> host.sendToPlayer(host.currentSenderId,
                ErrorMsg(event.code, event.message))
            is NightPromptEvent -> host.sendToPlayer(event.playerId,
                PrivatePrompt(event.actionType, event.targets, event.deadlineMs))
            is HunterPromptEvent -> host.sendToPlayer(event.playerId,
                PrivatePrompt("HUNTER_SHOOT", event.targets, 30_000L))
            is ChivatoPromptEvent -> host.sendToPlayer(event.playerId,
                PrivatePrompt("CHIVATO_REVEAL", event.targets, 30_000L))
            is RoomStateEvent -> host.sendToAll(RoomState(
                players = event.players.map { PlayerDto(it.id, it.name, it.alive, it.connected) },
                roleConfig = RoleConfigDto(
                    enabledRoles = (event.roleConfig["enabledRoles"] as? List<String>) ?: emptyList(),
                    roleCounts = (event.roleConfig["roleCounts"] as? Map<String, Int>) ?: emptyMap(),
                    nightDurationSeconds = ((event.roleConfig["nightDurationSeconds"] as? Long) ?: 60L).toInt(),
                    voteDurationSeconds = ((event.roleConfig["voteDurationSeconds"] as? Long) ?: 60L).toInt()
                ),
                phase = event.phase.name,
                hostId = event.hostId,
                roomCode = event.roomCode
            ))
        }
    }

    private fun buildRoomState(): RoomState {
        val state = host.game.state
        return RoomState(
            players = state.players.map { PlayerDto(it.id, it.name, it.alive, it.connected) },
            roleConfig = RoleConfigDto(
                enabledRoles = state.config.enabledRoles.toList(),
                roleCounts = state.config.roleCounts,
                nightDurationSeconds = (state.config.nightDurationMs / 1000).toInt(),
                voteDurationSeconds = (state.config.voteDurationMs / 1000).toInt()
            ),
            phase = state.phase.name,
            hostId = state.hostId,
            roomCode = state.roomCode
        )
    }
}
