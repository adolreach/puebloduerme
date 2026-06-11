package com.puebloduerme.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puebloduerme.engine.model.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos

// ─── MEDIEVAL THEME ──────────────────────────────────────────────────────

object MedievalColors {
    val Parchment = Color(0xFFF5E6C8)
    val DarkWood = Color(0xFF3E2723)
    val Gold = Color(0xFFFFD700)
    val BloodRed = Color(0xFF8B0000)
    val NightBlue = Color(0xFF0D1B2A)
    val MoonSilver = Color(0xFFC0C0C0)
    val ForestGreen = Color(0xFF2E7D32)
    val RoyalPurple = Color(0xFF4A148C)
    val StoneGray = Color(0xFF616161)
    val FireOrange = Color(0xFFFF6F00)
    val MagicTeal = Color(0xFF00BCD4)
    val AshGray = Color(0xFF37474F)
    val SkyDay = Color(0xFF87CEEB)
    val CandleWax = Color(0xFFF5E6C8)
    val FlameOuter = Color(0xFFFF8F00)
    val FlameInner = Color(0xFFFFEB3B)
    val WaterTop = Color(0xFF4FC3F7)
    val WaterBottom = Color(0xFF0288D1)
    val ReviveGreen = Color(0xFF00E676)
    val ParticleGreen = Color(0xFFB2FF59)
    val HorizonGreen = Color(0xFF1B5E20)
}

val MedievalGold = Brush.horizontalGradient(listOf(Color(0xFFB8860B), Color(0xFFFFD700), Color(0xFFB8860B)))

fun lerpColor(a: Color, b: Color, fraction: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction
    )
}

@Composable
fun MedievalBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0A2E), Color(0xFF16213E), Color(0xFF0F3460))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..20) {
                val x = (i * w / 20f) + (sin(i * 1.5f) * 20f).toFloat()
                val y = h * 0.1f + (cos(i * 2.1f) * h * 0.05f).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = 2f + (i % 5).toFloat(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun MedievalDivider() {
    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            brush = MedievalGold,
            start = Offset(40f, 0f),
            end = Offset(size.width - 40f, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
        )
        drawCircle(color = Color(0xFFFFD700), radius = 5f, center = Offset(size.width / 2, 0f))
    }
}

// ─── PHASE TRANSITION (Sol ↔ Luna) ──────────────────────────────────────

@Composable
fun PhaseTransitionAnimation(phase: Phase, modifier: Modifier = Modifier) {
    val isNight = phase == Phase.NOCHE
    val transition = remember { Animatable(if (isNight) 1f else 0f) }

    LaunchedEffect(isNight) {
        transition.animateTo(
            targetValue = if (isNight) 1f else 0f,
            animationSpec = tween(durationMillis = 1200, easing = EaseInOutCubic)
        )
    }

    Box(modifier = modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        val tVal = transition.value

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val sky = lerpColor(MedievalColors.SkyDay, MedievalColors.NightBlue, tVal)
            drawRect(sky)

            for (i in 0..30) {
                val alpha = (tVal * 0.8f * (0.5f + (i % 5) * 0.1f))
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 1.5f + (i % 3).toFloat(),
                    center = Offset((i * w / 31f) + sin(i * 3.7f).toFloat() * 10f, (i * h / 8f) % h)
                )
            }

            drawRect(
                color = MedievalColors.HorizonGreen.copy(alpha = 1f - tVal * 0.3f),
                topLeft = Offset(0f, h * 0.75f),
                size = Size(w, h * 0.25f)
            )
        }

        Box(
            modifier = Modifier
                .offset(y = (tVal * 80).dp)
                .size(70.dp)
                .alpha(1f - tVal * 0.9f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFF176), Color(0xFFFF8F00), Color(0xFFFF6F00))))
        )

        Box(modifier = Modifier.offset(y = ((1f - tVal) * -80).dp).size(60.dp).alpha(tVal * 0.9f + 0.1f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFE0E0E0), radius = size.minDimension / 2)
                drawCircle(MedievalColors.NightBlue, radius = size.minDimension / 2 * 0.75f, center = Offset(size.width * 0.3f, size.height * 0.3f))
            }
        }
    }
}

// ─── DEATH ANIMATION ────────────────────────────────────────────────────

