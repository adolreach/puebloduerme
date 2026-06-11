package com.puebloduerme.server

import com.puebloduerme.engine.model.GameConfig
import com.puebloduerme.protocol.*
import com.puebloduerme.host.GameHost
import com.puebloduerme.host.WebSocketSession
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

fun main() {
    val hosts = ConcurrentHashMap<String, GameHost>()
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        install(WebSockets)

        routing {
            webSocket("/game") {
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val session = WebSocketSession { text ->
                    scope.launch { send(Frame.Text(text)) }
                }

                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()

                        val jsonObj = json.decodeFromString<JsonObject>(text)
                        val type = (jsonObj["type"] as? JsonPrimitive)?.contentOrNull ?: continue

                        when (type) {
                            "CREATE_ROOM" -> {
                                val msg: CreateRoom = json.decodeFromString(text)
                                val host = GameHost()
                                val config = GameConfig(
                                    enabledRoles = msg.roleConfig.enabledRoles.toSet(),
                                    roleCounts = msg.roleConfig.roleCounts,
                                    revealRoleOnDeath = msg.roleConfig.revealRoleOnDeath,
                                    nightDurationMs = msg.roleConfig.nightDurationSeconds * 1000L,
                                    voteDurationMs = msg.roleConfig.voteDurationSeconds * 1000L
                                )
                                val events = host.createRoom(msg.hostName, config)
                                val joined = events.filterIsInstance<com.puebloduerme.engine.PlayerJoinedEvent>().firstOrNull()
                                val token = events.filterIsInstance<com.puebloduerme.engine.TokenAssignedEvent>()
                                    .firstOrNull { joined != null && it.playerId == joined.playerId }
                                if (joined != null && token != null) {
                                    host.addSession(joined.playerId, token.token, session)
                                    hosts[host.getRoomCode()] = host
                                }
                            }
                            "JOIN_ROOM" -> {
                                val msg: JoinRoom = json.decodeFromString(text)
                                val host = hosts[msg.roomCode]
                                if (host == null) {
                                    session.send(json.encodeToString(ErrorMsg("ROOM_NOT_FOUND", "Sala no encontrada")))
                                } else {
                                    host.joinPlayer(msg.playerName, session)
                                }
                            }
                            "RECONNECT" -> {
                                val msg: Reconnect = json.decodeFromString(text)
                                val host = hosts[msg.roomCode]
                                if (host == null) {
                                    session.send(json.encodeToString(ErrorMsg("ROOM_NOT_FOUND", "Sala no encontrada")))
                                } else {
                                    val ok = host.reconnectPlayer(msg.playerToken, session)
                                    if (!ok) {
                                        session.send(json.encodeToString(ErrorMsg("RECONNECT_FAILED", "Token inválido")))
                                    }
                                }
                            }
                            else -> {
                                val host = hosts.values.find { it.getPlayer(session.playerId) != null }
                                host?.handleMessage(session.playerId, text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Log would go here in production
                } finally {
                    if (session.playerId.isNotEmpty()) {
                        hosts.values.find { it.getPlayer(session.playerId) != null }
                            ?.disconnectPlayer(session.playerId)
                    }
                    scope.cancel()
                }
            }
        }
    }.start(wait = true)
}
