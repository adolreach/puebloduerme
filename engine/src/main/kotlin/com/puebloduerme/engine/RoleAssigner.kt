package com.puebloduerme.engine

import com.puebloduerme.engine.model.GameConfig
import com.puebloduerme.engine.model.GameState
import com.puebloduerme.engine.model.Team
import com.puebloduerme.engine.roles.*
import kotlin.random.Random

object RoleAssigner {

    fun assignRoles(state: GameState) {
        val playerCount = state.players.size
        val config = state.config
        val roles = mutableListOf<Role>()

        val roleCounts = config.roleCounts.toMutableMap()

        for ((roleName, count) in roleCounts) {
            if (count <= 0) continue
            val role = createRole(roleName)
            repeat(count) { roles.add(role) }
        }

        val assigned = roles.size
        val ciudadanosNeeded = (playerCount - assigned).coerceAtLeast(0)
        repeat(ciudadanosNeeded) { roles.add(Ciudadano) }

        val shuffled = roles.shuffled(Random)
        state.players.forEachIndexed { index, player ->
            if (index < shuffled.size) player.role = shuffled[index]
        }

        assignUsurpadorTargets(state)
    }

    private fun assignUsurpadorTargets(state: GameState) {
        val usurpadores = state.players.filter { it.role is Usurpador && (it.role as Usurpador).targetId.isEmpty() }
        val otherPlayers = state.players.filter { it.role !is Usurpador }

        usurpadores.forEach { usurpador ->
            if (otherPlayers.isNotEmpty()) {
                val target = otherPlayers.random(Random)
                usurpador.role = Usurpador(target.id)
                usurpador.targetId = target.id
            }
        }
    }

    fun createRole(name: String): Role = when (name) {
        "Ciudadano" -> Ciudadano
        "Abuela gruñona" -> AbuelaGrunona
        "Cazador" -> Cazador
        "Brujo" -> Brujo
        "Vidente" -> Vidente
        "Sacerdote" -> Sacerdote
        "Chivato" -> Chivato
        "Hombre lobo" -> HombreLobo
        "Hombre lobo vidente" -> HombreLoboVidente
        "Bufón" -> Bufon
        "Atormentado" -> Atormentado
        "Usurpador" -> Usurpador("")
        else -> Ciudadano
    }

    fun allRoles(): List<Role> = listOf(
        Ciudadano, AbuelaGrunona, Cazador, Brujo, Vidente, Sacerdote, Chivato,
        HombreLobo, HombreLoboVidente, Bufon, Atormentado, Usurpador("")
    )

    fun rolesByTeam(team: Team): List<Role> = allRoles().filter { it.team == team }

    fun puebloRoles() = allRoles().filter { it.team == Team.PUEBLO }.map { it.name }
    fun loboRoles() = allRoles().filter { it.team == Team.LOBOS }.map { it.name }
    fun neutralRoles() = allRoles().filter { it.team == Team.NEUTRAL }.map { it.name }

    fun getPreset(playerCount: Int): GameConfig {
        val wolves = when {
            playerCount in 5..6 -> 1
            playerCount in 7..9 -> 2
            playerCount in 10..12 -> 3
            playerCount in 13..16 -> 4
            else -> 1
        }

        val hasWolfSeer = playerCount >= 10
        val neutrals = when {
            playerCount in 5..6 -> 1
            playerCount in 7..12 -> 1
            playerCount >= 13 -> 2
            else -> 0
        }

        val roleCounts = mutableMapOf<String, Int>()
        if (hasWolfSeer && wolves > 2) {
            roleCounts["Hombre lobo"] = wolves - 1
            roleCounts["Hombre lobo vidente"] = 1
        } else {
            roleCounts["Hombre lobo"] = wolves
        }

        if (neutrals >= 1) roleCounts["Bufón"] = 1
        if (neutrals >= 2) roleCounts["Atormentado"] = 1

        val enabled = mutableSetOf("Ciudadano", "Hombre lobo", "Hombre lobo vidente",
            "Bufón", "Vidente", "Brujo", "Cazador", "Abuela gruñona", "Sacerdote",
            "Chivato", "Atormentado", "Usurpador")

        return GameConfig(
            enabledRoles = enabled,
            roleCounts = roleCounts,
            revealRoleOnDeath = true,
            nightDurationMs = 60_000L,
            voteDurationMs = 60_000L
        )
    }
}
