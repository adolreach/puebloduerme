package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*

class ChatManager(private val state: GameState) {

    fun canSendMessage(playerId: String, channel: String): Boolean {
        val player = state.players.find { it.id == playerId } ?: return false

        return when (channel) {
            "PUEBLO" -> player.alive && !player.silencedThisRound
            "LOBOS" -> player.alive && player.role.team == Team.LOBOS
            "MUERTOS" -> !player.alive || (player.alive && player.role is Brujo)
            else -> false
        }
    }

    fun getRecipients(channel: String, senderId: String): Set<String> {
        return when (channel) {
            "PUEBLO" -> state.vivos.map { it.id }.toSet()
            "LOBOS" -> state.lobosVivos.map { it.id }.toSet()
            "MUERTOS" -> {
                val recipients = state.muertos.map { it.id }.toMutableSet()
                val brujosVivos = state.vivos.filter { it.role is Brujo }.map { it.id }
                recipients.addAll(brujosVivos)
                recipients
            }
            else -> emptySet()
        }
    }

    fun sendMessage(senderId: String, channel: String, text: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (!canSendMessage(senderId, channel)) {
            events.add(ErrorEvent("CHAT_DENIED", "No puedes enviar mensajes en este canal"))
            return events
        }

        val sender = state.players.find { it.id == senderId } ?: return events
        val recipients = getRecipients(channel, senderId)

        val entry = ChatEntry(channel, senderId, sender.name, text)
        state.chatMessages.add(entry)

        events.add(ChatBroadcastEvent(channel, senderId, sender.name, text, recipients))
        return events
    }
}
