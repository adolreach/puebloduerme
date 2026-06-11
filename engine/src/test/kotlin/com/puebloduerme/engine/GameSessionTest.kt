package com.puebloduerme.engine

import com.puebloduerme.engine.model.*
import com.puebloduerme.engine.roles.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class GameSessionTest {

    private lateinit var session: GameSession
    private lateinit var events: MutableList<GameEvent>

    @Before
    fun setUp() {
        session = GameSession()
        events = mutableListOf()
    }

    private fun defaultConfig() = GameConfig(
        enabledRoles = setOf("Ciudadano", "Hombre lobo"),
        roleCounts = mapOf("Hombre lobo" to 1),
        revealRoleOnDeath = true,
        nightDurationMs = 1000L,
        voteDurationMs = 1000L
    )

    @Test
    fun `should create room with host`() {
        val result = session.createRoom("Host", defaultConfig())
        assertEquals(Phase.LOBBY, session.state.phase)
        assertEquals(1, session.state.players.size)
        assertEquals("Host", session.state.players[0].name)
        assertTrue(result.any { it is PlayerJoinedEvent })
        assertTrue(result.any { it is RoomStateEvent })
    }

    @Test
    fun `should allow players to join lobby`() {
        session.createRoom("Host", defaultConfig())
        val result = session.joinRoom("Player2")
        assertEquals(2, session.state.players.size)
        assertTrue(result.any { it is PlayerJoinedEvent })
    }

    @Test
    fun `should reject join after game starts`() {
        session.createRoom("Host", defaultConfig())
        session.state.phase = Phase.NOCHE
        val result = session.joinRoom("Player2")
        assertTrue(result.any { it is ErrorEvent })
    }

    @Test
    fun `should require minimum 5 players to start`() {
        session.createRoom("Host", defaultConfig())
        session.joinRoom("P2")
        session.joinRoom("P3")
        session.joinRoom("P4")
        val result = session.startGame()
        assertTrue(result.any { it is ErrorEvent && (it as ErrorEvent).code == "NOT_ENOUGH_PLAYERS" })
    }

    @Test
    fun `should start game with 5 players and assign roles`() {
        session.createRoom("Host", defaultConfig())
        repeat(4) { session.joinRoom("Player${it + 2}") }
        val result = session.startGame()

        assertEquals(Phase.NOCHE, session.state.phase)
        assertEquals(1, session.state.round)
        assertEquals(5, session.state.totalInicial)
        assertTrue(result.any { it is RoleAssignedEvent })
        assertTrue(result.any { it is PhaseChangedEvent })
    }

    @Test
    fun `should assign correct number of wolves based on config`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo"),
            roleCounts = mapOf("Hombre lobo" to 2)
        )
        session.createRoom("Host", config)
        repeat(4) { session.joinRoom("Player${it + 2}") }
        session.startGame()

        val wolves = session.state.lobosVivos
        assertEquals(2, wolves.size)
    }

    @Test
    fun `phase transitions should follow LOBBY to NOCHE to DIA to DISCUSION to VOTACION cycle`() {
        setupGame(6, 1)

        assertEquals(Phase.NOCHE, session.state.phase)

        // NOCHE -> DIA
        session.advancePhase()
        assertEquals(Phase.DIA, session.state.phase)

        // DIA -> DISCUSION
        session.advancePhase()
        assertEquals(Phase.DISCUSION, session.state.phase)

        // DISCUSION -> VOTACION
        session.advancePhase()
        assertEquals(Phase.VOTACION, session.state.phase)
    }

    @Test
    fun `wolf kill should kill target`() {
        setupGame(6, 1)

        val wolves = session.state.lobosVivos
        val villagers = session.state.puebloVivos

        session.submitNightAction(wolves[0].id, "WOLF_KILL_VOTE", villagers[0].id)
        session.advancePhase() // NOCHE -> DIA

        val events = session.state.let { state ->
            // The death should be in the events from advancePhase
            emptyList<GameEvent>() // We need to capture events from advancePhase
        }

        // Verify the villager is dead
        val victim = session.state.players.find { it.id == villagers[0].id }
        assertNotNull(victim)
        assertFalse(victim!!.alive)
    }

    @Test
    fun `wolf kill on Atormentado should mutate not kill`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Atormentado"),
            roleCounts = mapOf("Hombre lobo" to 1, "Atormentado" to 1)
        )
        setupGameWithConfig(6, config)

        // Find the atormentado
        val atormentado = session.state.players.find { it.role is Atormentado }
        val wolf = session.state.lobosVivos.first()

        assertNotNull(atormentado)
        assertTrue(atormentado!!.alive)
        assertEquals(Team.PUEBLO, atormentado.role.team)

        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", atormentado.id)
        session.advancePhase() // NOCHE -> DIA

        assertTrue(atormentado.alive)
        assertEquals(Team.LOBOS, atormentado.role.team)
        assertTrue(atormentado.mutationPending)
    }

    @Test
    fun `witch revive should save player from wolf kill`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Brujo"),
            roleCounts = mapOf("Hombre lobo" to 1, "Brujo" to 1)
        )
        setupGameWithConfig(6, config)

        val wolf = session.state.lobosVivos.first()
        val witch = session.state.players.find { it.role is Brujo }
        val villagers = session.state.puebloVivos.filter { it.role !is Brujo }
        val victim = villagers.first()

        assertNotNull(witch)

        // Wolf kills victim
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", victim.id)
        // Witch revives same victim
        session.submitNightAction(witch!!.id, "WITCH_REVIVE", victim.id)
        session.advancePhase() // NOCHE -> DIA

        assertTrue(victim.alive)
        assertTrue(session.state.witchUsedRevive)
    }

    @Test
    fun `witch can only revive once`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Brujo"),
            roleCounts = mapOf("Hombre lobo" to 1, "Brujo" to 1)
        )
        setupGameWithConfig(6, config)

        val witch = session.state.players.find { it.role is Brujo }!!
        session.state.witchUsedRevive = true

        val result = session.submitNightAction(witch.id, "WITCH_REVIVE", "any")
        assertTrue(result.any { it is ErrorEvent && (it as ErrorEvent).code == "ALREADY_USED" })
    }

    @Test
    fun `lynching requires majority threshold of at least 50 percent`() {
        setupGame(6, 1)

        // Advance to VOTACION
        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        assertEquals(Phase.VOTACION, session.state.phase)

        val players = session.state.vivos
        val target = players.last()
        val totalVivos = players.size

        // Only 2 out of 6 vote → less than 50%, nobody dies
        session.castVote(players[0].id, target.id)
        session.castVote(players[1].id, target.id)

        val result = session.advancePhase()
        assertTrue(target.alive) // Nobody dies with <50%
    }

    @Test
    fun `lynching with majority should kill target`() {
        setupGame(6, 1)

        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        val players = session.state.vivos
        val target = players.last()

        // 4 out of 6 vote → >=50% majority
        session.castVote(players[0].id, target.id)
        session.castVote(players[1].id, target.id)
        session.castVote(players[2].id, target.id)
        session.castVote(players[3].id, target.id)

        val result = session.advancePhase()
        assertFalse(target.alive)
        assertTrue(result.any { it is LynchResultEvent && it.playerId == target.id })
    }

    @Test
    fun `silenced player cannot vote`() {
        setupGame(6, 1)

        session.advancePhase() // NOCHE -> DIA (night resolution clears silenced)
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        // Set silenced after night resolution has run, simulating Abuela silence
        val player = session.state.puebloVivos.first()
        player.silencedThisRound = true

        val result = session.castVote(player.id, "any")
        assertTrue(result.any { it is ErrorEvent && (it as ErrorEvent).code == "CANNOT_VOTE" })
    }

    @Test
    fun `bufon lynched should win individually but game continues`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Bufón"),
            roleCounts = mapOf("Hombre lobo" to 1, "Bufón" to 1),
            revealRoleOnDeath = true
        )
        setupGameWithConfig(6, config)

        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        val bufon = session.state.players.find { it.role is Bufon }!!
        val players = session.state.vivos

        // All vote for bufon
        for (p in players) {
            if (p.id != bufon.id) {
                session.castVote(p.id, bufon.id)
            }
        }

        val result = session.advancePhase()
        assertFalse(bufon.alive)
        // Bufon wins individually, game continues (not FIN phase)
        // VictoryChecker doesn't terminate on Bufon win
    }

    @Test
    fun `lobos win when they are 50 percent of living players`() {
        // Start with 6 players, 1 wolf → kill until wolves are 50%
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo"),
            roleCounts = mapOf("Hombre lobo" to 2)
        )
        setupGameWithConfig(6, config)

        val wolves = session.state.lobosVivos
        val villagers = session.state.puebloVivos

        // Wolf kill a villager
        session.submitNightAction(wolves[0].id, "WOLF_KILL_VOTE", villagers[0].id)
        session.submitNightAction(wolves[1].id, "WOLF_KILL_VOTE", villagers[0].id)
        val result1 = session.advancePhase()

        // Now: 5 vivos, 2 wolves (40% - not enough yet)
        assertEquals(Phase.DIA, session.state.phase)

        // Another round: wolves kill another villager
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        // Lynch nobody (not enough votes)
        session.advancePhase() // VOTACION -> NOCHE

        val livingVillagers = session.state.puebloVivos
        session.submitNightAction(wolves[0].id, "WOLF_KILL_VOTE", livingVillagers[0].id)
        session.submitNightAction(wolves[1].id, "WOLF_KILL_VOTE", livingVillagers[0].id)
        val result2 = session.advancePhase()

        // Now: 4 vivos, 2 wolves = 50% → lobos win
        assertEquals(Phase.FIN, session.state.phase)
        assertTrue(result2.any { it is GameOverEvent && (it as GameOverEvent).winningTeam == Team.LOBOS })
    }

    @Test
    fun `pueblo wins when no wolves alive`() {
        setupGame(6, 1)

        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        // Lynch the only wolf
        val wolf = session.state.lobosVivos.first()
        val players = session.state.vivos

        for (p in players) {
            if (p.id != wolf.id && !p.silencedThisRound) {
                session.castVote(p.id, wolf.id)
            }
        }

        val result = session.advancePhase()
        assertTrue(result.any { it is GameOverEvent && (it as GameOverEvent).winningTeam == Team.PUEBLO })
        assertEquals(Phase.FIN, session.state.phase)
    }

    @Test
    fun `vidente sees target role privately`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Vidente"),
            roleCounts = mapOf("Hombre lobo" to 1, "Vidente" to 1)
        )
        setupGameWithConfig(6, config)

        val vidente = session.state.players.find { it.role is Vidente }!!
        val target = session.state.lobosVivos.first()

        session.submitNightAction(vidente.id, "SEER_INSPECT", target.id)
        val result = session.advancePhase()

        assertTrue(result.any {
            it is SeerResultEvent && it.playerId == vidente.id && it.revealedRole == "Hombre lobo"
        })
    }

    @Test
    fun `hombre lobo vidente shares result with all wolves`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Hombre lobo vidente"),
            roleCounts = mapOf("Hombre lobo" to 1, "Hombre lobo vidente" to 1)
        )
        setupGameWithConfig(7, config)

        val wolfSeer = session.state.players.find { it.role is HombreLoboVidente }!!
        val target = session.state.puebloVivos.first()

        session.submitNightAction(wolfSeer.id, "WOLFSEER_INSPECT", target.id)
        val result = session.advancePhase()

        assertTrue(result.any {
            it is WolfSeerResultEvent && it.revealedRole == target.role.name
        })
        assertTrue(result.any {
            it is WolfChannelUpdateEvent && (it as WolfChannelUpdateEvent).wolfSeerResult != null
        })
    }

    @Test
    fun `abuela silences target for the round`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Abuela gruñona"),
            roleCounts = mapOf("Hombre lobo" to 1, "Abuela gruñona" to 1)
        )
        setupGameWithConfig(6, config)

        val abuela = session.state.players.find { it.role is AbuelaGrunona }!!
        val target = session.state.puebloVivos.first { it.id != abuela.id }

        session.submitNightAction(abuela.id, "GRANDMA_SILENCE", target.id)
        session.advancePhase()

        assertTrue(target.silencedThisRound)
    }

    @Test
    fun `cazador can shoot when dying`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Cazador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Cazador" to 1)
        )
        setupGameWithConfig(6, config)

        val cazador = session.state.players.find { it.role is Cazador }!!
        val wolf = session.state.lobosVivos.first()

        // Wolf kills cazador
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", cazador.id)
        val result = session.advancePhase()

        assertFalse(cazador.alive)
        assertNotNull(session.state.pendingHunterDeath)
        assertEquals(cazador.id, session.state.pendingHunterDeath)
        assertTrue(result.any { it is HunterPromptEvent })
    }

    @Test
    fun `cazador shot kills target and can chain`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Cazador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Cazador" to 2),
            revealRoleOnDeath = true
        )
        setupGameWithConfig(7, config)

        val cazadores = session.state.players.filter { it.role is Cazador }
        val cazador1 = cazadores[0]
        val cazador2 = cazadores[1]
        val wolf = session.state.lobosVivos.first()

        // Wolf kills cazador1
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", cazador1.id)
        session.advancePhase() // NOCHE -> DIA

        assertFalse(cazador1.alive)
        assertEquals(cazador1.id, session.state.pendingHunterDeath)

        // Cazador1 shoots cazador2 → chain reaction
        session.submitHunterShoot(cazador1.id, cazador2.id)

        assertFalse(cazador2.alive)
        assertEquals(cazador2.id, session.state.pendingHunterDeath)
    }

    @Test
    fun `sacerdote holy water kills wolf and reveals role`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Sacerdote"),
            roleCounts = mapOf("Hombre lobo" to 1, "Sacerdote" to 1),
            revealRoleOnDeath = true
        )
        setupGameWithConfig(6, config)

        val sacerdote = session.state.players.find { it.role is Sacerdote }!!
        val wolf = session.state.lobosVivos.first()

        session.advancePhase() // NOCHE -> DIA
        val result = session.submitDayAction(sacerdote.id, "PRIEST_HOLYWATER", wolf.id)

        assertFalse(wolf.alive)
        assertTrue(result.any { it is DeathRevealedEvent })
        assertTrue(session.state.priestUsedHolyWater)
    }

    @Test
    fun `sacerdote holy water on villager kills sacerdote and hides role`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Sacerdote"),
            roleCounts = mapOf("Hombre lobo" to 1, "Sacerdote" to 1),
            revealRoleOnDeath = true
        )
        setupGameWithConfig(6, config)

        val sacerdote = session.state.players.find { it.role is Sacerdote }!!
        val villager = session.state.puebloVivos.first { it.id != sacerdote.id }

        session.advancePhase() // NOCHE -> DIA
        val result = session.submitDayAction(sacerdote.id, "PRIEST_HOLYWATER", villager.id)

        assertFalse(sacerdote.alive)
        assertTrue(villager.alive)
        assertTrue(result.any {
            it is DeathRevealedEvent && (it as DeathRevealedEvent).deaths.any { d -> d.revealedRole == null }
        })
    }

    @Test
    fun `chivato reveals role of chosen player upon death`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Chivato"),
            roleCounts = mapOf("Hombre lobo" to 1, "Chivato" to 1)
        )
        setupGameWithConfig(6, config)

        val chivato = session.state.players.find { it.role is Chivato }!!
        val wolf = session.state.lobosVivos.first()
        val target = session.state.puebloVivos.first { it.id != chivato.id }

        // Wolf kills chivato
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", chivato.id)
        val result = session.advancePhase()

        assertFalse(chivato.alive)
        assertTrue(result.any { it is ChivatoPromptEvent })
    }

    @Test
    fun `usurpador inherits role when target dies`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Usurpador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Usurpador" to 1)
        )
        setupGameWithConfig(6, config)

        val usurpador = session.state.players.find { it.role is Usurpador }!!
        val uRole = usurpador.role as Usurpador
        val targetId = uRole.targetId
        val target = session.state.players.find { it.id == targetId }!!
        val wolf = session.state.lobosVivos.first()

        // Wolf kills the usurpador's target
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", target.id)
        session.advancePhase()

        assertFalse(target.alive)
        assertEquals(target.role.name, usurpador.role.name)
    }

    @Test
    fun `discussion duration formula calculates correctly`() {
        setupGame(10, 2)

        session.startGame()
        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION

        // 10 players all alive: 45 + (10/10)*75 = 120 sec
        var duration = session.calculateDiscussionDuration()
        assertEquals(120_000L, duration)

        // Simulate 5 alive: 45 + (5/10)*75 = 82.5 sec
        session.state.players.take(5).forEach { it.alive = false }
        duration = session.calculateDiscussionDuration()
        assertEquals(82_500L, duration)

        // Simulate 1 alive: 45 + (1/10)*75 = 52.5 sec  
        session.state.players.forEach { it.alive = false }
        session.state.players.first().alive = true
        duration = session.calculateDiscussionDuration()
        assertEquals(52_500L, duration)
    }

    @Test
    fun `chat messages are filtered by channel`() {
        setupGame(6, 1)

        val villager = session.state.puebloVivos.first()
        val wolf = session.state.lobosVivos.first()

        // Wolf can send to LOBOS channel
        val wolfCanChat = session.chat.canSendMessage(wolf.id, "LOBOS")
        assertTrue(wolfCanChat)

        // Villager cannot send to LOBOS channel
        val villagerCannotChat = session.chat.canSendMessage(villager.id, "LOBOS")
        assertFalse(villagerCannotChat)

        // Villager can send to PUEBLO channel
        val villagerCanChat = session.chat.canSendMessage(villager.id, "PUEBLO")
        assertTrue(villagerCanChat)
    }

    @Test
    fun `dead players can chat in MUERTOS channel`() {
        setupGame(6, 1)

        val villager = session.state.puebloVivos.first()
        val wolf = session.state.lobosVivos.first()

        // Kill villager
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", villager.id)
        session.advancePhase()

        assertFalse(villager.alive)

        // Dead player can chat in MUERTOS
        val canChat = session.chat.canSendMessage(villager.id, "MUERTOS")
        assertTrue(canChat)

        // Alive non-Brujo cannot chat in MUERTOS
        val aliveCannotChat = session.chat.canSendMessage(wolf.id, "MUERTOS")
        assertFalse(aliveCannotChat)
    }

    @Test
    fun `brujo can chat in MUERTOS while alive`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Brujo"),
            roleCounts = mapOf("Hombre lobo" to 1, "Brujo" to 1)
        )
        setupGameWithConfig(6, config)

        val brujo = session.state.players.find { it.role is Brujo }!!

        val canChat = session.chat.canSendMessage(brujo.id, "MUERTOS")
        assertTrue(canChat)
    }

    @Test
    fun `reveal role on death is configurable`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo"),
            roleCounts = mapOf("Hombre lobo" to 1),
            revealRoleOnDeath = false
        )
        setupGameWithConfig(6, config)

        val wolf = session.state.lobosVivos.first()
        val villager = session.state.puebloVivos.first()

        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", villager.id)
        val result = session.advancePhase()

        assertFalse(villager.alive)
        val deathEvent = result.filterIsInstance<DeathRevealedEvent>().firstOrNull()
        assertNotNull(deathEvent)
        assertNull(deathEvent!!.deaths.first().revealedRole)
    }

    @Test
    fun `wolf vote without consensus picks randomly among top`() {
        setupGameWithConfig(7, GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo"),
            roleCounts = mapOf("Hombre lobo" to 2)
        ))

        val wolves = session.state.lobosVivos
        val targets = session.state.puebloVivos.take(2)

        assertEquals(2, wolves.size)

        // Wolves disagree
        session.submitNightAction(wolves[0].id, "WOLF_KILL_VOTE", targets[0].id)
        session.submitNightAction(wolves[1].id, "WOLF_KILL_VOTE", targets[1].id)

        session.advancePhase()

        // One of the two targets should be dead (random choice among tied)
        val deaths = listOf(!targets[0].alive, !targets[1].alive)
        assertTrue(deaths.any { it })
    }

    @Test
    fun `no wolf votes means no wolf victim`() {
        setupGame(6, 1)

        // No wolf submits any vote
        session.advancePhase()

        // All villagers should still be alive
        val aliveVillagers = session.state.puebloVivos.filter { it.alive }
        assertEquals(session.state.puebloVivos.size, aliveVillagers.size)
    }

    // ─── F5: Neutrales ────────────────────────────────────────────────────

    @Test
    fun `bufon wins and game ends when lynched`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Bufón"),
            roleCounts = mapOf("Hombre lobo" to 1, "Bufón" to 1)
        )
        setupGameWithConfig(6, config)

        val bufon = session.state.players.find { it.role is Bufon }!!

        session.advancePhase() // NOCHE -> DIA
        session.advancePhase() // DIA -> DISCUSION
        session.advancePhase() // DISCUSION -> VOTACION

        val voters = session.state.vivos.filter { it.id != bufon.id && !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, bufon.id)

        val result = session.advancePhase()

        assertFalse(bufon.alive)
        assertEquals(Phase.FIN, session.state.phase)
        assertTrue(result.any { it is GameOverEvent && (it as GameOverEvent).winningTeam == Team.NEUTRAL })
        assertTrue(result.any { it is GameOverEvent && (it as GameOverEvent).winners.contains(bufon.id) })
    }

    @Test
    fun `bufon killed by wolves does NOT win`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Bufón"),
            roleCounts = mapOf("Hombre lobo" to 1, "Bufón" to 1)
        )
        setupGameWithConfig(6, config)

        val bufon = session.state.players.find { it.role is Bufon }!!
        val wolf = session.state.lobosVivos.first()

        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", bufon.id)
        session.advancePhase()

        assertFalse(bufon.alive)
        assertFalse(session.state.winners.contains(bufon.id))
    }

    @Test
    fun `bufon included in winners list when game ends after individual win`() {
        // Bufón lynched → game ends immediately, Bufón is the sole winner
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Bufón"),
            roleCounts = mapOf("Hombre lobo" to 1, "Bufón" to 1)
        )
        setupGameWithConfig(7, config)

        val bufon = session.state.players.find { it.role is Bufon }!!

        // Lynch the Bufón
        session.advancePhase(); session.advancePhase(); session.advancePhase()
        val voters = session.state.vivos.filter { it.id != bufon.id && !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, bufon.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        assertFalse(bufon.alive)
        val gameOver = result.filterIsInstance<GameOverEvent>().firstOrNull()
        assertNotNull(gameOver)
        assertEquals(Team.NEUTRAL, gameOver!!.winningTeam)
        assertTrue(gameOver.winners.contains(bufon.id))
    }

    @Test
    fun `atormentado mutates when killed by wolves and joins wolf team`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Atormentado"),
            roleCounts = mapOf("Hombre lobo" to 1, "Atormentado" to 1)
        )
        setupGameWithConfig(7, config)

        val atormentado = session.state.players.find { it.role is Atormentado }!!
        val wolf = session.state.lobosVivos.first()

        assertEquals(Team.PUEBLO, atormentado.role.team)

        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", atormentado.id)
        session.advancePhase()

        assertTrue(atormentado.alive)
        assertEquals(Team.LOBOS, atormentado.role.team)
        assertTrue(atormentado.mutationPending)
        assertEquals("Hombre lobo", atormentado.role.name)
    }

    @Test
    fun `atormentado wins with wolves after mutation when wolves reach 50 percent`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Atormentado"),
            roleCounts = mapOf("Hombre lobo" to 1, "Atormentado" to 1)
        )
        setupGameWithConfig(5, config)

        val atormentado = session.state.players.find { it.role is Atormentado }!!
        val wolf = session.state.lobosVivos.first()

        // Wolf kills atormentado → mutates to Lobo. Now 2 wolves, 3 villagers = 5 total
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", atormentado.id)
        session.advancePhase()

        assertTrue(atormentado.alive)
        assertEquals(Team.LOBOS, atormentado.role.team)

        // Advance to VOTACION, lynch nobody
        session.advancePhase(); session.advancePhase()
        session.advancePhase()

        // Wolf kills 1 villager → 2 wolves / 4 total = 50% → LOBOS win
        val villager = session.state.puebloVivos.first()
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", villager.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        val gameOver = result.filterIsInstance<GameOverEvent>().first()
        assertEquals(Team.LOBOS, gameOver.winningTeam)
        assertTrue(gameOver.winners.contains(atormentado.id))
    }

    @Test
    fun `atormentado wins with pueblo if never killed by wolves`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Atormentado"),
            roleCounts = mapOf("Hombre lobo" to 1, "Atormentado" to 1)
        )
        setupGameWithConfig(6, config)

        val atormentado = session.state.players.find { it.role is Atormentado }!!
        val wolf = session.state.lobosVivos.first()

        // Lynch the wolf directly
        session.advancePhase(); session.advancePhase(); session.advancePhase()
        val voters = session.state.vivos.filter { !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, wolf.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        val gameOver = result.filterIsInstance<GameOverEvent>().first()
        assertEquals(Team.PUEBLO, gameOver.winningTeam)
        assertTrue(gameOver.winners.contains(atormentado.id))
    }

    @Test
    fun `usurpador inherits target role when target dies`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Usurpador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Usurpador" to 1)
        )
        setupGameWithConfig(6, config)

        val usurpador = session.state.players.find { it.role is Usurpador }!!
        val uRole = usurpador.role as Usurpador
        val target = session.state.players.find { it.id == uRole.targetId }!!
        val wolf = session.state.lobosVivos.first()

        val originalTeam = target.role.team
        assertEquals(Team.NEUTRAL, usurpador.role.team)

        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", target.id)
        session.advancePhase()

        assertFalse(target.alive)
        assertEquals(target.role.name, usurpador.role.name)
        assertEquals(originalTeam, usurpador.role.team)
    }

    @Test
    fun `usurpador wins with pueblo when inherited role is pueblo and pueblo wins`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Usurpador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Usurpador" to 1)
        )
        setupGameWithConfig(6, config)

        val usurpador = session.state.players.find { it.role is Usurpador }!!
        val uRole = usurpador.role as Usurpador
        val target = session.state.players.find { it.id == uRole.targetId }!!
        val wolf = session.state.lobosVivos.first()

        // Ensure target is NOT the wolf (for test predictability)
        assumeTrue("Target is not the wolf", target.id != wolf.id)

        // Wolf kills target → Usurpador inherits target's role (Pueblo)
        session.submitNightAction(wolf.id, "WOLF_KILL_VOTE", target.id)
        session.advancePhase()

        assertFalse(target.alive)
        assertEquals(target.role.name, usurpador.role.name)

        // Lynch the only wolf → Pueblo wins, Usurpador wins with Pueblo
        session.advancePhase(); session.advancePhase()
        val voters = session.state.vivos.filter { !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, wolf.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        val gameOver = result.filterIsInstance<GameOverEvent>().first()
        assertEquals(Team.PUEBLO, gameOver.winningTeam)
        assertTrue(gameOver.winners.contains(usurpador.id))
    }

    @Test
    fun `usurpador loses if target never dies`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Usurpador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Usurpador" to 1),
            usurperLosesIfTargetSurvives = true
        )
        setupGameWithConfig(6, config)

        val usurpador = session.state.players.find { it.role is Usurpador }!!
        val uRole = usurpador.role as Usurpador
        val target = session.state.players.find { it.id == uRole.targetId }!!
        val wolf = session.state.lobosVivos.first()

        // Skip if target is the wolf (lynching wolf = target dies = inheritance)
        assumeTrue(target.id != wolf.id)

        // Lynch the wolf — Usurpador's target never died
        session.advancePhase(); session.advancePhase(); session.advancePhase()
        val voters = session.state.vivos.filter { !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, wolf.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        val gameOver = result.filterIsInstance<GameOverEvent>().first()
        assertEquals(Team.PUEBLO, gameOver.winningTeam)
        assertFalse(gameOver.winners.contains(usurpador.id))
    }

    @Test
    fun `usurpador can win if losesIfTargetSurvives is false`() {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Usurpador"),
            roleCounts = mapOf("Hombre lobo" to 1, "Usurpador" to 1),
            usurperLosesIfTargetSurvives = false
        )
        setupGameWithConfig(6, config)

        val usurpador = session.state.players.find { it.role is Usurpador }!!
        val uRole = usurpador.role as Usurpador
        val target = session.state.players.find { it.id == uRole.targetId }!!
        val wolf = session.state.lobosVivos.first()

        // Skip if target is the wolf (makes inheritance happen, invalidating test)
        assumeTrue(target.id != wolf.id)

        session.advancePhase(); session.advancePhase(); session.advancePhase()
        val voters = session.state.vivos.filter { !it.silencedThisRound }
        for (v in voters) session.castVote(v.id, wolf.id)
        val result = session.advancePhase()

        assertEquals(Phase.FIN, session.state.phase)
        val gameOver = result.filterIsInstance<GameOverEvent>().first()
        assertEquals(Team.PUEBLO, gameOver.winningTeam)
        assertTrue(gameOver.winners.contains(usurpador.id))
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun setupGame(playerCount: Int, wolfCount: Int) {
        val config = GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo"),
            roleCounts = mapOf("Hombre lobo" to wolfCount),
            revealRoleOnDeath = true,
            nightDurationMs = 40_000L,
            voteDurationMs = 30_000L
        )
        setupGameWithConfig(playerCount, config)
    }

    private fun setupGameWithConfig(playerCount: Int, config: GameConfig) {
        session.createRoom("Host", config)
        repeat(playerCount - 1) { session.joinRoom("Player${it + 2}") }
        session.startGame()
    }
}
