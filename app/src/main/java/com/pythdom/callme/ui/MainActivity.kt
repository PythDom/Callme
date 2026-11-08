package com.pythdom.callme.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythdom.callme.Config
import com.pythdom.callme.data.CallStore
import com.pythdom.callme.data.ContactsRepository
import com.pythdom.callme.data.FavoriteContact
import com.pythdom.callme.data.PhoneNumber
import com.pythdom.callme.data.ScheduledCall
import com.pythdom.callme.quick.QuickCallConfigActivity
import com.pythdom.callme.quick.QuickCallStore
import com.pythdom.callme.scheduling.CallScheduler
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface Screen {
    data object Home : Screen
    data object PickContact : Screen
    data class PickTime(val contact: FavoriteContact, val number: PhoneNumber, val editing: ScheduledCall?) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CallmeTheme { CallmeApp() } }
    }
}

@Composable
private fun CallmeApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.PickTime && (screen as Screen.PickTime).editing == null) Screen.PickContact else Screen.Home
    }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onSchedule = { screen = Screen.PickContact },
            onEdit = { call ->
                val contact = FavoriteContact(
                    call.contactId, call.lookupKey, call.name, call.photoThumbUri,
                    listOf(PhoneNumber(call.number, call.numberLabel)),
                )
                screen = Screen.PickTime(contact, contact.numbers.first(), call)
            },
        )
        Screen.PickContact -> ContactPickerScreen(
            onBack = { screen = Screen.Home },
            onPicked = { contact, number -> screen = Screen.PickTime(contact, number, null) },
        )
        is Screen.PickTime -> TimePickerScreen(
            contact = s.contact,
            number = s.number,
            initial = s.editing?.triggerAt,
            onBack = { screen = if (s.editing == null) Screen.PickContact else Screen.Home },
            onConfirm = { triggerAt -> screen = Screen.Home; save(context, s, triggerAt) },
        )
    }
}

private fun save(context: android.content.Context, s: Screen.PickTime, triggerAt: Long) {
    val call = s.editing?.copy(triggerAt = triggerAt) ?: ScheduledCall(
        contactId = s.contact.contactId,
        lookupKey = s.contact.lookupKey,
        name = s.contact.name,
        number = s.number.number,
        numberLabel = s.number.label,
        photoThumbUri = s.contact.photoThumbUri,
        triggerAt = triggerAt,
    )
    CallScheduler.schedule(context, call)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onSchedule: () -> Unit, onEdit: (ScheduledCall) -> Unit) {
    val context = LocalContext.current
    val calls by CallStore.calls.collectAsStateWithLifecycle()

    // Re-check requirements every time the user comes back (e.g. from system settings).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val reqs = remember(refresh) { requirements(context) }
    val presets = remember(refresh) { QuickCallStore.slots(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(1_000)
        }
    }
    var selected by remember { mutableStateOf<ScheduledCall?>(null) }

    val regular = calls.count { !it.isCallback }
    val canSchedule = regular < Config.MAX_PENDING_CALLS && reqs.filter { it.blocking }.all { it.ok }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Callme") },
                actions = {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(context, QuickCallConfigActivity::class.java)
                                .putExtra(QuickCallConfigActivity.EXTRA_TILE, true),
                        )
                    }) {
                        Icon(Icons.Filled.Bolt, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Quick call tile")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (canSchedule) onSchedule() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Schedule a call") },
                containerColor = if (canSchedule) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSchedule) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (reqs.any { !it.ok }) {
                item {
                    SetupCard(reqs) { req ->
                        if (req.permission != null) {
                            permissionLauncher.launch(arrayOf(req.permission))
                        } else if (req.settingsIntent != null) {
                            runCatching { context.startActivity(req.settingsIntent) }
                        }
                    }
                }
            }
            item {
                QuickPresetsRow(presets, enabled = reqs.filter { it.blocking }.all { it.ok }) { refresh++ }
            }
            item {
                Text(
                    "Upcoming calls  ($regular/${Config.MAX_PENDING_CALLS})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (regular >= Config.MAX_PENDING_CALLS) {
                    Text(
                        "Maximum of ${Config.MAX_PENDING_CALLS} waiting calls reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (calls.isEmpty()) {
                item {
                    Text(
                        "No calls scheduled. Tap \"Schedule a call\" to pick a favorite and a time.\n\n" +
                            "For one-tap calls, add the \"Call me\" widget from your home screen " +
                            "(long-press → Widgets → Callme) or set up the quick-settings tile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(calls, key = { it.id }) { call -> CallRow(call, now) { selected = call } }
        }
    }

    selected?.let { call ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(call.name) },
            text = { Text(if (call.isCallback) "Automatic callback at ${formatWhen(context, call.triggerAt, now)}." else formatWhen(context, call.triggerAt, now)) },
            confirmButton = {
                if (!call.isCallback) {
                    TextButton(onClick = { selected = null; onEdit(call) }) { Text("Change time") }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null; CallScheduler.cancel(context, call.id) }) {
                    Text(if (call.isCallback) "Cancel callback" else "Cancel call")
                }
            },
        )
    }
}

@Composable
private fun SetupCard(reqs: List<Requirement>, onFix: (Requirement) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Setup needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fake calls may not ring until these are allowed.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            reqs.forEach { req ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(
                        if (req.ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        null,
                        tint = if (req.ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(req.title + if (req.blocking) "" else " (recommended)", style = MaterialTheme.typography.bodyLarge)
                        Text(req.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!req.ok) TextButton(onClick = { onFix(req) }) { Text("Allow") }
                }
            }
        }
    }
}

@Composable
private fun CallRow(call: ScheduledCall, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb = rememberThumbnail(call.photoThumbUri)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(call.name, thumb, 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(call.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${call.numberLabel}  ${ContactsRepository.formatNumber(call.number)}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (call.isCallback) "Callback · " else "") + formatWhen(context, call.triggerAt, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---- Formatting helpers ------------------------------------------------------------------

fun formatWhen(context: android.content.Context, triggerAt: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(triggerAt).atZone(zone)
    val today = LocalDate.now(zone)
    val time = date.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
    val day = when (date.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return "$day $time · ${formatCountdown(triggerAt - now)}"
}

fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "now"
    val s = ms / 1000
    return when {
        s < 60 -> "in ${s}s"
        s < 3600 -> "in ${s / 60} min ${s % 60}s"
        s < 86_400 -> "in ${s / 3600} h ${(s % 3600) / 60} min"
        else -> "in ${s / 86_400} d ${(s % 86_400) / 3600} h"
    }
}
