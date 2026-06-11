package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Chivato : Role {
    override val team = Team.PUEBLO
    override val name = "Chivato"
    override val nightCaller = false
    override val abilities = listOf("Al morir, revela el rol de un jugador vivo a todos")

    override fun onDeath(state: GameState, self: Player) {
        state.chivatoRevealTarget = self.id
    }
}
