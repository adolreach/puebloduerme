package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object HombreLoboVidente : Role {
    override val team = Team.LOBOS
    override val name = "Hombre lobo vidente"
    override val nightCaller = true
    override val abilities = listOf("Votar víctima con los lobos", "Ver el rol de un jugador (resultado compartido con lobos)")

    override fun canChatInChannel(channel: String, state: GameState, self: Player): Boolean {
        return channel == "LOBOS" && self.alive
    }
}
