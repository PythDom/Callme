package com.pythdom.callme.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pythdom.callme.data.ContactsRepository
import com.pythdom.callme.data.FavoriteContact
import com.pythdom.callme.data.PhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// ---- Step 1: pick a favorite -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(onBack: () -> Unit, onPicked: (FavoriteContact, PhoneNumber) -> Unit) {
    val context = LocalContext.current
    val favorites by produceState<List<FavoriteContact>?>(null) {
        val list = withContext(Dispatchers.IO) { runCatching { ContactsRepository.favorites(context) }.getOrDefault(emptyList()) }
        value = list
    }
    var choosingNumber by remember { mutableStateOf<FavoriteContact?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Who's calling?") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val list = favorites
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "No favorites with a phone number.\nStar a contact in the Contacts app, then come back.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(list, key = { it.contactId }) { contact ->
                    FavoriteRow(contact) {
                        if (contact.numbers.size == 1) onPicked(contact, contact.numbers.first())
                        else choosingNumber = contact
                    }
                    HorizontalDivider(Modifier.padding(start = 80.dp))
                }
            }
        }
    }

    choosingNumber?.let { contact ->
        AlertDialog(
            onDismissRequest = { choosingNumber = null },
            title = { Text(contact.name) },
            text = {
                Column {
                    contact.numbers.forEach { n ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { choosingNumber = null; onPicked(contact, n) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(ContactsRepository.formatNumber(n.number), style = MaterialTheme.typography.bodyLarge)
                            Text(n.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingNumber = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FavoriteRow(contact: FavoriteContact, onClick: () -> Unit) {
    val thumb = rememberThumbnail(contact.photoThumbUri)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, thumb, 48.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (contact.numbers.size == 1) "${contact.numbers[0].label}  ${ContactsRepository.formatNumber(contact.numbers[0].number)}"
                else "${contact.numbers.size} numbers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Step 2: pick the time ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimePickerScreen(
    contact: FavoriteContact,
    number: PhoneNumber,
    initial: Long?,
    onBack: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val context = LocalContext.current
    var triggerAt by remember { mutableStateOf(initial) }
    var quickMinutes by remember { mutableStateOf<Int?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(1_000)
        }
    }
    val valid = triggerAt?.let { it > now } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "When?" else "Change time") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name, rememberThumbnail(contact.photoThumbUri), 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(contact.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${number.label}  ${ContactsRepository.formatNumber(number.number)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Quick", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 15, 30).forEach { min ->
                    FilterChip(
                        selected = quickMinutes == min,
                        onClick = {
                            quickMinutes = min
                            triggerAt = System.currentTimeMillis() + min * 60_000L
                        },
                        label = { Text("In $min min") },
                    )
                }
            }

            OutlinedButton(onClick = { showDate = true }) {
                Icon(Icons.Filled.Event, null)
                Spacer(Modifier.width(8.dp))
                Text("Pick date & time")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                triggerAt?.let { formatWhen(context, it, now) } ?: "No time selected",
                style = MaterialTheme.typography.headlineSmall,
                color = if (triggerAt != null && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (triggerAt != null && !valid) {
                Text("That time has passed.", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { triggerAt?.let(onConfirm) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (initial == null) "Schedule call" else "Save") }
        }
    }

    if (showDate) {
        val zone = ZoneId.systemDefault()
        val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = todayUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                    if (pickedDate != null) showTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val start = LocalTime.now().plusMinutes(5)
        val state = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickedDate ?: LocalDate.now()
                    triggerAt = date.atTime(state.hour, state.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    quickMinutes = null
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
