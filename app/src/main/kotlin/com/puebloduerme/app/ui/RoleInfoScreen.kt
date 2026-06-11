package com.puebloduerme.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puebloduerme.engine.model.Team

data class RoleInfo(
    val name: String,
    val team: Team,
    val description: String
)

val allRolesInfo = listOf(
    RoleInfo("Ciudadano", Team.PUEBLO, "Sin habilidad especial. Tu poder es tu voto y tu palabra en el debate. La mayoría del pueblo unido es imparable."),
    RoleInfo("Vidente", Team.PUEBLO, "Cada noche puedes inspeccionar a un jugador y ver su rol exacto. Solo tú recibes esta información. Úsala con sabiduría para guiar al pueblo."),
    RoleInfo("Cazador", Team.PUEBLO, "Al morir (de día o de noche), puedes disparar a un jugador y matarlo. Si no eliges en 30 segundos, pierdes el poder."),
    RoleInfo("Brujo", Team.PUEBLO, "Puedes hablar con los muertos en el chat. Una sola vez en la partida puedes usar tu conjuro nocturno para revivir a un jugador."),
    RoleInfo("Abuela gruñona", Team.PUEBLO, "Cada noche silencias a un jugador. Esa persona no podrá hablar ni votar durante el día siguiente."),
    RoleInfo("Sacerdote", Team.PUEBLO, "Una vez en la partida lanzas agua bendita sobre un jugador. Si es lobo, muere. Si no lo es, mueres tú y tu rol no se revela."),
    RoleInfo("Chivato", Team.PUEBLO, "Al morir, puedes revelar públicamente el rol de un jugador vivo. Esa información queda visible para siempre."),
    RoleInfo("Hombre lobo", Team.LOBOS, "Cada noche votas con los demás lobos a quién matar. Te ves con tus compañeros lobos y podéis hablar en el chat de lobos."),
    RoleInfo("Hombre lobo vidente", Team.LOBOS, "Como los demás lobos, votas a la víctima. Además, cada noche ves el rol de un jugador, y lo compartes con todos los lobos."),
    RoleInfo("Bufón", Team.NEUTRAL, "Tu único objetivo es ser linchado en una votación. Si lo consigues, ganas la partida individualmente, pero el juego continúa."),
    RoleInfo("Atormentado", Team.NEUTRAL, "Empiezas en el bando del pueblo. Pero si los lobos intentan matarte, no mueres: mutas a lobo en secreto y ganas con ellos."),
    RoleInfo("Usurpador", Team.NEUTRAL, "Al inicio recibes un objetivo secreto. Si ese jugador muere, adoptas su rol y su equipo. Si no muere, pierdes la partida.")
)

@Composable
fun RoleInfoScreen(onBack: () -> Unit) {
    var expandedIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MC.NightBlue)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("← Volver", color = MC.BrightGold, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                }
                Spacer(Modifier.weight(1f))
                Text("Todos los roles", color = MC.BrightGold, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            ShieldBorder(color = MC.Gold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(allRolesInfo) { index, role ->
                    val isExpanded = expandedIndex == index
                    val teamColor = when (role.team) {
                        Team.PUEBLO -> MC.ForestGreen
                        Team.LOBOS -> MC.BloodRed
                        Team.NEUTRAL -> MC.RoyalPurple
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedIndex = if (isExpanded) -1 else index },
                        color = if (isExpanded) MC.PanelBg else MC.PanelBg.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        role.name,
                                        color = if (isExpanded) MC.BrightGold else MC.Parchment,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Text(
                                        when (role.team) {
                                            Team.PUEBLO -> "Pueblo"
                                            Team.LOBOS -> "Lobos"
                                            Team.NEUTRAL -> "Neutral"
                                        },
                                        color = teamColor,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                                Text(
                                    if (isExpanded) "▲" else "▼",
                                    color = MC.MoonSilver,
                                    fontSize = 14.sp
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    ShieldBorder(color = teamColor.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        role.description,
                                        color = MC.Parchment,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Serif,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
