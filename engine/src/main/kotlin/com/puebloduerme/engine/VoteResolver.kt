package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*
import kotlin.random.Random

class VoteResolver(private val state: GameState) {

    fun castVote(voterId: String, targetId: String?) {
        val voter = state.players.find { it.id == voterId } ?: return
        if (!voter.canVote) return
        state.votes[voterId] = targetId
        voter.hasVoted = true
    }

    fun resolve(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        val eligibleVoters = state.vivos.filter { !it.silencedThisRound }
        val totalEligible = eligibleVoters.size
        if (totalEligible == 0) {
            events.add(LynchResultEvent(null, null, null))
            cleanupVotes()
            return events
        }

        val tally = mutableMapOf<String, Int>()
        for ((_, targetId) in state.votes) {
            if (targetId != null) {
                tally[targetId] = (tally[targetId] ?: 0) + 1
            }
        }

        events.add(VoteTallyEvent(tally.toMap()))

        val maxVotes = tally.values.maxOrNull() ?: 0
        if (maxVotes == 0) {
            events.add(LynchResultEvent(null, null, null))
            cleanupVotes()
            return events
        }

        val threshold = (totalEligible / 2.0)
        if (maxVotes < threshold) {
            events.add(LynchResultEvent(null, null, null))
            cleanupVotes()
            return events
        }

        val topCandidates = tally.filter { it.value == maxVotes }.keys.toList()
        val lynchedId = if (topCandidates.size == 1) topCandidates.first()
        else topCandidates.random(Random)

        val lynched = state.players.find { it.id == lynchedId }
        if (lynched != null) {
            lynched.alive = false
            val revealedRole = if (state.config.revealRoleOnDeath) {
                lynched.role.shouldRevealRoleOnDeath(state, lynched)
            } else null

            events.add(LynchResultEvent(lynched.id, lynched.name, revealedRole))
            lynched.role.onDeath(state, lynched)

            if (lynched.role is Bufon) {
                VictoryChecker.registerBufonWin(state, lynched.id)
            }

            if (lynched.role is Cazador) {
                val targets = state.vivos.map { it.id }
                if (targets.isNotEmpty()) {
                    events.add(HunterPromptEvent(lynched.id, targets))
                }
                state.pendingHunterDeath = lynched.id
            }

            if (lynched.role is Chivato) {
                val targets = state.vivos.map { it.id }
                if (targets.isNotEmpty()) {
                    events.add(ChivatoPromptEvent(lynched.id, targets))
                }
            }

            checkUsurpadorInheritance(lynched, state)
        }

        cleanupVotes()
        return events
    }

    fun resolveHunterShoot(targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val hunterId = state.pendingHunterDeath ?: return events
        val hunter = state.players.find { it.id == hunterId } ?: return events
        val target = state.players.find { it.id == targetId && it.alive } ?: return events

        target.alive = false
        state.pendingHunterDeath = null

        val revealedRole = if (state.config.revealRoleOnDeath) {
            target.role.shouldRevealRoleOnDeath(state, target)
        } else null

        val deathInfo = DeathInfoEvent(target.id, target.name, revealedRole, "CAZADOR")
        events.add(DeathRevealedEvent(listOf(deathInfo)))

        target.role.onDeath(state, target)

        if (target.role is Cazador) {
            val targets = state.vivos.map { it.id }
            if (targets.isNotEmpty()) {
                events.add(HunterPromptEvent(target.id, targets))
            }
            state.pendingHunterDeath = target.id
        }

        if (target.role is Chivato) {
            val targets = state.vivos.map { it.id }
            if (targets.isNotEmpty()) {
                events.add(ChivatoPromptEvent(target.id, targets))
            }
        }

        checkUsurpadorInheritance(target, state)
        return events
    }

    fun resolveChivatoReveal(targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val target = state.players.find { it.id == targetId && it.alive } ?: return events

        state.revealedRoles[target.id] = target.role.name
        events.add(ChivatoRevealedEvent(target.id, target.name, target.role.name))
        state.chivatoRevealTarget = null

        return events
    }

    fun resolvePriestHolyWater(priestId: String, targetId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        if (state.priestUsedHolyWater) {
            events.add(ErrorEvent("ALREADY_USED", "El agua bendita ya fue usada"))
            return events
        }

        val priest = state.players.find { it.id == priestId && it.alive } ?: return events
        if (priest.role !is Sacerdote) {
            events.add(ErrorEvent("NOT_PRIEST", "No eres el Sacerdote"))
            return events
        }

        val target = state.players.find { it.id == targetId && it.alive } ?: return events
        state.priestUsedHolyWater = true

        if (target.role.team == Team.LOBOS) {
            target.alive = false
            val revealedRole = if (state.config.revealRoleOnDeath) target.role.name else null
            val info = DeathInfoEvent(target.id, target.name, revealedRole, "SACERDOTE")
            events.add(DeathRevealedEvent(listOf(info)))
            target.role.onDeath(state, target)

            if (target.role is Cazador) {
                val targets = state.vivos.map { it.id }
                if (targets.isNotEmpty()) {
                    events.add(HunterPromptEvent(target.id, targets))
                }
                state.pendingHunterDeath = target.id
            }

            checkUsurpadorInheritance(target, state)
        } else {
            priest.alive = false
            val info = DeathInfoEvent(priest.id, priest.name, null, "SACERDOTE")
            events.add(DeathRevealedEvent(listOf(info)))

            if (priest.role is Cazador) {
                val targets = state.vivos.map { it.id }
                if (targets.isNotEmpty()) {
                    events.add(HunterPromptEvent(priest.id, targets))
                }
                state.pendingHunterDeath = priest.id
            }
        }

        return events
    }

    private fun checkUsurpadorInheritance(deadPlayer: Player, state: GameState) {
        val usurpadores = state.vivos.filter { it.role is Usurpador }
        for (usurpador in usurpadores) {
            val uRole = usurpador.role as Usurpador
            if (deadPlayer.id == uRole.targetId) {
                usurpador.role = deadPlayer.role
            }
        }
    }

    private fun cleanupVotes() {
        state.votes.clear()
        state.players.forEach { it.hasVoted = false }
    }
}
