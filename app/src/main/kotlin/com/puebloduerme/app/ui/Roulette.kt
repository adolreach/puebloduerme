package com.puebloduerme.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puebloduerme.engine.model.Team
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RoleCard(
    val name: String,
    val team: Team,
    val drawableName: String
)

val allRoleCards = listOf(
    RoleCard("Ciudadano", Team.PUEBLO, "ic_ciudadano"),
    RoleCard("Abuela\ngruñona", Team.PUEBLO, "ic_abuela"),
    RoleCard("Cazador", Team.PUEBLO, "ic_cazador"),
    RoleCard("Brujo", Team.PUEBLO, "ic_brujo"),
    RoleCard("Vidente", Team.PUEBLO, "ic_vidente"),
    RoleCard("Sacerdote", Team.PUEBLO, "ic_sacerdote"),
    RoleCard("Chivato", Team.PUEBLO, "ic_chivato"),
    RoleCard("Hombre lobo", Team.LOBOS, "ic_lobo"),
    RoleCard("Lobo\nvidente", Team.LOBOS, "ic_lobo_vidente"),
    RoleCard("Bufón", Team.NEUTRAL, "ic_bufon"),
    RoleCard("Atormentado", Team.NEUTRAL, "ic_atormentado"),
    RoleCard("Usurpador", Team.NEUTRAL, "ic_usurpador")
)

@Composable
fun RoleRevealRoulette(
    assignedRole: String,
    assignedTeam: Team,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(0) } // 0=countdown, 1=spinning, 2=slowing, 3=reveal
    val rotation = remember { Animatable(0f) }
    var currentIdx by remember { mutableStateOf(0) }
    var targetIdx by remember { mutableStateOf(0) }

    // Find target index
    LaunchedEffect(Unit) {
        targetIdx = allRoleCards.indexOfFirst { it.name.replace("\n", " ") == assignedRole.replace(" ", " ") }
        if (targetIdx < 0) targetIdx = 0
    }

    // Sound: countdown + spin
    LaunchedEffect(Unit) {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        try {
            // "3... 2... 1..."
            delay(600)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); delay(400)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); delay(400)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); delay(200)
            phase = 1
            
            // Fast spin
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            rotation.animateTo(6f * 360f, animationSpec = tween(2500, easing = LinearEasing))
            phase = 2
            
            // Slow down + click sounds
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 100)
            val remaining = ((targetIdx.toFloat() / allRoleCards.size) * 360f + 360f * 3f)
            rotation.animateTo(
                rotation.value + remaining,
                animationSpec = tween(1800, easing = EaseOutCubic)
            )
            
            // Tick sounds during slowdown
            for (i in 0..8) {
                delay(200 - i * 15L)
                tg.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 40)
            }
            
            phase = 3
            // Reveal fanfare
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 80); delay(100)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 80); delay(100)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
            delay(1500)
            onFinished()
        } finally {
            tg.release()
        }
    }

    val teamColor = when (assignedTeam) {
        Team.PUEBLO -> MC.ForestGreen
        Team.LOBOS -> MC.BloodRed
        Team.NEUTRAL -> MC.RoyalPurple
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MC.NightBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                0 -> {
                    // Countdown
                    Text("Tu rol es...", color = MC.BrightGold, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Spacer(Modifier.height(40.dp))
                    Canvas(Modifier.size(200.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2; val r = 80f
                        val segments = allRoleCards.size
                        val sliceAngle = 360f / segments
                        for (i in 0 until segments) {
                            val a = Math.toRadians((i * sliceAngle + rotation.value % 360).toDouble()).toFloat()
                            val c = when (allRoleCards[i].team) {
                                Team.PUEBLO -> MC.ForestGreen
                                Team.LOBOS -> MC.BloodRed
                                Team.NEUTRAL -> MC.RoyalPurple
                            }
                            drawArc(c.copy(alpha = 0.7f), (rotation.value + i * sliceAngle), sliceAngle, true, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                        }
                    }
                }
                1, 2 -> {
                    // Spinning wheel
                    Canvas(Modifier.size(220.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2; val r = 95f
                        val segments = allRoleCards.size
                        val sliceAngle = 360f / segments
                        for (i in 0 until segments) {
                            val c = when (allRoleCards[i].team) {
                                Team.PUEBLO -> MC.ForestGreen
                                Team.LOBOS -> MC.BloodRed
                                Team.NEUTRAL -> MC.RoyalPurple
                            }
                            drawArc(c.copy(alpha = 0.8f), (rotation.value + i * sliceAngle), sliceAngle - 2f, true, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                        }
                        drawCircle(MC.NightBlue, radius = 35f, center = Offset(cx, cy))
                        drawCircle(MC.Gold, radius = 37f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                    Text("Tu destino se decide...", color = MC.MoonSilver, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                }
                3 -> {
                    // Reveal
                    val scale = remember { Animatable(0.3f) }
                    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale.value)) {
                        // Card
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(MC.PanelBg, RoundedCornerShape(20.dp))
                                .border(3.dp, teamColor, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(80.dp)) {
                                val drawableId = allRoleCards.find { it.name.replace("\n", " ") == assignedRole.replace(" ", "") }?.let { card ->
                                    context.resources.getIdentifier(card.drawableName, "drawable", context.packageName)
                                } ?: 0
                                // Fallback: draw team-colored circle with initial
                                val letter = assignedRole.take(1)
                                drawCircle(MC.Gold.copy(alpha = 0.2f), radius = 35f)
                                drawCircle(teamColor, radius = 15f)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("¡Eres $assignedRole!", color = teamColor, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(when (assignedTeam) {
                            Team.PUEBLO -> "Bando: Pueblo"
                            Team.LOBOS -> "Bando: Lobos"
                            Team.NEUTRAL -> "Bando: Neutral"
                        }, color = MC.MoonSilver, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(12.dp))
                        val desc = allRolesInfo.find {
                            it.name.replace(" ", "") == assignedRole.replace(" ", "").replace("\n", "")
                        }?.description ?: ""
                        if (desc.isNotBlank()) {
                            ShieldBorder(color = teamColor.copy(alpha = 0.4f))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                desc,
                                color = MC.Parchment,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
