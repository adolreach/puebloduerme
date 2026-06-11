package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

data class Usurpador(val targetId: String) : Role {
    override val team = Team.NEUTRAL
    override val name = "Usurpador"
    override val nightCaller = false
    override val abilities = listOf("Si su objetivo muere, adopta su rol y equipo")
}
