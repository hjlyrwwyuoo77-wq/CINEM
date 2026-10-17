package com.cinemo.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Bg = Color(0xFF05070B)
private val Line = Color(0xFF1D2837)
private val Blue = Color(0xFF2F7BFF)
private val BlueHi = Color(0xFF6AA5FF)
private val BlueDeep = Color(0xFF1747BD)
private val Muted = Color(0xFF7F8EA4)
private val Fg = Color(0xFFF4F7FB)
private val ErrorRed = Color(0xFFFF8E9B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CinemoApp() }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CinemoApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var videoName by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // One ExoPlayer for the whole screen. Media3 uses the phone's own
    // hardware decoders, so no extra native libraries are needed.
    val exo = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorText = "This video can't be played (" + error.errorCodeName + ")."
            }
        }
        exo.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.removeListener(listener)
            exo.release()
        }
    }

    // System photo picker, video only. On phones without the picker,
    // the androidx contract falls back to the system file chooser.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            errorText = null
            videoName = queryName(context, uri) ?: "Selected video"
            videoUri = uri.toString()
        }
    }

    LaunchedEffect(videoUri) {
        val chosen = videoUri
        if (chosen != null) {
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(chosen)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(BlueHi, Blue, BlueDeep)))
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "CINEMO",
                style = TextStyle(
                    color = Fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, Line, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri == null) {
                BasicText(
                    text = "No video yet.\nTap Import Video to choose one.",
                    style = TextStyle(
                        color = Muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exo
                            useController = true
                        }
                    }
                )
            }
        }

        if (videoUri != null) {
            BasicText(
                text = videoName,
                maxLines = 1,
                style = TextStyle(color = Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        }

        val problem = errorText
        if (problem != null) {
            BasicText(
                text = problem,
                style = TextStyle(color = ErrorRed, fontSize = 13.sp)
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(BlueHi, Blue, BlueDeep)))
                .clickable(role = Role.Button) {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "Import Video",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }

        BasicText(
            text = "Milestone 1: pick a video and play it.",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        )
    }
}

private fun queryName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (e: Exception) {
        null
    }
}
