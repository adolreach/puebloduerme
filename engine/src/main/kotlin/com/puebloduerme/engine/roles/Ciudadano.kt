package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.Team

object Ciudadano : Role {
    override val team = Team.PUEBLO
    override val name = "Ciudadano"
    override val nightCaller = false
    override val abilities = listOf("Ninguna")
}
