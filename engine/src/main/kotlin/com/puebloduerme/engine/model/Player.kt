package com.puebloduerme.engine.model

import com.puebloduerme.engine.roles.Role

data class Player(
    val id: String,
    val name: String,
    var role: Role,
    var alive: Boolean = true,
    var silencedThisRound: Boolean = false,
    val token: String,
    var connected: Boolean = true,
    var hasVoted: Boolean = false,
    var mutationPending: Boolean = false,
    var targetId: String? = null
) {
    val isLobo: Boolean get() = role.team == Team.LOBOS
    val isVivo: Boolean get() = alive
    val canVote: Boolean get() = alive && !silencedThisRound
}
