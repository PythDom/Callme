package com.pythdom.callme.quick

import android.Manifest
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pythdom.callme.R
import com.pythdom.callme.data.ContactsRepository
import com.pythdom.callme.data.FavoriteContact
import com.pythdom.callme.data.PhoneNumber
import com.pythdom.callme.ui.Avatar
import com.pythdom.callme.ui.CallmeTheme
import com.pythdom.callme.ui.ContactPickerScreen
import com.pythdom.callme.ui.rememberThumbnail

/**
 * Sets the "call me in N min, by X" preset of a home-screen widget (launched by the
 * launcher with EXTRA_APPWIDGET_ID) or of the quick-settings tile (from the app, a tap on
 * an unconfigured tile, or a long-press on the tile).
 */
class QuickCallConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val isWidget get() = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // If the user backs out, the launcher must not place an unconfigured widget.
        if (isWidget) setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        val existing = if (isWidget) QuickCallStore.widget(this, widgetId) else QuickCallStore.tile(this)
        enableEdgeToEdge()
        setContent {
            CallmeTheme {
                QuickCallConfig(
                    title = if (isWidget) "Widget: call me in…" else "Quick-settings tile",
                    existing = existing,
                    onCancel = { finish() },
                    onSave = ::save,
                )
            }
        }
    }

    private fun save(preset: QuickCallPreset) {
        if (isWidget) {
            QuickCallStore.setWidget(this, widgetId, preset)
            QuickCallWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        } else {
            QuickCallStore.setTile(this, preset)
            QuickCallTileService.requestRefresh(this)
            // Offer to add the tile to quick settings (no-op dialog if it's already there).
            runCatching {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this, QuickCallTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_call_notification),
                    mainExecutor,
                ) { finish() }
            }.onFailure { finish() }
        }
    }

    companion object {
        const val EXTRA_TILE = "tile"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickCallConfig(
    title: String,
    existing: QuickCallPreset?,
    onCancel: () -> Unit,
    onSave: (QuickCallPreset) -> Unit,
) {
    val context = LocalContext.current
    var hasContacts by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
    LaunchedEffect(Unit) { if (!hasContacts) permission.launch(Manifest.permission.READ_CONTACTS) }

    // Start on the delay step when editing an existing preset.
    var picked by remember {
        mutableStateOf(
            existing?.let {
                FavoriteContact(it.contactId, it.lookupKey, it.name, it.photoThumbUri, listOf(PhoneNumber(it.number, it.numberLabel))) to
                    PhoneNumber(it.number, it.numberLabel)
            },
        )
    }
    var delay by remember { mutableIntStateOf(existing?.delayMinutes ?: 5) }

    if (!hasContacts) {
        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Text("Callme needs access to your contacts to list your favorites.", textAlign = TextAlign.Center)
        }
        return
    }

    val current = picked
    if (current == null) {
        ContactPickerScreen(onBack = onCancel, onPicked = { c, n -> picked = c to n })
        return
    }
    BackHandler { picked = null }
    val (contact, number) = current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { picked = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${number.label}  ${ContactsRepository.formatNumber(number.number)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("Call me in", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickCallPreset.DELAYS.forEach { min ->
                    FilterChip(selected = delay == min, onClick = { delay = min }, label = { Text("$min min") })
                }
            }
            Text(
                "One tap on the shortcut schedules a call from ${contact.name} $delay min later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    onSave(
                        QuickCallPreset(
                            contactId = contact.contactId,
                            lookupKey = contact.lookupKey,
                            name = contact.name,
                            number = number.number,
                            numberLabel = number.label,
                            photoThumbUri = contact.photoThumbUri,
                            delayMinutes = delay,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
        }
    }
}
