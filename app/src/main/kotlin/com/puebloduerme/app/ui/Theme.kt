package com.puebloduerme.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puebloduerme.engine.model.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos

// ─── HIGH-CONTRAST MEDIEVAL PALETTE ─────────────────────────────────────

object MC {
    val Parchment    = Color(0xFFFFF8E7)  // papel claro
    val DarkWood     = Color(0xFF2C1810)  // marrón muy oscuro
    val Gold         = Color(0xFFFFD700)
    val BrightGold   = Color(0xFFFFF176)  // oro brillante para contraste
    val BloodRed     = Color(0xFFD32F2F)  // rojo más vivo
    val NightBlue    = Color(0xFF070B19)  // casi negro azulado
    val MoonSilver   = Color(0xFFE0E0E0)  // más claro
    val ForestGreen  = Color(0xFF43A047)  // verde más vivo
    val RoyalPurple  = Color(0xFF7B1FA2)
    val StoneGray    = Color(0xFF9E9E9E)  // más claro
    val FireOrange   = Color(0xFFFF9800)  // naranja más vivo
    val MagicTeal    = Color(0xFF26C6DA)  // cyan más brillante
    val AshGray      = Color(0xFF546E7A)
    val SkyBlue      = Color(0xFF90CAF9)  // azul cielo más claro
    val Flame        = Color(0xFFFF5722)
    val DeepGreen    = Color(0xFF1B5E20)
    val IceWhite     = Color(0xFFF5F5F5)  // blanco hielo
    val CrimsonBg    = Color(0xFF4A0B0B)  // fondo rojo oscuro
    val GoldBg       = Color(0xFF3E2C00)  // fondo dorado oscuro
    val PurpleBg     = Color(0xFF1A0A2E)  // fondo púrpura
    val PanelBg      = Color(0xFF1E1E30)  // panel oscuro
}

// ─── MEDIEVAL BACKGROUND ────────────────────────────────────────────────

@Composable
fun MedievalBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MC.NightBlue, MC.PurpleBg, Color(0xFF0D1B2A))))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Stars
            for (i in 0..40) {
                drawCircle(Color.White.copy(alpha = 0.06f + (i % 7) * 0.02f), radius = 1.5f + (i % 4).toFloat(), center = Offset((i * w / 41f) + sin(i * 2.3f).toFloat() * 15f, (cos(i * 1.7f) * h * 0.3f + h * 0.4f).toFloat()))
            }
            // Subtle vignette
            drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))), radius = w * 1.2f, center = Offset(w / 2, h / 2))
        }
    }
}

// ─── DECORATIVE BORDERS ─────────────────────────────────────────────────

@Composable
fun OrnateBorder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val w = size.width
        // Center jewel
        drawCircle(MC.BrightGold, 4f, Offset(w / 2, 3f))
        drawCircle(MC.Gold.copy(alpha = 0.4f), 8f, Offset(w / 2, 3f))
        // Left line
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, MC.Gold)), Offset(12f, 3f), Offset(w / 2 - 10f, 3f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        // Right line
        drawLine(Brush.horizontalGradient(listOf(MC.Gold, Color.Transparent)), Offset(w / 2 + 10f, 3f), Offset(w - 12f, 3f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    }
}

@Composable
fun ShieldBorder(modifier: Modifier = Modifier, color: Color = MC.Gold) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f), Color.Transparent)), Offset(0f, 0f), Offset(size.width, 0f), 2f)
    }
}

@Composable
fun CornerOrnaments(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val s = 20f
        val c = MC.Gold.copy(alpha = 0.3f)
        // TL
        drawLine(c, Offset(8f, 8f), Offset(8f, 8f + s), 1.5f)
        drawLine(c, Offset(8f, 8f), Offset(8f + s, 8f), 1.5f)
        // TR
        drawLine(c, Offset(w - 8f, 8f), Offset(w - 8f, 8f + s), 1.5f)
        drawLine(c, Offset(w - 8f, 8f), Offset(w - 8f - s, 8f), 1.5f)
        // BL
        drawLine(c, Offset(8f, h - 8f), Offset(8f, h - 8f - s), 1.5f)
        drawLine(c, Offset(8f, h - 8f), Offset(8f + s, h - 8f), 1.5f)
        // BR
        drawLine(c, Offset(w - 8f, h - 8f), Offset(w - 8f, h - 8f - s), 1.5f)
        drawLine(c, Offset(w - 8f, h - 8f), Offset(w - 8f - s, h - 8f), 1.5f)
    }
}

