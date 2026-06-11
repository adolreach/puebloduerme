package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*
import kotlin.random.Random

class NightResolver(private val state: GameState) {

    fun resolve(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val wolfKillTarget = mutableListOf<String>()

        // 1. Limpieza: retirar marcas temporales
        state.players.forEach { it.silencedThisRound = false }
        state.votes.clear()

        // 2. Información: Vidente + Lobo vidente
        resolveSeer(state, events)
        resolveWolfSeer(state, events)

        // 3. Abuela gruñona: silenciar
        resolveGrandma(state, events)

        // 4. Lobos: resolver voto de víctima
        val wolfVictimId = resolveWolfVote(state)

        // 5. Aplicar muerte de lobos (excepción Atormentado)
        val mutated = applyWolfKill(wolfVictimId, state, events)
        if (wolfVictimId != null && !mutated) {
            wolfKillTarget.add(wolfVictimId)
        }

        // 6. Brujo: revivir
        val witchReviveId = resolveWitchRevive(state)

        // 7. Calcular muertes netas
        val deaths = calculateDeaths(state, wolfKillTarget, witchReviveId, events)

        // 8. Revelar muertes
        revealDeaths(deaths, state, events)

        // 9. Cazador / Chivato al morir
        triggerOnDeathEffects(deaths, state, events)

        // 10. Usurpador: heredar rol
        checkUsurpadorInheritance(deaths, state)

        state.wolfVotes.clear()
        state.nightActions.clear()

        return events
    }

    private fun resolveSeer(state: GameState, events: MutableList<GameEvent>) {
        val actions = state.nightActions.filter { it.actionType == "SEER_INSPECT" }
        for (action in actions) {
            val target = state.players.find { it.id == action.targetId && it.alive } ?: continue
            events.add(SeerResultEvent(action.playerId, target.id, target.role.name))
        }
    }

    private fun resolveWolfSeer(state: GameState, events: MutableList<GameEvent>) {
        val actions = state.nightActions.filter { it.actionType == "WOLFSEER_INSPECT" }
        for (action in actions) {
            val target = state.players.find { it.id == action.targetId && it.alive } ?: continue
            val result = WolfSeerResultEvent(target.id, target.role.name)
            events.add(result)
            val wolfIds = state.lobosVivos.map { it.id }
            events.add(WolfChannelUpdateEvent(wolfIds, result))
        }
    }

    private fun resolveGrandma(state: GameState, events: MutableList<GameEvent>) {
        val actions = state.nightActions.filter { it.actionType == "GRANDMA_SILENCE" }
        for (action in actions) {
            val target = state.players.find { it.id == action.targetId && it.alive } ?: continue
            val allowSelf = state.config.allowSelfSilence
            if (action.playerId == target.id && !allowSelf) continue
            target.silencedThisRound = true
            events.add(PlayerSilencedEvent(target.id))
            events.add(SilencedNotificationEvent(target.id))
        }
    }

    private fun resolveWolfVote(state: GameState): String? {
        val wolves = state.lobosVivos
        if (wolves.isEmpty()) return null
        if (state.wolfVotes.isEmpty()) return null

        val tally = mutableMapOf<String, Int>()
        for ((_, targetId) in state.wolfVotes) {
            tally[targetId] = (tally[targetId] ?: 0) + 1
        }

        val maxVotes = tally.values.maxOrNull() ?: return null
        val topTargets = tally.filter { it.value == maxVotes }.keys.toList()

        return if (topTargets.size == 1) topTargets.first()
        else topTargets.random(Random)
    }

    private fun applyWolfKill(victimId: String?, state: GameState, events: MutableList<GameEvent>): Boolean {
        if (victimId == null) return false
        val victim = state.players.find { it.id == victimId && it.alive } ?: return false

        if (victim.role is Atormentado) {
            victim.role = HombreLobo
            victim.mutationPending = true
            events.add(WolfMutationEvent(victim.id))
            val wolfIds = state.lobosVivos.map { it.id }
            events.add(WolfChannelUpdateEvent(wolfIds, null))
            return true
        }
        return false
    }

    private fun resolveWitchRevive(state: GameState): String? {
        if (state.witchUsedRevive) return null
        val actions = state.nightActions.filter { it.actionType == "WITCH_REVIVE" }
        if (actions.isEmpty()) return null

        state.witchUsedRevive = true
        return actions.first().targetId
    }

    private fun calculateDeaths(
        state: GameState,
        wolfKillTargets: List<String>,
        witchReviveId: String?,
        events: MutableList<GameEvent>
    ): List<Player> {
        val deaths = mutableListOf<Player>()

        for (targetId in wolfKillTargets) {
            val player = state.players.find { it.id == targetId && it.alive } ?: continue
            if (player.role is Atormentado) continue

            if (witchReviveId == player.id) {
                events.add(PlayerRevivedEvent(player.id, player.name))
                continue
            }

            player.alive = false
            deaths.add(player)
        }

        // Witch revive can save someone already dead (from previous rounds)
        if (witchReviveId != null && !wolfKillTargets.contains(witchReviveId)) {
            val revived = state.players.find { it.id == witchReviveId && !it.alive }
            if (revived != null) {
                revived.alive = true
                events.add(PlayerRevivedEvent(revived.id, revived.name))
            }
        }

        return deaths
    }

    private fun revealDeaths(deaths: List<Player>, state: GameState, events: MutableList<GameEvent>) {
        if (deaths.isEmpty()) return

        val deathInfos = deaths.map { player ->
            val revealed = if (state.config.revealRoleOnDeath) {
                player.role.shouldRevealRoleOnDeath(state, player)
            } else null

            DeathInfoEvent(
                playerId = player.id,
                playerName = player.name,
                revealedRole = revealed,
                cause = "LOBOS"
            )
        }

        events.add(DeathRevealedEvent(deathInfos))
    }

    private fun triggerOnDeathEffects(deaths: List<Player>, state: GameState, events: MutableList<GameEvent>) {
        for (player in deaths) {
            player.role.onDeath(state, player)

            when (player.role) {
                is Cazador -> {
                    val targets = state.vivos.map { it.id }
                    if (targets.isNotEmpty()) {
                        events.add(HunterPromptEvent(player.id, targets))
                    }
                    state.pendingHunterDeath = player.id
                }
                is Chivato -> {
                    val targets = state.vivos.map { it.id }
                    if (targets.isNotEmpty()) {
                        events.add(ChivatoPromptEvent(player.id, targets))
                    }
                }
            }
        }
    }

    private fun checkUsurpadorInheritance(deaths: List<Player>, state: GameState) {
        val usurpadores = state.vivos.filter { it.role is Usurpador }
        for (usurpador in usurpadores) {
            val uRole = usurpador.role as Usurpador
            if (deaths.any { it.id == uRole.targetId }) {
                val target = state.players.find { it.id == uRole.targetId }
                if (target != null) {
                    usurpador.role = target.role
                }
            }
        }
    }
}
