package com.pythdom.callme.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pythdom.callme.data.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// Muted pastel tones in the spirit of Samsung's letter avatars.
private val avatarColors = listOf(
    Color(0xFF7BA7E8), Color(0xFF6CC3A8), Color(0xFFE8A07B), Color(0xFFB59AE6),
    Color(0xFFE88BA6), Color(0xFF8BC56C), Color(0xFFE6C15C), Color(0xFF6CB8D6),
)

/** Contact photo in a circle, or the first letter on a coloured background. */
@Composable
fun Avatar(name: String, photo: Bitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(avatarColors[abs(name.hashCode()) % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/** Loads a contact thumbnail off the main thread. */
@Composable
fun rememberThumbnail(uri: String?): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { ContactsRepository.loadThumbnail(context, uri) }
    }
    return bitmap
}
