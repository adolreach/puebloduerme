package com.puebloduerme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puebloduerme.engine.model.Phase
import com.puebloduerme.engine.model.Team
import com.puebloduerme.host.GameHost
import com.puebloduerme.host.WebSocketSession
import com.puebloduerme.protocol.*
import com.puebloduerme.app.ui.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: GameViewModel = viewModel()
                if (vm.showRoleInfo) {
                    RoleInfoScreen(onBack = { vm.showRoleInfo = false })
                } else {
                    AnimatedContent(
                    targetState = vm.gameStarted,
                    transitionSpec = {
                        fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 } togetherWith
                        fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 4 }
                    }
                ) { started ->
                    if (started) GameScreen(vm) else LobbyScreen(vm)
                }
                }
            }
        }
    }
}

data class ChatMsg(val fromId: String, val fromName: String, val text: String, val channel: String)
data class PlayerInfo(val id: String, val name: String, val alive: Boolean, val connected: Boolean)
data class ActionPrompt(val type: String, val targets: List<String>, val deadline: Long)

class GameViewModel : ViewModel() {
    var playerId by mutableStateOf("")
    var playerName by mutableStateOf("")
    var isHost by mutableStateOf(false)
    var roomCode by mutableStateOf("")
    var gameStarted by mutableStateOf(false)
    var currentPhase by mutableStateOf(Phase.LOBBY)
    var myRole by mutableStateOf("")
    var myTeam by mutableStateOf("")
    var myAbilities by mutableStateOf<List<String>>(emptyList())
    var myToken by mutableStateOf("")
    var players by mutableStateOf<List<PlayerInfo>>(emptyList())
    var statusMsg by mutableStateOf("")
    var phaseEndsAt by mutableStateOf(0L)
    var round by mutableStateOf(0)

    var chatMessages by mutableStateOf<List<ChatMsg>>(emptyList())
    var currentChannel by mutableStateOf("PUEBLO")
    var availableChannels by mutableStateOf(listOf("PUEBLO"))

    var activePrompt by mutableStateOf<ActionPrompt?>(null)
    var selectedTarget by mutableStateOf<String?>(null)
    var voteTally by mutableStateOf<Map<String, Int>>(emptyMap())
    var lastDeaths by mutableStateOf<List<String>>(emptyList())
    var lynchInfo by mutableStateOf<String?>(null)
    var gameOverInfo by mutableStateOf<String?>(null)
    var winningTeam by mutableStateOf("")
    var rolesRevealed by mutableStateOf(false)
    var showRoulette by mutableStateOf(false)
    var rouletteRole by mutableStateOf("")
    var rouletteTeam by mutableStateOf(Team.PUEBLO)
    var allRolesText by mutableStateOf("")
    var wolves by mutableStateOf<List<String>>(emptyList())
    var silenced by mutableStateOf(false)
    var showSeerEye by mutableStateOf(false)
    var showHolyWater by mutableStateOf(false)
    var showRevive by mutableStateOf(false)
    var remoteUrl by mutableStateOf("")
    var reconnectToken by mutableStateOf("")
    var showRoleInfo by mutableStateOf(false)

