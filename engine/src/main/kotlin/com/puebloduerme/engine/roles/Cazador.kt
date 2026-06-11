package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Cazador : Role {
    override val team = Team.PUEBLO
    override val name = "Cazador"
    override val nightCaller = false
    override val abilities = listOf("Al morir, elige a un jugador para matar (30s)")

    override fun onDeath(state: GameState, self: Player) {
        state.pendingHunterDeath = self.id
    }
}
