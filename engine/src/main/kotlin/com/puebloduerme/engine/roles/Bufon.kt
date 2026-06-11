package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Bufon : Role {
    override val team = Team.NEUTRAL
    override val name = "Bufón"
    override val nightCaller = false
    override val abilities = listOf("Gana si es linchado en votación diurna (no termina la partida)")
}
