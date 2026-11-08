package com.pythdom.callme.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pythdom.callme.quick.QuickCallConfigActivity
import com.pythdom.callme.quick.QuickCallPreset
import com.pythdom.callme.quick.QuickCallStore
import com.pythdom.callme.quick.QuickCallTrigger

/**
 * Row of in-app quick presets ("X min, by Y"). Tap a set slot to schedule the call,
 * tap an empty slot to define it, long-press a set slot to change or remove it.
 *
 * [presets] is read by the caller (and re-read on resume) so edits made in
 * QuickCallConfigActivity show up when the user comes back.
 */
@Composable
fun QuickPresetsRow(presets: List<QuickCallPreset?>, enabled: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<Int?>(null) }

    Column {
        Text("Quick calls", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap to get a call in a few minutes. Long-press to change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { index, preset ->
                Box(Modifier.weight(1f)) {
                    if (preset == null) {
                        EmptySlot { editSlot(context, index) }
                    } else {
                        PresetSlot(
                            preset,
                            enabled,
                            onClick = { if (enabled) QuickCallTrigger.fire(context, preset); onChanged() },
                            onLongClick = { menuFor = index },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { index ->
        val preset = presets.getOrNull(index)
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("Quick call ${index + 1}") },
            text = { Text(preset?.let { "${it.name}, calling ${it.delayMinutes} min after a tap." } ?: "") },
            confirmButton = {
                TextButton(onClick = { menuFor = null; editSlot(context, index) }) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = {
                    menuFor = null
                    QuickCallStore.setSlot(context, index, null)
                    onChanged()
                }) { Text("Remove") }
            },
        )
    }
}

private fun editSlot(context: Context, index: Int) {
    context.startActivity(
        Intent(context, QuickCallConfigActivity::class.java).putExtra(QuickCallConfigActivity.EXTRA_SLOT, index),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetSlot(preset: QuickCallPreset, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(preset.name, rememberThumbnail(preset.photoThumbUri), 40.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                preset.name.trim().substringBefore(' '),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                "${preset.delayMinutes} min",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptySlot(onClick: () -> Unit) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(6.dp))
            Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(" ", style = MaterialTheme.typography.labelMedium)
        }
    }
}
