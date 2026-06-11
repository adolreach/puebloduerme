package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Sacerdote : Role {
    override val team = Team.PUEBLO
    override val name = "Sacerdote"
    override val nightCaller = false
    override val abilities = listOf("Agua bendita: si es lobo muere, si no muere el Sacerdote (un solo uso)")
}