// ─── PHASE TRANSITION ───────────────────────────────────────────────────

@Composable
fun PhaseTransitionAnimation(phase: Phase, modifier: Modifier = Modifier) {
    val isNight = phase == Phase.NOCHE
    val transition = remember { Animatable(if (isNight) 1f else 0f) }

    LaunchedEffect(isNight) { transition.animateTo(if (isNight) 1f else 0f, tween(1200, easing = EaseInOutCubic)) }

    Box(modifier = modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        val t = transition.value
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(lerpColor(MC.SkyBlue, MC.NightBlue, t))
            for (i in 0..35) drawCircle(Color.White.copy(alpha = t * 0.9f * (0.4f + (i % 6) * 0.1f)), radius = 1.2f + (i % 4).toFloat(), center = Offset((i * w / 36f) + sin(i * 2.9f).toFloat() * 8f, (cos(i * 1.5f) * h * 0.6f + h * 0.2f).toFloat()))
            drawRect(MC.DeepGreen.copy(alpha = 1f - t * 0.3f), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
        }
        // Sun
        Box(Modifier.offset(y = (t * 60).dp).size(50.dp).alpha(1f - t * 0.95f).clip(CircleShape).background(Brush.radialGradient(listOf(MC.BrightGold, MC.FireOrange, Color(0xFFE65100)))))
        // Moon
        Box(Modifier.offset(y = ((1f - t) * -60).dp).size(44.dp).alpha(t * 0.95f + 0.05f)) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(MC.MoonSilver, radius = size.minDimension / 2); drawCircle(MC.NightBlue, radius = size.minDimension / 2 * 0.72f, center = Offset(size.width * 0.28f, size.height * 0.28f)) }
        }
    }
}

// ─── DEATH ──────────────────────────────────────────────────────────────

@Composable
fun DeathAnimation(playerName: String, revealedRole: String?, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(500)); delay(2200); alpha.animateTo(0f, tween(400)); onFinished() }
    Box(modifier = modifier.fillMaxWidth().padding(8.dp).alpha(alpha.value).border(1.dp, MC.BloodRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).background(MC.CrimsonBg.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(32.dp)) {
                val f = 0.3f + (sin(System.currentTimeMillis() / 150.0).toFloat() * 0.15f)
                drawRect(MC.Parchment, Offset(size.width / 2 - 5f, size.height * 0.35f), Size(10f, size.height * 0.55f))
                drawLine(Color.Black, Offset(size.width / 2, size.height * 0.35f), Offset(size.width / 2, size.height * 0.18f), 1.5f)
                drawCircle(MC.Flame.copy(alpha = 0.5f + f), 8f + f * 3f, Offset(size.width / 2, size.height * 0.15f))
                drawCircle(MC.BrightGold.copy(alpha = 0.85f), 4f + f * 2f, Offset(size.width / 2, size.height * 0.15f))
            }
            Text("☠ Ha muerto $playerName", color = MC.Parchment, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            if (revealedRole != null) Text("Era $revealedRole", color = MC.BrightGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }
    }
}

// ─── SPELLS ─────────────────────────────────────────────────────────────

fun lerpColor(a: Color, b: Color, fraction: Float) = Color(a.red + (b.red - a.red) * fraction, a.green + (b.green - a.green) * fraction, a.blue + (b.blue - a.blue) * fraction, a.alpha + (b.alpha - a.alpha) * fraction)

@Composable
fun SeerEyeAnimation(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) { repeat(3) { pulse.animateTo(1f, tween(350)); pulse.animateTo(0f, tween(350)) }; delay(150); onFinished() }
    Box(Modifier.size(60.dp).background(MC.NightBlue.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).border(1.dp, MC.MagicTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2; val r = 16f + pulse.value * 6f
            drawCircle(MC.MagicTeal.copy(alpha = 0.25f + pulse.value * 0.5f), r + 8f, Offset(cx, cy))
            drawCircle(Color.White, r, Offset(cx, cy))
            drawCircle(MC.MagicTeal, r * 0.5f, Offset(cx, cy))
            drawCircle(Color.Black, r * 0.2f, Offset(cx, cy))
        }
    }
}

