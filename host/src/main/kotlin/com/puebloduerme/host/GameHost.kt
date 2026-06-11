package com.puebloduerme.host

import com.puebloduerme.engine.*
import com.puebloduerme.engine.model.*
import com.puebloduerme.protocol.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.concurrent.ConcurrentHashMap

class GameHost {
    val game = GameSession()
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val dispatcher = EventDispatcher(this)
    var currentSenderId: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun createRoom(hostName: String, config: GameConfig): List<GameEvent> {
        return game.createRoom(hostName, config)
    }

    fun addSession(playerId: String, token: String, session: WebSocketSession) {
        session.playerId = playerId
        session.playerToken = token
        sessions[playerId] = session
    }

    fun joinPlayer(playerName: String, session: WebSocketSession): String {
        val events = game.joinRoom(playerName)
        val joined = events.filterIsInstance<PlayerJoinedEvent>().firstOrNull() ?: return ""
        val token = events.filterIsInstance<TokenAssignedEvent>()
            .firstOrNull { it.playerId == joined.playerId } ?: return ""

        addSession(joined.playerId, token.token, session)
        events.forEach { dispatcher.dispatch(it) }
        return joined.playerId
    }

    fun startGame() {
        val events = game.startGame()
        events.forEach { dispatcher.dispatch(it) }
    }

    fun handleMessage(senderId: String, rawMessage: String) {
        currentSenderId = senderId
        try {
            val clientMsg = json.decodeFromString<ClientMessage>(rawMessage)
            handleClientMessage(senderId, clientMsg)
        } catch (e: Exception) {
            sendToPlayer(senderId, ErrorMsg("PARSE_ERROR", "Mensaje inválido: ${e.message}"))
        }
    }

    private fun handleClientMessage(senderId: String, message: ClientMessage) {
        currentSenderId = senderId
        val events = when (message) {
            is com.puebloduerme.protocol.StartGame -> game.startGame()
            is NightActionMsg -> game.submitNightAction(senderId, message.actionType, message.targetId)
            is DayActionMsg -> game.submitDayAction(senderId, message.actionType, message.targetId)
            is HunterShoot -> game.submitHunterShoot(message.targetId)
            is ChivatoRevealMsg -> game.submitChivatoReveal(message.targetId)
            is CastVote -> game.castVote(senderId, message.targetId)
            is ChatMessageMsg -> game.sendChatMessage(senderId, message.channel, message.text)
            is LeaveRoom -> game.leaveRoom(senderId)
            else -> listOf(ErrorEvent("UNSUPPORTED", "Comando no soportado"))
        }
        events.forEach { dispatcher.dispatch(it) }
    }

    fun advancePhase() {
        val events = game.advancePhase()
        events.forEach { dispatcher.dispatch(it) }
    }

    fun disconnectPlayer(playerId: String) {
        sessions.remove(playerId)
        game.leaveRoom(playerId)
    }

    fun reconnectPlayer(token: String, session: WebSocketSession): Boolean {
        val player = game.reconnectPlayer(token) ?: return false
        addSession(player.id, player.token, session)

        sendToPlayer(player.id, PlayerToken(player.token))
        sendCurrentGameState(player.id)
        return true
    }

    private fun sendCurrentGameState(playerId: String) {
        val state = game.state
        val player = state.players.find { it.id == playerId } ?: return

        sendToPlayer(player.id, AssignRole(player.role.name, player.role.team.name, player.role.abilities))

        if (player.role.team == Team.LOBOS) {
            sendToPlayer(player.id, WolfChannelUpdate(getWolves(), null))
        }

        sendToPlayer(player.id, PhaseChange(state.phase.name, null, "Ronda ${state.round}"))
    }

    fun sendToAll(message: ServerMessage) {
        val text = json.encodeToString(message)
        sessions.values.forEach { it.send(text) }
    }

    fun sendToAllExcept(message: ServerMessage, excludeId: String) {
        val text = json.encodeToString(message)
        sessions.values.filter { it.playerId != excludeId }.forEach { it.send(text) }
    }

    fun sendToPlayer(playerId: String, message: ServerMessage) {
        val text = json.encodeToString(message)
        sessions[playerId]?.send(text)
    }

    fun sendToWolves(message: ServerMessage) {
        val wolves = getWolves()
        val text = json.encodeToString(message)
        sessions.values.filter { it.playerId in wolves }.forEach { it.send(text) }
    }

    fun getPlayer(playerId: String): Player? = game.state.players.find { it.id == playerId }
    fun getWolves(): List<String> = game.getWolfPlayers()
    fun getSessionCount(): Int = sessions.size
    fun getRoomCode(): String = game.state.roomCode
}

class WebSocketSession(
    var playerId: String = "",
    var playerToken: String = "",
    private val sendFn: (String) -> Unit
) {
    fun send(text: String) = sendFn(text)
}
