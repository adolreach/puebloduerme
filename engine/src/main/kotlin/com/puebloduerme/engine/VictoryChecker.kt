package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*

object VictoryChecker {

    fun check(state: GameState): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        val totalVivos = state.vivosActuales
        if (totalVivos == 0) return events

        val lobosVivos = state.lobosVivos.size

        // 1. Bufón — se registra al ser linchado en VoteResolver, aquí no se evalúa

        // 2. Lobos: >= 50% de vivos totales
        if (lobosVivos > 0 && lobosVivos.toDouble() / totalVivos >= 0.5) {
            events.add(buildGameOver(state, Team.LOBOS))
            state.phase = Phase.FIN
            return events
        }

        // 3. Pueblo: no queda ningún lobo vivo
        if (lobosVivos == 0) {
            events.add(buildGameOver(state, Team.PUEBLO))
            state.phase = Phase.FIN
            return events
        }

        return events
    }

    fun registerBufonWin(state: GameState, bufonId: String) {
        state.winners.add(bufonId)
    }

    private fun buildGameOver(state: GameState, winningTeam: Team): GameOverEvent {
        val winners = computeWinners(state, winningTeam)
        val rolesSummary = state.players.map { p ->
            RoleSummaryEvent(p.id, p.name, p.role.name, p.role.team, p.alive)
        }
        return GameOverEvent(winningTeam, winners, rolesSummary)
    }

    private fun computeWinners(state: GameState, winningTeam: Team): List<String> {
        val winnerIds = mutableSetOf<String>()

        for (player in state.players) {
            when {
                // Bufón que fue linchado gana individualmente
                player.role is Bufon && state.winners.contains(player.id) -> {
                    winnerIds.add(player.id)
                }
                // Atormentado mutado gana con lobos
                player.mutationPending -> {
                    if (winningTeam == Team.LOBOS) winnerIds.add(player.id)
                }
                // Atormentado no mutado gana con pueblo
                player.role is Atormentado && !player.mutationPending -> {
                    if (winningTeam == Team.PUEBLO) winnerIds.add(player.id)
                }
                // Usurpador que heredó gana con el equipo de su nuevo rol
                player.targetId != null && player.role !is Usurpador -> {
                    if (player.role.team == winningTeam) winnerIds.add(player.id)
                }
                // Usurpador que NO heredó: pierde (si config lo indica)
                player.role is Usurpador -> {
                    if (!state.config.usurperLosesIfTargetSurvives) {
                        // Si la config dice que no pierde, gana con pueblo
                        if (winningTeam == Team.PUEBLO) winnerIds.add(player.id)
                    }
                }
                // Miembro del equipo ganador
                player.role.team == winningTeam -> {
                    winnerIds.add(player.id)
                }
            }
        }

        return winnerIds.toList()
    }
}