@Composable
fun DeathAnimation(playerName: String, revealedRole: String?, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.5f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(600))
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        delay(2500)
        alpha.animateTo(0f, tween(500))
        onFinished()
    }

    Box(modifier = modifier.fillMaxWidth().height(100.dp).alpha(alpha.value).scale(scale.value), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(60.dp)) {
            val cx = size.width / 2
            val bodyTop = size.height * 0.4f
            val bodyH = size.height * 0.5f
            drawRect(MedievalColors.CandleWax, topLeft = Offset(cx - 8f, bodyTop), size = Size(16f, bodyH))
            drawLine(Color.Black, Offset(cx, bodyTop), Offset(cx, size.height * 0.25f), 2f)
            val flicker = 0.3f + (sin(System.currentTimeMillis() / 200.0).toFloat() * 0.1f)
            drawCircle(MedievalColors.FlameOuter.copy(alpha = 0.5f + flicker), radius = 12f + flicker * 4f, center = Offset(cx, size.height * 0.2f))
            drawCircle(MedievalColors.FlameInner.copy(alpha = 0.8f), radius = 6f + flicker * 2f, center = Offset(cx, size.height * 0.2f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ha muerto $playerName", color = MedievalColors.BloodRed, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            if (revealedRole != null) Text("Era $revealedRole", color = MedievalColors.Gold, fontSize = 14.sp, fontFamily = FontFamily.Serif)
        }
    }
}

// ─── SPELL ANIMATIONS ───────────────────────────────────────────────────

@Composable
fun SeerEyeAnimation(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        repeat(3) { pulse.animateTo(1f, tween(400)); pulse.animateTo(0f, tween(400)) }
        delay(200)
        onFinished()
    }

    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2; val r = 25f + pulse.value * 8f
            drawCircle(MedievalColors.MagicTeal.copy(alpha = 0.2f + pulse.value * 0.4f), radius = r + 10f, center = Offset(cx, cy))
            drawCircle(Color.White, radius = r, center = Offset(cx, cy))
            drawCircle(MedievalColors.MagicTeal, radius = r * 0.55f, center = Offset(cx, cy))
            drawCircle(Color.Black, radius = r * 0.25f, center = Offset(cx, cy))
        }
    }
}

@Composable
fun HolyWaterAnimation(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val drop = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        drop.animateTo(1f, tween(800, easing = EaseOutBounce))
        delay(600)
        onFinished()
    }

    Box(modifier = modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val dropY = size.height * 0.2f + drop.value * size.height * 0.5f
            val dropRadius = 8f * (1f - drop.value * 0.3f)
            drawCircle(Brush.verticalGradient(listOf(MedievalColors.WaterTop, MedievalColors.WaterBottom)), radius = dropRadius, center = Offset(cx, dropY))
            for (i in 0..7) {
                val angle = Math.toRadians(i * 45.0 + drop.value * 30.0)
                drawLine(
                    Color(0xFFFFD700).copy(alpha = 0.4f * (1f - drop.value)),
                    Offset(cx, dropY),
                    Offset(cx + (cos(angle).toFloat() * (20f + drop.value * 15f)), dropY + (sin(angle).toFloat() * (20f + drop.value * 15f))),
                    1.5f
                )
            }
        }
    }
}

@Composable
fun ReviveAnimation(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        glow.animateTo(1f, tween(1000))
        delay(800)
        glow.animateTo(0f, tween(400))
        onFinished()
    }

    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { time.value = (time.value + 0.05f) % 1f; delay(16) } }

    Box(modifier = modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2; val g = glow.value
            drawCircle(
                Brush.radialGradient(listOf(MedievalColors.ReviveGreen.copy(alpha = g * 0.6f), MedievalColors.ReviveGreen.copy(alpha = 0f))),
                radius = 35f * g, center = Offset(cx, cy)
            )
            for (i in 0..11) {
                val angle = Math.toRadians(i * 30.0 + time.value * 360f)
                val dist = 40f * (0.5f + (sin(time.value * 3f + i).toFloat() * 0.5f))
                drawCircle(
                    MedievalColors.ParticleGreen.copy(alpha = g * 0.8f),
                    radius = 3f, center = Offset(cx + (cos(angle).toFloat() * dist), cy + (sin(angle).toFloat() * dist))
                )
            }
        }
    }
}

// ─── COUNTDOWN TIMER ─────────────────────────────────────────────────────

@Composable
fun PhaseTimer(endsAt: Long, modifier: Modifier = Modifier) {
    if (endsAt <= 0) return
    var remaining by remember { mutableStateOf((endsAt - System.currentTimeMillis()) / 1000) }

    LaunchedEffect(endsAt) {
        while (remaining > 0) {
            delay(1000)
            remaining = ((endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        }
    }

    Box(modifier = modifier.fillMaxWidth().height(3.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.White.copy(alpha = 0.15f))
            val total = 60f
            val fraction = (remaining / total).coerceIn(0f, 1f)
            drawRect(
                when { remaining <= 5 -> Color(0xFFFF5252); remaining <= 15 -> Color(0xFFFFD700); else -> Color(0xFF4CAF50) },
                size = Size(size.width * fraction, size.height)
            )
        }
    }
}
