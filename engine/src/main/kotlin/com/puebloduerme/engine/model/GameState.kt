package com.puebloduerme.engine.model

import com.puebloduerme.engine.roles.Role

data class GameState(
    val players: MutableList<Player> = mutableListOf(),
    var phase: Phase = Phase.LOBBY,
    var round: Int = 0,
    var config: GameConfig = GameConfig(emptySet(), emptyMap()),
    val nightActions: MutableList<NightAction> = mutableListOf(),
    val dayActions: MutableList<DayAction> = mutableListOf(),
    val votes: MutableMap<String, String?> = mutableMapOf(),
    val winners: MutableSet<String> = mutableSetOf(),
    val wolfVotes: MutableMap<String, String> = mutableMapOf(),
    var hunterKillTarget: String? = null,
    var chivatoRevealTarget: String? = null,
    var witchReviveTarget: String? = null,
    var witchUsedRevive: Boolean = false,
    var priestUsedHolyWater: Boolean = false,
    val revealedRoles: MutableMap<String, String> = mutableMapOf(),
    var pendingHunterDeath: String? = null,
    val chatMessages: MutableList<ChatEntry> = mutableListOf(),
    var roomCode: String = "",
    var hostId: String = "",
    val playerTokens: MutableMap<String, String> = mutableMapOf(),
    var totalInicial: Int = 0
) {
    val vivos: List<Player> get() = players.filter { it.alive }
    val muertos: List<Player> get() = players.filter { !it.alive }
    val lobosVivos: List<Player> get() = players.filter { it.alive && it.role.team == Team.LOBOS }
    val puebloVivos: List<Player> get() = players.filter { it.alive && it.role.team == Team.PUEBLO }
    val neutralesVivos: List<Player> get() = players.filter { it.alive && it.role.team == Team.NEUTRAL }
    val vivosActuales: Int get() = vivos.size
}

data class ChatEntry(
    val channel: String,
    val fromId: String,
    val fromName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