    var chatText by mutableStateOf("")
    var hostConfig by mutableStateOf<Map<String, Int>>(emptyMap())
    var useAutoPreset by mutableStateOf(true)

    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private val hosts = ConcurrentHashMap<String, GameHost>()
    private var clientSession: WebSocketSession? = null
    private var httpClient: HttpClient? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }

    fun createRoom(name: String) {
        playerName = name
        isHost = true
        useAutoPreset = true
        statusMsg = "Iniciando servidor..."

        hostConfig = mutableMapOf("Hombre lobo" to 1)

        server = embeddedServer(CIO, port = 8080) {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/game") {
                    val session = WebSocketSession { text -> viewModelScope.launch { send(Frame.Text(text)) } }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) handleIncoming(frame.readText(), session)
                        }
                    } catch (_: Exception) {} finally {
                        if (session.playerId.isNotEmpty())
                            hosts.values.find { it.getPlayer(session.playerId) != null }?.disconnectPlayer(session.playerId)
                    }
                }
            }
        }.start(wait = false)

        val host = GameHost()
        val config = com.puebloduerme.engine.model.GameConfig(
            enabledRoles = setOf("Ciudadano", "Hombre lobo", "Vidente", "Brujo", "Sacerdote", "Cazador", "Abuela gruñona", "Chivato", "Bufón", "Atormentado", "Usurpador", "Hombre lobo vidente"),
            roleCounts = hostConfig,
            nightDurationMs = 60_000L, voteDurationMs = 60_000L
        )
        host.createRoom(name, config)
        hosts[host.getRoomCode()] = host
        gameStarted = true
        roomCode = host.getRoomCode()
    }

    fun joinRoom(name: String, code: String, serverUrl: String = "ws://10.0.2.2:8080/game") {
        playerName = name; roomCode = code; isHost = false; remoteUrl = serverUrl
        statusMsg = "Conectando a $serverUrl..."
        viewModelScope.launch {
            try {
                httpClient?.close()
                httpClient = HttpClient(OkHttp)
                httpClient!!.webSocket(serverUrl) {
                    send(Frame.Text(json.encodeToString(JoinRoom(code, name))))
                    for (frame in incoming) if (frame is Frame.Text) handleServerMessage(frame.readText())
                }
            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
        }
    }

    fun reconnectToRoom() {
        if (reconnectToken.isBlank() || roomCode.isBlank()) return
        val url = remoteUrl.ifBlank { "ws://10.0.2.2:8080/game" }
        statusMsg = "Reconectando..."
        viewModelScope.launch {
            try {
                httpClient?.close()
                httpClient = HttpClient(OkHttp)
                httpClient!!.webSocket(url) {
                    send(Frame.Text(json.encodeToString(Reconnect(roomCode, reconnectToken))))
                    for (frame in incoming) if (frame is Frame.Text) handleServerMessage(frame.readText())
                }
            } catch (e: Exception) { statusMsg = "Reconexión fallida: ${e.message}" }
        }
    }

    fun startGame() { hosts[roomCode]?.startGame() }
    fun advancePhase() { hosts[roomCode]?.advancePhase() }

    fun sendChat() {
        if (chatText.isBlank()) return
        hosts[roomCode]?.handleMessage(playerId, json.encodeToString(ChatMessageMsg(currentChannel, chatText)))
        chatText = ""
    }

    fun submitAction(targetId: String) {
        val prompt = activePrompt ?: return
        val msg = when (prompt.type) {
            "SEER_INSPECT" -> NightActionMsg("SEER_INSPECT", targetId)
            "WOLFSEER_INSPECT" -> NightActionMsg("WOLFSEER_INSPECT", targetId)
            "GRANDMA_SILENCE" -> NightActionMsg("GRANDMA_SILENCE", targetId)
            "WITCH_REVIVE" -> NightActionMsg("WITCH_REVIVE", targetId)
            "WOLF_KILL_VOTE" -> NightActionMsg("WOLF_KILL_VOTE", targetId)
            "HUNTER_SHOOT" -> HunterShoot(targetId)
            "CHIVATO_REVEAL" -> ChivatoRevealMsg(targetId)
            "PRIEST_HOLYWATER" -> DayActionMsg("PRIEST_HOLYWATER", targetId)
            else -> return
        }
        hosts[roomCode]?.handleMessage(playerId, json.encodeToString(msg))
        activePrompt = null
    }

    fun castVote(targetId: String?) {
        hosts[roomCode]?.handleMessage(playerId, json.encodeToString(CastVote(targetId)))
    }

    private fun handleIncoming(text: String, session: WebSocketSession) {
        try {
            val jsonObj = json.decodeFromString<JsonObject>(text)
            val type = (jsonObj["type"] as? JsonPrimitive)?.contentOrNull ?: return
            when (type) {
                "CREATE_ROOM" -> {
                    val msg = json.decodeFromString<CreateRoom>(text)
                    val host = GameHost()
                    val config = com.puebloduerme.engine.model.GameConfig(
                        enabledRoles = msg.roleConfig.enabledRoles.toSet(),
                        roleCounts = msg.roleConfig.roleCounts,
                        nightDurationMs = msg.roleConfig.nightDurationSeconds * 1000L,
                        voteDurationMs = msg.roleConfig.voteDurationSeconds * 1000L
                    )
                    val events = host.createRoom(msg.hostName, config)
                    val joined = events.filterIsInstance<com.puebloduerme.engine.PlayerJoinedEvent>().firstOrNull()
                    val token = events.filterIsInstance<com.puebloduerme.engine.TokenAssignedEvent>().firstOrNull { joined != null && it.playerId == joined.playerId }
                    if (joined != null && token != null) { host.addSession(joined.playerId, token.token, session); hosts[host.getRoomCode()] = host }
                }
                "JOIN_ROOM" -> { val msg = json.decodeFromString<JoinRoom>(text); hosts[msg.roomCode]?.joinPlayer(msg.playerName, session) }
                else -> hosts.values.find { it.getPlayer(session.playerId) != null }?.handleMessage(session.playerId, text)
            }
        } catch (_: Exception) {}
    }

    private fun handleServerMessage(text: String) {
        try {
            val data = json.decodeFromString<JsonObject>(text)
            val type = (data["type"] as? JsonPrimitive)?.contentOrNull ?: return
            when (type) {
                "ROOM_STATE" -> {
                    val pArr = data["players"]?.let { it as? kotlinx.serialization.json.JsonArray }
                    players = pArr?.mapNotNull { el -> val o = el.jsonObject; PlayerInfo(o["id"].toString().trim('"'), o["name"].toString().trim('"'), o["alive"]?.toString()?.toBooleanStrictOrNull() ?: true, o["connected"]?.toString()?.toBooleanStrictOrNull() ?: true) } ?: emptyList()
                    roomCode = data["roomCode"].toString().trim('"')
                    currentPhase = try { Phase.valueOf(data["phase"].toString().trim('"')) } catch (_: Exception) { Phase.LOBBY }
                    if (!gameStarted && currentPhase != Phase.LOBBY) gameStarted = true
                }
                "ASSIGN_ROLE" -> {
                    myRole = data["role"].toString().trim('"'); myTeam = data["team"].toString().trim('"')
                    rouletteRole = myRole
                    rouletteTeam = try { Team.valueOf(myTeam) } catch (_: Exception) { Team.PUEBLO }
                    showRoulette = true
                    val abArr = data["abilities"]?.let { it as? kotlinx.serialization.json.JsonArray }
                    myAbilities = abArr?.map { it.toString().trim('"') } ?: emptyList()
                    availableChannels = when (myTeam) { "LOBOS" -> listOf("PUEBLO", "LOBOS"); else -> listOf("PUEBLO") }
                    if (myRole == "Brujo") availableChannels = availableChannels + "MUERTOS"
                }
                "PLAYER_TOKEN" -> { myToken = data["playerToken"].toString().trim('"'); reconnectToken = myToken }
                "PHASE_CHANGE" -> {
                    currentPhase = try { Phase.valueOf(data["phase"].toString().trim('"')) } catch (_: Exception) { Phase.LOBBY }
                    phaseEndsAt = data["endsAt"]?.toString()?.toLongOrNull() ?: 0L
                    round = data["publicInfo"]?.toString()?.let { it.replace("Ronda ", "").trim('"').toIntOrNull() } ?: 0
                    activePrompt = null; silenced = false
                }
                "PRIVATE_PROMPT" -> {
                    val actType = data["actionType"].toString().trim('"')
                    val targets = (data["eligibleTargets"] as? kotlinx.serialization.json.JsonArray)?.map { it.toString().trim('"') } ?: emptyList()
                    activePrompt = ActionPrompt(actType, targets, data["deadlineMs"]?.toString()?.toLongOrNull() ?: 0L)
                }
                "SEER_RESULT" -> { statusMsg = "Vidente: ${data["targetId"].toString().trim('"')} es ${data["revealedRole"].toString().trim('"')}"; showSeerEye = true }
                "WOLF_CHANNEL_UPDATE" -> {
                    wolves = (data["wolves"] as? kotlinx.serialization.json.JsonArray)?.map { it.toString().trim('"') } ?: emptyList()
                    val ws = data["wolfSeerResult"]?.jsonObject
                    if (ws != null) { statusMsg = "Lobo vidente: ${ws["targetId"].toString().trim('"')} es ${ws["revealedRole"].toString().trim('"')}" }
                }
                "DEATH_REVEAL" -> {
                    val deaths = (data["deaths"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonObject }
                    lastDeaths = deaths?.map { "${it["playerName"].toString().trim('"')} (${it["revealedRole"].toString().trim('"')}) - ${it["cause"].toString().trim('"')}" } ?: emptyList()
                    // Refresh channels in case current player died
                    val isAlive = players.find { it.id == playerId }?.alive ?: true
                    if (!isAlive) availableChannels = listOf("MUERTOS")
                }
                "REVIVE" -> { statusMsg = "${data["playerName"].toString().trim('"')} ha revivido"; showRevive = true }
                "SILENCED" -> { if (data["playerId"].toString().trim('"') == playerId) silenced = true }
                "VOTE_UPDATE" -> { voteTally = data["tally"]?.jsonObject?.entries?.associate { it.key to (it.value.toString().toIntOrNull() ?: 0) } ?: emptyMap() }
                "LYNCH_RESULT" -> { val n = data["playerName"]?.toString()?.trim('"'); lynchInfo = if (n != null) "$n (${data["revealedRole"]?.toString()?.trim('"')}) ha sido linchado" else "Nadie fue linchado" }
                "CHIVATO_REVEAL_PUBLIC" -> { statusMsg = "Chivato revela: ${data["targetName"].toString().trim('"')} es ${data["role"].toString().trim('"')}" }
                "CHAT_BROADCAST" -> { chatMessages = chatMessages + ChatMsg(data["fromId"].toString().trim('"'), data["fromName"].toString().trim('"'), data["text"].toString().trim('"'), data["channel"].toString().trim('"')) }
                "GAME_OVER" -> { gameOverInfo = "Fin del juego — ganan: ${data["winningTeam"].toString().trim('"')}"; winningTeam = data["winningTeam"].toString().trim('"'); rolesRevealed = true; val s = data["rolesSummary"] as? kotlinx.serialization.json.JsonArray; allRolesText = s?.joinToString("\n") { val o = it.jsonObject; "${o["playerName"].toString().trim('"')}: ${o["role"].toString().trim('"')} (${o["team"].toString().trim('"')})" } ?: "" }
                "ERROR" -> { statusMsg = data["message"].toString().trim('"') }
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() { super.onCleared(); server?.stop(1000, 2000); httpClient?.close(); hosts.clear() }
}

// ─── LOBBY ──────────────────────────────────────────────────────────────────

@Composable
fun LobbyScreen(vm: GameViewModel) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8080/game") }
    var showAdvanced by remember { mutableStateOf(false) }

    MedievalBackground()
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pueblo Duerme", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MC.BrightGold, fontFamily = FontFamily.Serif)
        ShieldBorder(color = MC.Gold)
        Spacer(Modifier.height(8.dp))
        Text("Juego de deducción social", color = MC.Parchment, fontSize = 15.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tu nombre", color = MC.MoonSilver) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MC.Parchment, unfocusedTextColor = MC.Parchment,
                focusedBorderColor = MC.Gold, unfocusedBorderColor = MC.StoneGray))
        Spacer(Modifier.height(16.dp))

        Button(onClick = { if (name.isNotBlank()) vm.createRoom(name) }, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MC.ForestGreen)) { Text("Crear sala (anfitrión)", fontFamily = FontFamily.Serif, color = Color.White, fontSize = 15.sp) }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { vm.showRoleInfo = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MC.Gold),
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Text("Ver todos los roles", fontFamily = FontFamily.Serif, color = MC.Gold, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))
        ShieldBorder()
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Código de sala", color = MC.MoonSilver) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MC.Parchment, unfocusedTextColor = MC.Parchment,
                focusedBorderColor = MC.Gold, unfocusedBorderColor = MC.StoneGray))
        Spacer(Modifier.height(8.dp))

        // Advanced: server URL toggle
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "▲ Ocultar opciones avanzadas" else "▼ Servidor remoto (opciones avanzadas)",
                color = MC.MoonSilver.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        AnimatedVisibility(visible = showAdvanced) {
            Column {
                OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it },
                    label = { Text("URL del servidor", color = MC.MoonSilver) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MC.Parchment, unfocusedTextColor = MC.Parchment,
                        focusedBorderColor = MC.MagicTeal, unfocusedBorderColor = MC.StoneGray))
                Spacer(Modifier.height(4.dp))
                Text("Ej: ws://192.168.1.50:8080/game", color = MC.StoneGray, fontSize = 11.sp)
                if (vm.reconnectToken.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { vm.reconnectToRoom() }) {
                        Text("Reconectar (token: ${vm.reconnectToken.take(6)}...)", color = MC.MagicTeal, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Button(onClick = { if (name.isNotBlank() && code.isNotBlank()) vm.joinRoom(name, code, serverUrl) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MC.RoyalPurple)) { Text("Unirse a sala", fontFamily = FontFamily.Serif) }

        if (vm.statusMsg.isNotBlank()) { Spacer(Modifier.height(16.dp)); Text(vm.statusMsg, color = MC.Gold, fontSize = 13.sp) }
    }
}

// ─── GAME SCREEN ────────────────────────────────────────────────────────────

@Composable
fun GameScreen(vm: GameViewModel) {
    if (vm.showRoulette) {
        RoleRevealRoulette(vm.rouletteRole, vm.rouletteTeam) { vm.showRoulette = false }
        return
    }

    if (vm.winningTeam == "NEUTRAL") {
        BufonVictoryScreen(
            playerName = vm.playerName,
            onContinue = { vm.winningTeam = ""; vm.gameOverInfo = null; vm.rolesRevealed = false }
        )
        return
    }

    MedievalBackground()
    Column(modifier = Modifier.fillMaxSize()) {
        PhaseTransitionAnimation(vm.currentPhase)
        PhaseTimer(vm.phaseEndsAt)
        PhaseHeader(vm)
        PlayerList(vm)

        AnimatedVisibility(visible = vm.lastDeaths.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            DeathAnnouncement(vm)
        }
        AnimatedVisibility(visible = vm.lynchInfo != null, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
            LynchAnnouncement(vm)
        }
        AnimatedVisibility(visible = vm.gameOverInfo != null, enter = scaleIn() + fadeIn(tween(800)), exit = fadeOut()) {
            GameOverPanel(vm)
        }

        // Spell overlays
        AnimatedVisibility(visible = vm.showSeerEye, enter = fadeIn(tween(300)), exit = fadeOut(tween(500))) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SeerEyeAnimation(onFinished = { vm.showSeerEye = false })
            }
        }
        AnimatedVisibility(visible = vm.showRevive, enter = fadeIn(tween(300)), exit = fadeOut(tween(500))) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ReviveAnimation(onFinished = { vm.showRevive = false })
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (vm.currentPhase == Phase.LOBBY && vm.isHost) {
                HostRoleConfigScreen(vm)
            } else {
                Crossfade(targetState = vm.currentPhase, animationSpec = tween(400)) { phase ->
                    when (phase) {
                        Phase.NOCHE -> NightActionsPanel(vm)
                        Phase.DIA, Phase.DISCUSION -> DayActionsPanel(vm)
                        Phase.VOTACION -> VotingPanel(vm)
                        Phase.FIN -> {}
                        else -> {}
                    }
                }
            }
        }

        if (vm.activePrompt != null) ActionTargetSelector(vm) else ChatPanel(vm)
    }
}

