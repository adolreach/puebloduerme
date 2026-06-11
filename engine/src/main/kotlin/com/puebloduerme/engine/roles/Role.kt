package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

interface Role {
    val team: Team
    val name: String
    val nightCaller: Boolean
    val abilities: List<String>

    fun onNight(state: GameState, self: Player) {}
    fun onDay(state: GameState, self: Player) {}
    fun onDeath(state: GameState, self: Player) {}
    fun shouldRevealRoleOnDeath(state: GameState, self: Player): String? = name
    fun canChatInChannel(channel: String, state: GameState, self: Player): Boolean = false
}
