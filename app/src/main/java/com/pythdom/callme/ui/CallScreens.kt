package com.pythdom.callme.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythdom.callme.call.CallManager
import com.pythdom.callme.call.CallState
import com.pythdom.callme.data.CallerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Palette approximating Samsung One UI's dark call screen.
private val BgTop = Color(0xFF2A3644)
private val BgBottom = Color(0xFF0B0E12)
private val Primary = Color.White
private val Secondary = Color(0xFFC9CED6)
private val AcceptGreen = Color(0xFF38B54A)
private val DeclineRed = Color(0xFFE5483D)
private val ButtonOn = Color(0xFF4C9BFF)

@Composable
fun CallScreen(state: CallState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
    ) {
        when (state) {
            is CallState.Ringing -> IncomingCall(state.caller)
            is CallState.Active -> InCall(state.caller, state, ended = false)
            is CallState.Ended -> InCall(state.caller, null, ended = true)
            CallState.Idle -> Unit
        }
    }
}

@Composable
private fun CallerHeader(caller: CallerInfo, status: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            caller.name,
            color = Primary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        val line = listOf(caller.numberLabel, caller.number).filter { it.isNotBlank() }.joinToString("  ")
        Text(line, color = Secondary, fontSize = 17.sp)
        if (status != null) {
            Spacer(Modifier.height(6.dp))
            Text(status, color = Secondary, fontSize = 16.sp)
        }
    }
}

// ---- Incoming ---------------------------------------------------------------------------

@Composable
private fun IncomingCall(caller: CallerInfo) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        CallerHeader(caller, status = null)
        Spacer(Modifier.weight(1f))
        Avatar(caller.name, caller.photo, 150.dp)
        Spacer(Modifier.weight(1.4f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SwipeButton(AcceptGreen, Icons.Filled.Call, "Answer") { CallManager.answer() }
            SwipeButton(DeclineRed, Icons.Filled.CallEnd, "Decline") { CallManager.decline() }
        }
        Spacer(Modifier.height(56.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(remember { MutableInteractionSource() }, indication = null) { CallManager.decline() }
                .padding(bottom = 20.dp),
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, null, tint = Secondary, modifier = Modifier.size(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send message", color = Secondary, fontSize = 15.sp)
            }
        }
    }
}

/** Samsung-style button that must be dragged out of its circle to trigger. */
@Composable
private fun SwipeButton(color: Color, icon: ImageVector, description: String, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val threshold = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var triggered by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )
    val dragFraction = (offset.value.getDistance() / threshold).coerceIn(0f, 1f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
        // Idle hint ring, then a growing halo while dragging.
        Box(
            Modifier.size(84.dp)
                .scale(if (dragFraction > 0f) 1f + dragFraction * 1.4f else 1f + pulseProgress * 0.5f)
                .alpha(if (dragFraction > 0f) 0.25f else 0.35f * (1f - pulseProgress))
                .clip(CircleShape)
                .background(color),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (offset.value.getDistance() >= threshold && !triggered) {
                                triggered = true
                                onTrigger()
                            } else {
                                scope.launch { offset.animateTo(Offset.Zero, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring()) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                },
        ) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

// ---- In call ----------------------------------------------------------------------------

@Composable
private fun InCall(caller: CallerInfo, active: CallState.Active?, ended: Boolean) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active?.connectedAt) {
        while (active != null) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val status = if (ended) "Call ended" else formatDuration(now - (active?.connectedAt ?: now))

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        CallerHeader(caller, status)
        Spacer(Modifier.weight(1f))
        Avatar(caller.name, caller.photo, 120.dp)
        Spacer(Modifier.weight(1f))

        val enabled = !ended
        Column(Modifier.padding(horizontal = 28.dp).alpha(if (enabled) 1f else 0.4f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControl(Icons.Filled.FiberManualRecord, "Record")
                CallControl(Icons.Filled.Videocam, "Video call")
                CallControl(Icons.Filled.Bluetooth, "Bluetooth")
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControl(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", on = active?.speaker == true) {
                    if (enabled) CallManager.toggleSpeaker()
                }
                CallControl(Icons.Filled.MicOff, "Mute", on = active?.muted == true) {
                    if (enabled) CallManager.toggleMute()
                }
                CallControl(Icons.Filled.Dialpad, "Keypad")
            }
        }
        Spacer(Modifier.height(48.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(DeclineRed)
                .clickable(enabled = enabled) { CallManager.hangUp() },
        ) {
            Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(if (on) ButtonOn else Color.Transparent),
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Secondary, fontSize = 13.sp)
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
