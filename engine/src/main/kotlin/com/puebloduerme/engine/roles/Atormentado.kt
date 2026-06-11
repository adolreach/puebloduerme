package com.puebloduerme.engine.roles

import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Player
import com.puebloduerme.engine.model.Team

object Atormentado : Role {
    override val team = Team.PUEBLO
    override val name = "Atormentado"
    override val nightCaller = false
    override val abilities = listOf("Si los lobos lo matan, muta a lobo en vez de morir (secreto)")

    override fun shouldRevealRoleOnDeath(state: GameState, self: Player): String? {
        return null
    }
}
