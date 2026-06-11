package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.Team

object Vidente : Role {
    override val team = Team.PUEBLO
    override val name = "Vidente"
    override val nightCaller = true
    override val abilities = listOf("Ver el rol de un jugador cada noche")
}
