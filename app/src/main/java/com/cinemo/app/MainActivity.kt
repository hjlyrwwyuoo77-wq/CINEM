package com.cinemo.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

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
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { File(filesDir, "cinemo-crash.txt").appendText("\nThread: ${thread.name}\n" + android.util.Log.getStackTraceString(error) + "\n") } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
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
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var startSeconds by rememberSaveable { mutableStateOf(0) }
    var endSeconds by rememberSaveable { mutableStateOf(0) }
    var isExporting by remember { mutableStateOf(false) }

    val exo = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorText = "Video playback failed: ${error.errorCodeName}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exo.duration > 0) {
                    durationMs = exo.duration
                    if (endSeconds == 0) {
                        endSeconds = ceil(exo.duration / 1000.0).toInt()
                    }
                }
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

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            errorText = null
            exportStatus = null
            durationMs = 0L
            startSeconds = 0
            endSeconds = 0
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

    val maxSeconds = if (durationMs > 0) {
        ceil(durationMs / 1000.0).toInt()
    } else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(BlueHi, Blue, BlueDeep)))
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                "CINEMO",
                style = TextStyle(
                    color = Fg,
                    fontSize = 18.sp,
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
                    "No video yet.\nTap Import Video to choose one.",
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
                videoName,
                style = TextStyle(
                    color = Fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            BasicText(
                "TRIM VIDEO",
                style = TextStyle(
                    color = BlueHi,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )

            BasicText(
                "Duration: ${maxSeconds}s",
                style = TextStyle(color = Muted, fontSize = 12.sp)
            )

            BasicText(
                "Start: ${startSeconds}s",
                style = TextStyle(color = Fg, fontSize = 14.sp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallButton("-5s", enabled = startSeconds >= 5) {
                    startSeconds = (startSeconds - 5).coerceAtLeast(0)
                }
                SmallButton("-1s", enabled = startSeconds > 0) {
                    startSeconds = (startSeconds - 1).coerceAtLeast(0)
                }
                SmallButton("+1s", enabled = startSeconds + 1 < endSeconds) {
                    startSeconds += 1
                }
                SmallButton("+5s", enabled = startSeconds + 5 < endSeconds) {
                    startSeconds += 5
                }
            }

            BasicText(
                "End: ${endSeconds}s",
                style = TextStyle(color = Fg, fontSize = 14.sp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallButton("-5s", enabled = endSeconds - 5 > startSeconds) {
                    endSeconds -= 5
                }
                SmallButton("-1s", enabled = endSeconds - 1 > startSeconds) {
                    endSeconds -= 1
                }
                SmallButton("+1s", enabled = endSeconds < maxSeconds) {
                    endSeconds = (endSeconds + 1).coerceAtMost(maxSeconds)
                }
                SmallButton("+5s", enabled = endSeconds + 5 <= maxSeconds) {
                    endSeconds = (endSeconds + 5).coerceAtMost(maxSeconds)
                }
            }

            BasicText(
                "Selected clip: ${(endSeconds - startSeconds).coerceAtLeast(0)} seconds",
                style = TextStyle(color = BlueHi, fontSize = 13.sp)
            )
        }

        if (errorText != null) {
            BasicText(
                errorText!!,
                style = TextStyle(color = ErrorRed, fontSize = 13.sp)
            )
        }

        if (exportStatus != null) {
            BasicText(
                exportStatus!!,
                style = TextStyle(color = BlueHi, fontSize = 12.sp)
            )
        }

        ActionButton(
            text = if (isExporting) "Exporting..." else "Export Video",
            enabled = videoUri != null && !isExporting && durationMs > 0
        ) {
            val chosen = videoUri
            if (chosen == null || durationMs <= 0) {
                exportStatus = "Wait until the video is ready."
            } else {
                val startMs = startSeconds * 1000L
                val endMs = minOf(endSeconds * 1000L, durationMs)

                if (endMs <= startMs) {
                    exportStatus = "Choose a valid start and end time."
                } else {
                    val baseDir = context.getExternalFilesDir(
                        Environment.DIRECTORY_MOVIES
                    ) ?: context.cacheDir
                    val outputDir = File(baseDir, "CINEMO")
                    outputDir.mkdirs()

                    val stamp = SimpleDateFormat(
                        "yyyyMMdd_HHmmss", Locale.US
                    ).format(Date())
                    val outputFile = File(outputDir, "CINEMO_$stamp.mp4")

                    isExporting = true
                    exportStatus = "Exporting your clip. Keep CINEMO open..."
                    errorText = null

                    try {
                        val clippedItem = MediaItem.Builder()
                            .setUri(Uri.parse(chosen))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(startMs)
                                    .setEndPositionMs(endMs)
                                    .build()
                            )
                            .build()

                        val editedItem = EditedMediaItem.Builder(clippedItem).build()

                        val transformer = Transformer.Builder(context)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult
                                ) {
                                    isExporting = false
                                    exportStatus =
                                        "Export complete! Saved to:\n${outputFile.absolutePath}"
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    isExporting = false
                                    outputFile.delete()
                                    exportStatus =
                                        "Export failed: ${exportException.message ?: "Unknown error"}"
                                }
                            })
                            .build()

                        transformer.start(editedItem, outputFile.absolutePath)
                    } catch (e: Exception) {
                        isExporting = false
                        outputFile.delete()
                        exportStatus = "Could not start export: ${e.message}"
                    }
                }
            }
        }

        ActionButton(text = "Import Video") {
            picker.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.VideoOnly
                )
            )
        }

        BasicText(
            "CINEMO Studio • Trim & Export",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.linearGradient(
                    listOf(BlueHi, Blue, BlueDeep)
                ) else Brush.linearGradient(
                    listOf(Line, Line)
                )
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (enabled) Color.White else Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}

@Composable
private fun SmallButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Line else Bg)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (enabled) Fg else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
