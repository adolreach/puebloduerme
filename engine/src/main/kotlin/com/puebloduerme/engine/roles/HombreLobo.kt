package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object HombreLobo : Role {
    override val team = Team.LOBOS
    override val name = "Hombre lobo"
    override val nightCaller = true
    override val abilities = listOf("Votar víctima con los demás lobos")

    override fun canChatInChannel(channel: String, state: GameState, self: Player): Boolean {
        return channel == "LOBOS" && self.alive
    }
}