@Composable
fun PhaseHeader(vm: GameViewModel) {
    val phaseColors = mapOf(Phase.NOCHE to Color(0xFF070B19), Phase.DIA to Color(0xFFFF8F00), Phase.DISCUSION to Color(0xFF6A1B9A), Phase.VOTACION to Color(0xFFC62828), Phase.FIN to Color(0xFF1B5E20))

    Surface(color = phaseColors[vm.currentPhase] ?: Color.DarkGray, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (vm.currentPhase) {
                    Phase.NOCHE -> "Noche"; Phase.DIA -> "Amanecer"; Phase.DISCUSION -> "Discusión"; Phase.VOTACION -> "Votación"; Phase.FIN -> "Fin del juego"; Phase.LOBBY -> "Sala"
                }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MC.BrightGold, fontFamily = FontFamily.Serif)
                if (vm.round > 0) Text("Ronda ${vm.round}", color = MC.Parchment, fontSize = 13.sp)
            }
            if (vm.silenced) Text("Silenciado", color = MC.BloodRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }
    }
}

@Composable
fun PlayerList(vm: GameViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).horizontalScroll(rememberScrollState())) {
        vm.players.forEach { p ->
            AnimatedVisibility(visible = true, enter = fadeIn() + expandHorizontally()) {
                val bg = if (!p.alive) MC.AshGray else if (vm.wolves.contains(p.id) && vm.myTeam == "LOBOS") MC.RoyalPurple.copy(alpha = 0.4f) else MC.ForestGreen.copy(alpha = 0.3f)
                Text("${p.name}${if (!p.alive) " ☠" else ""}",
                    modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
fun DeathAnnouncement(vm: GameViewModel) {
    Surface(color = MC.BloodRed.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            vm.lastDeaths.forEach { d -> Text("Muerto: $d", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) }
        }
    }
}

@Composable
fun LynchAnnouncement(vm: GameViewModel) {
    vm.lynchInfo?.let { info ->
        Surface(color = MC.FireOrange.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth()) {
            Text(info, modifier = Modifier.padding(10.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }
    }
}

@Composable
fun GameOverPanel(vm: GameViewModel) {
    vm.gameOverInfo?.let { info ->
        Surface(color = MC.DarkWood, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(info, color = MC.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                if (vm.allRolesText.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Roles:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp); Text(vm.allRolesText, color = Color.White, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun NightActionsPanel(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Fase nocturna", fontSize = 18.sp, color = Color(0xFFBBDEFB), fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Text("Tu rol: ${vm.myRole}", fontSize = 16.sp, color = MC.Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        vm.myAbilities.forEach { ab -> Text("• $ab", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)) }
        if (vm.isHost) { Spacer(Modifier.height(20.dp)); Button(onClick = { vm.advancePhase() }, colors = ButtonDefaults.buttonColors(containerColor = MC.NightBlue)) { Text("Resolver noche", fontFamily = FontFamily.Serif) } }
    }
}

@Composable
fun DayActionsPanel(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Fase de día", fontSize = 18.sp, color = Color(0xFFFFF9C4), fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp)); Text(vm.statusMsg, fontSize = 13.sp, color = MC.Gold)
        if (vm.isHost) { Spacer(Modifier.height(20.dp)); Button(onClick = { vm.advancePhase() }, colors = ButtonDefaults.buttonColors(containerColor = MC.FireOrange)) { Text("Iniciar discusión", fontFamily = FontFamily.Serif) } }
    }
}

@Composable
fun VotingPanel(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Votación — elige a quién linchar", fontSize = 16.sp, color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        if (vm.voteTally.isNotEmpty()) { Text("Votos:", color = Color.Gray, fontSize = 12.sp); vm.voteTally.forEach { (id, count) -> val name = vm.players.find { it.id == id }?.name ?: id; Text("  $name: $count votos", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }; Spacer(Modifier.height(8.dp)) }
        LazyColumn {
            items(vm.players.filter { it.alive }) { p ->
                Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { vm.castVote(p.id) }, color = Color(0xFF3E2723), shape = RoundedCornerShape(8.dp)) {
                    Text("  ${p.name}", modifier = Modifier.padding(12.dp), color = MC.Parchment, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { vm.castVote(null) }, color = Color(0xFF616161), shape = RoundedCornerShape(8.dp)) {
                    Text("  Abstenerse", modifier = Modifier.padding(12.dp), color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp, fontFamily = FontFamily.Serif)
                }
            }
        }
        if (vm.isHost) { Spacer(Modifier.height(8.dp)); Button(onClick = { vm.advancePhase() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MC.BloodRed)) { Text("Resolver votación", fontFamily = FontFamily.Serif) } }
    }
}

@Composable
fun ActionTargetSelector(vm: GameViewModel) {
    val prompt = vm.activePrompt ?: return
    Surface(color = Color(0xFF263238), modifier = Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(when (prompt.type) {
                "SEER_INSPECT" -> "Elige a quién inspeccionar (Vidente)"
                "WOLFSEER_INSPECT" -> "Elige a quién inspeccionar (Lobo vidente)"
                "GRANDMA_SILENCE" -> "Elige a quién silenciar"
                "WITCH_REVIVE" -> "Elige a quién revivir"
                "WOLF_KILL_VOTE" -> "Elige a la víctima (Lobos)"
                "HUNTER_SHOOT" -> "Disparo del Cazador — elige objetivo"
                "CHIVATO_REVEAL" -> "Elige un jugador para revelar su rol"
                "PRIEST_HOLYWATER" -> "Agua bendita — elige objetivo"
                else -> prompt.type
            }, color = MC.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(6.dp))
            val targets = prompt.targets.mapNotNull { id -> vm.players.find { it.id == id } }
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(targets) { p ->
                    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { vm.submitAction(p.id) }, color = Color(0xFF37474F), shape = RoundedCornerShape(8.dp)) {
                        Text(p.name, modifier = Modifier.padding(10.dp), color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPanel(vm: GameViewModel) {
    val listState = rememberLazyListState()
    val channelMessages = vm.chatMessages.filter { it.channel == vm.currentChannel }
    LaunchedEffect(channelMessages.size) { if (channelMessages.isNotEmpty()) listState.animateScrollToItem(channelMessages.size - 1) }

    Surface(color = MC.DarkWood.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.availableChannels.forEach { ch ->
                    val isSel = vm.currentChannel == ch
                    val label = when (ch) { "PUEBLO" -> "Pueblo"; "LOBOS" -> "Lobos"; "MUERTOS" -> "Muertos"; else -> ch }
                    Surface(modifier = Modifier.clickable { vm.currentChannel = ch }, color = if (isSel) MC.Gold.copy(alpha = 0.3f) else Color(0xFF333333), shape = RoundedCornerShape(8.dp)) {
                        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (isSel) MC.Gold else Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(channelMessages) { msg ->
                    Column(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(msg.fromName, color = MC.Gold.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Serif)
                        Text(msg.text, color = MC.Parchment, fontSize = 13.sp)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = vm.chatText, onValueChange = { vm.chatText = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Mensaje...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MC.Gold),
                    singleLine = true)
                IconButton(onClick = { vm.sendChat() }) { Text("▶", color = MC.Gold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun darkColorScheme() = darkColorScheme(primary = MC.Gold, secondary = MC.ForestGreen, background = Color(0xFF0D1B2A), surface = Color(0xFF16213E))

// ─── HOST ROLE CONFIG ───────────────────────────────────────────────────

data class RolePreset(val players: String, val wolves: String, val neutrals: String, val village: String)

val presets = listOf(
    RolePreset("5-6", "1 Lobo", "—", "4-5"),
    RolePreset("7-9", "2 Lobos", "1 Bufón", "4-6"),
    RolePreset("10-12", "2 Lobos + 1 Lobo vidente", "1 Bufón", "6-8"),
    RolePreset("13-16", "3 Lobos + 1 Lobo vidente", "1 Bufón + 1 Atorm.", "8-10")
)

fun autoPreset(playerCount: Int): Map<String, Int> {
    return when {
        playerCount in 5..6 -> mapOf("Hombre lobo" to 1)
        playerCount in 7..9 -> mapOf("Hombre lobo" to 2, "Bufón" to 1)
        playerCount in 10..12 -> mapOf("Hombre lobo" to 2, "Hombre lobo vidente" to 1, "Bufón" to 1)
        playerCount in 13..16 -> mapOf("Hombre lobo" to 3, "Hombre lobo vidente" to 1, "Bufón" to 1, "Atormentado" to 1)
        else -> mapOf("Hombre lobo" to 1)
    }
}

@Composable
fun HostRoleConfigScreen(vm: GameViewModel) {
    val playerCount = vm.players.size
    var manualConfig by remember { mutableStateOf(vm.hostConfig.toMutableMap()) }

    LaunchedEffect(vm.useAutoPreset, playerCount) {
        if (vm.useAutoPreset) vm.hostConfig = autoPreset(playerCount)
    }

    Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Configuración de la partida", fontSize = 17.sp, color = MC.BrightGold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Text("${playerCount} jugadores en sala", fontSize = 13.sp, color = MC.MoonSilver)
        ShieldBorder(color = MC.Gold)

        // Toggle
        Row(Modifier.fillMaxWidth().background(MC.PanelBg, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Asignación automática", color = MC.Parchment, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text("Roles según nº de jugadores", color = MC.StoneGray, fontSize = 11.sp)
            }
            Switch(vm.useAutoPreset, { vm.useAutoPreset = it }, colors = SwitchDefaults.colors(checkedTrackColor = MC.ForestGreen, checkedThumbColor = MC.BrightGold))
        }
        ShieldBorder(color = MC.ForestGreen.copy(alpha = 0.4f))

        if (vm.useAutoPreset) {
            Text("Preset para $playerCount jugadores:", color = MC.ForestGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)

            Row(Modifier.fillMaxWidth().background(MC.DarkWood, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(6.dp)) {
                Text("Jug.", Modifier.weight(0.6f), color = MC.BrightGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Lobos", Modifier.weight(1.3f), color = MC.BloodRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Neutrales", Modifier.weight(1.1f), color = MC.RoyalPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Pueblo", Modifier.weight(0.8f), color = MC.ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            presets.forEachIndexed { i, p ->
                val active = when { playerCount in 5..6 -> i == 0; playerCount in 7..9 -> i == 1; playerCount in 10..12 -> i == 2; playerCount in 13..16 -> i == 3; else -> false }
                Row(Modifier.fillMaxWidth().background(if (active) MC.GoldBg else MC.PanelBg).border(if (active) 1.dp else 0.dp, if (active) MC.Gold.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(if (i == presets.lastIndex) 6.dp else 0.dp)).padding(5.dp)) {
                    Text(p.players, Modifier.weight(0.6f), color = if (active) MC.BrightGold else MC.MoonSilver, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    Text(p.wolves, Modifier.weight(1.3f), color = MC.BloodRed, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text(p.neutrals, Modifier.weight(1.1f), color = MC.RoyalPurple, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text(p.village, Modifier.weight(0.8f), color = MC.ForestGreen, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(4.dp)); ShieldBorder(color = MC.Gold.copy(alpha = 0.3f))
            val cfg = vm.hostConfig
            if (cfg.isNotEmpty()) {
                Text("Asignación:", color = MC.MoonSilver, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                cfg.forEach { (role, count) -> Text("  $count× $role", color = MC.Parchment, fontSize = 12.sp, fontFamily = FontFamily.Serif) }
                Text("  ${playerCount - cfg.values.sum()}× Ciudadanos", color = MC.StoneGray, fontSize = 12.sp, fontFamily = FontFamily.Serif)
            }
            ShieldBorder(color = MC.Gold.copy(alpha = 0.3f))
        } else {
            Text("Configuración manual:", color = MC.FireOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)

            val allRoles = listOf(
                "Hombre lobo" to "Lobos" to MC.BloodRed, "Hombre lobo vidente" to "Lobos" to MC.BloodRed,
                "Bufón" to "Neutral" to MC.RoyalPurple, "Atormentado" to "Neutral" to MC.RoyalPurple, "Usurpador" to "Neutral" to MC.RoyalPurple,
                "Vidente" to "Pueblo" to MC.ForestGreen, "Brujo" to "Pueblo" to MC.ForestGreen, "Cazador" to "Pueblo" to MC.ForestGreen,
                "Abuela gruñona" to "Pueblo" to MC.ForestGreen, "Sacerdote" to "Pueblo" to MC.ForestGreen, "Chivato" to "Pueblo" to MC.ForestGreen
            )
            allRoles.forEach { (pair, tc) ->
                val (rn, team) = pair; val count = manualConfig[rn] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$rn ($team)", Modifier.weight(1f), color = tc, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                    TextButton(onClick = { if (count > 0) { manualConfig[rn] = count - 1; vm.hostConfig = manualConfig.toMap() } }) { Text("−", color = MC.BloodRed, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    Text("$count", color = MC.Parchment, fontSize = 14.sp, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                    TextButton(onClick = { manualConfig[rn] = count + 1; vm.hostConfig = manualConfig.toMap() }) { Text("+", color = MC.ForestGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
            ShieldBorder(color = MC.FireOrange.copy(alpha = 0.4f))
            val total = manualConfig.values.sum()
            Text("Especiales: $total | Ciudadanos: ${playerCount - total}", color = if (total <= playerCount) MC.ForestGreen else MC.BloodRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        val cfg = vm.hostConfig; val total = cfg.values.sum(); val ok = total <= playerCount && playerCount >= 5
        Button(onClick = { vm.startGame() }, Modifier.fillMaxWidth().height(48.dp), enabled = ok, colors = ButtonDefaults.buttonColors(containerColor = MC.ForestGreen, disabledContainerColor = MC.StoneGray)) {
            Text(if (playerCount < 5) "Mínimo 5 jugadores" else "Iniciar partida", fontFamily = FontFamily.Serif, fontSize = 16.sp, color = if (ok) Color.White else MC.NightBlue)
        }
    }
}
