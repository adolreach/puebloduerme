package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Brujo : Role {
    override val team = Team.PUEBLO
    override val name = "Brujo"
    override val nightCaller = true
    override val abilities = listOf("Hablar con los muertos", "Revivir a un jugador (un solo uso)")

    override fun canChatInChannel(channel: String, state: GameState, self: Player): Boolean {
        return channel == "MUERTOS" && self.alive
    }
}
