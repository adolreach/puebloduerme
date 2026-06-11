package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*
import kotlin.random.Random
import java.util.UUID

class GameSession {

    val state = GameState()
    private val nightResolver = NightResolver(state)
    private val voteResolver = VoteResolver(state)
    val chat = ChatManager(state)

    fun createRoom(hostName: String, config: GameConfig): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        state.roomCode = generateRoomCode()
        state.config = config

        val hostId = generatePlayerId()
        val hostToken = generateToken()
        state.hostId = hostId
        state.playerTokens[hostId] = hostToken

        val host = Player(
            id = hostId,
            name = hostName,
            role = Ciudadano,
            token = hostToken
        )
        state.players.add(host)

        events.add(PlayerJoinedEvent(hostId, hostName))
        events.add(TokenAssignedEvent(hostId, hostToken))
        events.add(buildRoomState(hostId))

        return events
    }

    fun joinRoom(playerName: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.phase != Phase.LOBBY) {
            events.add(ErrorEvent("GAME_STARTED", "La partida ya ha comenzado"))
            return events
        }

        val playerId = generatePlayerId()
        val token = generateToken()
        state.playerTokens[playerId] = token

        val player = Player(
            id = playerId,
            name = playerName,
            role = Ciudadano,
            token = token
        )
        state.players.add(player)

        events.add(PlayerJoinedEvent(playerId, playerName))
        events.add(TokenAssignedEvent(playerId, token))
        events.add(buildRoomState(state.hostId))

        return events
    }

    fun leaveRoom(playerId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val player = state.players.find { it.id == playerId } ?: return events

        if (state.phase == Phase.LOBBY) {
            state.players.remove(player)
            events.add(PlayerLeftEvent(player.id, player.name))
            events.add(buildRoomState(state.hostId))
        } else {
            player.connected = false
            events.add(PlayerLeftEvent(player.id, player.name))
        }

        return events
    }

    fun startGame(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.players.size < 5) {
            events.add(ErrorEvent("NOT_ENOUGH_PLAYERS", "Se necesitan al menos 5 jugadores"))
            return events
        }

        state.totalInicial = state.players.size

        RoleAssigner.assignRoles(state)

        for (player in state.players) {
            events.add(RoleAssignedEvent(
                playerId = player.id,
                role = player.role.name,
                team = player.role.team,
                abilities = player.role.abilities
            ))
            // Also send visible wolves list to each wolf
            if (player.role.team == Team.LOBOS) {
                val wolves = state.lobosVivos.map { it.id }
                events.add(WolfChannelUpdateEvent(wolves, null))
            }
        }

        state.round = 1
        changePhase(Phase.NOCHE, events)

        return events
    }

    fun advancePhase(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        if (state.phase == Phase.FIN) return events

        when (state.phase) {
            Phase.NOCHE -> {
                val nightEvents = nightResolver.resolve()
                events.addAll(nightEvents)
                val victoryEvents = VictoryChecker.check(state)
                events.addAll(victoryEvents)
                if (state.phase != Phase.FIN) {
                    changePhase(Phase.DIA, events)
                }
            }
            Phase.DIA -> {
                changePhase(Phase.DISCUSION, events)
            }
            Phase.DISCUSION -> {
                changePhase(Phase.VOTACION, events)
            }
            Phase.VOTACION -> {
                val voteEvents = voteResolver.resolve()
                events.addAll(voteEvents)

                val victoryEvents = VictoryChecker.check(state)
                events.addAll(victoryEvents)

                if (state.phase != Phase.FIN) {
                    state.round++
                    changePhase(Phase.NOCHE, events)
                }
            }
            Phase.FIN, Phase.LOBBY -> {}
        }

        return events
    }

    fun submitNightAction(playerId: String, actionType: String, targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.phase != Phase.NOCHE) {
            events.add(ErrorEvent("WRONG_PHASE", "No es de noche"))
            return events
        }

        val player = state.players.find { it.id == playerId && it.alive } ?: run {
            events.add(ErrorEvent("NOT_FOUND", "Jugador no encontrado"))
            return events
        }

        when (actionType) {
            "WOLF_KILL_VOTE" -> {
                if (player.role.team != Team.LOBOS) {
                    events.add(ErrorEvent("NOT_WOLF", "Solo los lobos pueden votar víctima"))
                    return events
                }
                state.wolfVotes[playerId] = targetId
            }
            "SEER_INSPECT" -> {
                if (player.role !is Vidente) {
                    events.add(ErrorEvent("NOT_SEER", "No eres el Vidente"))
                    return events
                }
                state.nightActions.removeAll { it.playerId == playerId && it.actionType == "SEER_INSPECT" }
                state.nightActions.add(NightAction(playerId, actionType, targetId))
            }
            "GRANDMA_SILENCE" -> {
                if (player.role !is AbuelaGrunona) {
                    events.add(ErrorEvent("NOT_GRANDMA", "No eres la Abuela gruñona"))
                    return events
                }
                state.nightActions.removeAll { it.playerId == playerId && it.actionType == "GRANDMA_SILENCE" }
                state.nightActions.add(NightAction(playerId, actionType, targetId))
            }
            "WITCH_REVIVE" -> {
                if (player.role !is Brujo) {
                    events.add(ErrorEvent("NOT_WITCH", "No eres el Brujo"))
                    return events
                }
                if (state.witchUsedRevive) {
                    events.add(ErrorEvent("ALREADY_USED", "Ya usaste tu conjuro"))
                    return events
                }
                state.nightActions.removeAll { it.playerId == playerId && it.actionType == "WITCH_REVIVE" }
                state.nightActions.add(NightAction(playerId, actionType, targetId))
            }
            "WOLFSEER_INSPECT" -> {
                if (player.role !is HombreLoboVidente) {
                    events.add(ErrorEvent("NOT_WOLFSEER", "No eres el Hombre lobo vidente"))
                    return events
                }
                state.nightActions.removeAll { it.playerId == playerId && it.actionType == "WOLFSEER_INSPECT" }
                state.nightActions.add(NightAction(playerId, actionType, targetId))
            }
        }

        return events
    }

    fun submitDayAction(playerId: String, actionType: String, targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.phase != Phase.DIA && state.phase != Phase.DISCUSION && state.phase != Phase.VOTACION) {
            events.add(ErrorEvent("WRONG_PHASE", "No es de día"))
            return events
        }

        when (actionType) {
            "PRIEST_HOLYWATER" -> {
                val result = voteResolver.resolvePriestHolyWater(playerId, targetId)
                events.addAll(result)
                val victoryEvents = VictoryChecker.check(state)
                events.addAll(victoryEvents)
            }
        }

        return events
    }

    fun submitHunterShoot(playerId: String, targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.phase != Phase.DIA) {
            events.add(ErrorEvent("WRONG_PHASE", "Solo puedes disparar durante el día"))
            return events
        }
        if (state.pendingHunterDeath == null) {
            events.add(ErrorEvent("NO_HUNTER", "No hay disparo pendiente del Cazador"))
            return events
        }
        if (state.pendingHunterDeath != playerId) {
            events.add(ErrorEvent("NOT_YOUR_TURN", "No eres el Cazador que debe disparar"))
            return events
        }

        val result = voteResolver.resolveHunterShoot(targetId)
        events.addAll(result)
        val victoryEvents = VictoryChecker.check(state)
        events.addAll(victoryEvents)
        return events
    }

    fun submitChivatoReveal(playerId: String, targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        if (state.chivatoRevealTarget != playerId) {
            events.add(ErrorEvent("NOT_CHIVATO", "No eres el Chivato que acaba de morir"))
            return events
        }
        val result = voteResolver.resolveChivatoReveal(targetId)
        events.addAll(result)
        return events
    }

    fun castVote(voterId: String, targetId: String?): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (state.phase != Phase.VOTACION) {
            events.add(ErrorEvent("WRONG_PHASE", "No es fase de votación"))
            return events
        }

        val voter = state.players.find { it.id == voterId } ?: run {
            events.add(ErrorEvent("NOT_FOUND", "Jugador no encontrado"))
            return events
        }

        if (!voter.canVote) {
            events.add(ErrorEvent("CANNOT_VOTE", "No puedes votar (muerto o silenciado)"))
            return events
        }

        if (voter.hasVoted) {
            events.add(ErrorEvent("ALREADY_VOTED", "Ya has votado"))
            return events
        }

        voteResolver.castVote(voterId, targetId)
        return events
    }

    fun sendChatMessage(senderId: String, channel: String, text: String): List<GameEvent> {
        return chat.sendMessage(senderId, channel, text)
    }

    fun getPhaseDurationMs(): Long {
        return when (state.phase) {
            Phase.NOCHE -> state.config.nightDurationMs
            Phase.DISCUSION -> calculateDiscussionDuration()
            Phase.VOTACION -> state.config.voteDurationMs
            else -> 0L
        }
    }

    fun calculateDiscussionDuration(): Long {
        val vivos = state.vivosActuales
        val total = state.totalInicial
        if (total == 0) return 45_000L

        val seconds = 45.0 + (vivos.toDouble() / total.toDouble()) * 75.0
        val clamped = seconds.coerceIn(45.0, 120.0)
        return (clamped * 1000).toLong()
    }

    fun getEligibleTargets(playerId: String): List<String> {
        val player = state.players.find { it.id == playerId } ?: return emptyList()
        return when (state.phase) {
            Phase.NOCHE -> {
                when (player.role) {
                    is Vidente, is HombreLoboVidente -> state.vivos.filter { it.id != playerId }.map { it.id }
                    is AbuelaGrunona -> state.vivos.filter { p ->
                        p.id != playerId || state.config.allowSelfSilence
                    }.map { it.id }
                    is Brujo -> state.players.map { it.id }
                    else -> emptyList()
                }
            }
            Phase.DIA, Phase.DISCUSION -> {
                when (player.role) {
                    is Sacerdote -> state.vivos.filter { it.id != playerId }.map { it.id }
                    else -> emptyList()
                }
            }
            Phase.VOTACION -> state.vivos.filter { it.id != playerId }.map { it.id }
            else -> emptyList()
        }
    }

    fun getWolfPlayers(): List<String> = state.lobosVivos.map { it.id }

    fun buildRoomState(hostId: String): RoomStateEvent {
        return RoomStateEvent(
            players = state.players.map {
                RoomPlayerInfo(it.id, it.name, it.alive, it.connected)
            },
            roleConfig = mapOf(
                "enabledRoles" to state.config.enabledRoles.toList(),
                "roleCounts" to state.config.roleCounts,
                "nightDurationSeconds" to (state.config.nightDurationMs / 1000),
                "voteDurationSeconds" to (state.config.voteDurationMs / 1000)
            ),
            phase = state.phase,
            hostId = hostId,
            roomCode = state.roomCode
        )
    }

    fun reconnectPlayer(token: String): Player? {
        val player = state.players.find { it.token == token } ?: return null
        player.connected = true
        return player
    }

    private fun changePhase(newPhase: Phase, events: MutableList<GameEvent>) {
        state.phase = newPhase
        val duration = getPhaseDurationMs()
        val endsAt = if (duration > 0) System.currentTimeMillis() + duration else null

        events.add(PhaseChangedEvent(newPhase, endsAt, state.round))

        if (newPhase == Phase.NOCHE) {
            sendNightPrompts(events)
        }

        if (newPhase == Phase.VOTACION) {
            sendVotePrompts(events)
        }
    }

    private fun sendNightPrompts(events: MutableList<GameEvent>) {
        val allAlive = state.vivos.map { it.id }

        for (player in state.vivos) {
            val targets = getEligibleTargets(player.id)
            if (targets.isEmpty()) continue

            val actionType = when (player.role) {
                is Vidente -> "SEER_INSPECT"
                is HombreLoboVidente -> "WOLFSEER_INSPECT"
                is AbuelaGrunona -> "GRANDMA_SILENCE"
                is Brujo -> "WITCH_REVIVE"
                else -> null
            }

            if (actionType != null) {
                events.add(NightPromptEvent(player.id, actionType, targets, state.config.nightDurationMs))
            }
        }

        for (wolfId in state.lobosVivos.map { it.id }) {
            events.add(NightPromptEvent(wolfId, "WOLF_KILL_VOTE", allAlive, state.config.nightDurationMs))
        }
    }

    private fun sendVotePrompts(events: MutableList<GameEvent>) {
        for (player in state.vivos.filter { !it.silencedThisRound }) {
            val targets = state.vivos.filter { it.id != player.id }.map { it.id }
            if (targets.isEmpty()) continue
            events.add(NightPromptEvent(player.id, "CAST_VOTE", targets, state.config.voteDurationMs))
        }
    }

    private fun generatePlayerId(): String = UUID.randomUUID().toString().take(8)
    private fun generateToken(): String = UUID.randomUUID().toString().take(12)
    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