@Composable
fun ReviveAnimation(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val glow = remember { Animatable(0f) }
    val time = remember { mutableStateOf(0f) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { while (running) { time.value = (time.value + 0.06f) % 1f; delay(16) } }
    LaunchedEffect(Unit) {
        glow.animateTo(1f, tween(900))
        delay(700)
        glow.animateTo(0f, tween(350))
        running = false
        onFinished()
    }
    Box(Modifier.size(70.dp).background(MC.DeepGreen.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).border(1.dp, MC.ForestGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2; val g = glow.value
            drawCircle(Brush.radialGradient(listOf(MC.ForestGreen.copy(alpha = g * 0.7f), Color.Transparent)), 30f * g, Offset(cx, cy))
            for (i in 0..10) {
                val a = Math.toRadians(i * 33.0 + time.value * 360f)
                val d = 28f * (0.4f + sin(time.value * 4f + i).toFloat() * 0.4f)
                drawCircle(Color(0xFFC8E6C9).copy(alpha = g * 0.9f), 2.5f, Offset(cx + cos(a).toFloat() * d, cy + sin(a).toFloat() * d))
            }
        }
    }
}

// ─── BUFÓN VICTORY ──────────────────────────────────────────────────────

@Composable
fun BufonVictoryScreen(playerName: String, onContinue: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    val confetti = remember { (0..20).map { Animatable(0f) } }

    LaunchedEffect(Unit) {
        show = true
        delay(300)
        confetti.forEachIndexed { i, a ->
            launch { delay(i * 40L); a.animateTo(1f, tween(600 + i * 30, easing = EaseOutCubic)) }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MC.RoyalPurple, MC.PurpleBg, MC.NightBlue))), contentAlignment = Alignment.Center) {
        // Confetti particles
        Canvas(Modifier.fillMaxSize()) {
            confetti.forEachIndexed { i, a ->
                val x = (size.width * (0.1f + (i % 5) * 0.2f) + sin(i * 1.8f).toFloat() * 20f)
                val y = size.height * 0.3f - a.value * size.height * 0.4f + cos(i * 0.9f + a.value * 5f).toFloat() * 10f
                val colors = listOf(MC.BrightGold, MC.BloodRed, MC.MagicTeal, MC.ForestGreen, MC.FireOrange)
                drawCircle(colors[i % colors.size].copy(alpha = (1f - a.value) * 0.8f), radius = 4f + (i % 3) * 3f, center = Offset(x, y))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Jester hat
            Canvas(Modifier.size(80.dp)) {
                val cx = size.width / 2; val cy = size.height / 2
                drawCircle(MC.BrightGold, 8f, Offset(cx, cy - 28f))
                drawCircle(MC.BrightGold, 8f, Offset(cx - 16f, cy - 8f))
                drawCircle(MC.BrightGold, 8f, Offset(cx + 16f, cy - 8f))
                drawArc(MC.RoyalPurple, 0f, 180f, true, Offset(cx - 20f, cy + 4f), Size(40f, 16f))
            }

            Spacer(Modifier.height(16.dp))
            Text("¡El Bufón ha sido linchado!", color = MC.BrightGold, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("$playerName engañó al pueblo", color = MC.Parchment, fontSize = 16.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(4.dp))
            Text("y consiguió su objetivo.", color = MC.Parchment, fontSize = 16.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(20.dp))
            ShieldBorder(color = MC.Gold)
            Spacer(Modifier.height(20.dp))
            Text("🏆 El Bufón gana la partida 🏆", color = MC.BrightGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)

            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = onContinue,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MC.BrightGold)
            ) {
                Text("Continuar", fontFamily = FontFamily.Serif, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun PhaseTimer(endsAt: Long, modifier: Modifier = Modifier) {
    if (endsAt <= 0) return
    val totalMs = remember(endsAt) { endsAt - System.currentTimeMillis() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) { delay(200); now = System.currentTimeMillis() }
    }

    val remainingMs = (endsAt - now).coerceAtLeast(0)
    Box(modifier.fillMaxWidth().height(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = MC.IceWhite.copy(alpha = 0.1f))
            val fraction = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
            drawRect(color = when { remainingMs <= 5000 -> MC.BloodRed; remainingMs <= 15000 -> MC.Gold; else -> MC.ForestGreen }, size = Size(size.width * fraction, size.height))
        }
    }
}
