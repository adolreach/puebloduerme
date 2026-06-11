package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object AbuelaGrunona : Role {
    override val team = Team.PUEBLO
    override val name = "Abuela gruñona"
    override val nightCaller = true
    override val abilities = listOf("Silenciar a un jugador cada noche")
}
