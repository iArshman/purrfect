package me.eternal.purrfect.ui.manager.pages.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.eternal.purrfect.LogLine
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.logger.LogLevel
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin

@Composable
fun RexModsToolsAndLogsCard(
    context: RemoteSideContext,
    refreshNonce: Int
) {
    val skin = LocalPurrfectSkin.current
    val clipboardManager = LocalClipboardManager.current

    var logRefreshTrigger by remember { mutableIntStateOf(0) }
    var rexLogs by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(false) }

    LaunchedEffect(refreshNonce, logRefreshTrigger) {
        isLoadingLogs = true
        rexLogs = withContext(Dispatchers.IO) {
            context.log.getRexModsLogs(maxLines = 250)
        }
        isLoadingLogs = false
    }

    // Auto-poll every 3 seconds while viewing the RexMods screen
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val latest = withContext(Dispatchers.IO) {
                context.log.getRexModsLogs(maxLines = 250)
            }
            if (latest.size != rexLogs.size || latest.lastOrNull()?.toString() != rexLogs.lastOrNull()?.toString()) {
                rexLogs = latest
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Classic Emoji Header Bar Preview Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rexmods_emoji_preview_card"),
            shape = RoundedCornerShape(18.dp),
            color = skin.cardOverlayColor.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Classic Emoji Chat Header & Send-To Preview",
                    color = skin.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Below the Account icon on the Chat Homescreen (and on the Send-To screen), text filters are replaced with classic emojis + numbered 50-friend shortcut lists:",
                    color = skin.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val samplePills = listOf(
                        "💬" to "All",
                        "🟦 9+" to "Unread",
                        "👥" to "Groups",
                        "↩️" to "Reply",
                        "🟣 6" to "Stories",
                        "🎂" to "Birthday",
                        "👶" to "New",
                        "1️⃣" to "1-50",
                        "2️⃣" to "51-100",
                        "3️⃣" to "101-150"
                    )
                    samplePills.forEachIndexed { idx, (emoji, hint) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (idx == 0) skin.glowPrimary.copy(alpha = 0.22f) else skin.textPrimary.copy(alpha = 0.06f),
                            border = BorderStroke(
                                1.dp,
                                if (idx == 0) skin.glowPrimary else skin.textPrimary.copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 14.sp)
                                Text(
                                    text = hint,
                                    fontSize = 10.sp,
                                    color = skin.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. RexMods Feature Logs Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rexmods_feature_logs_card"),
            shape = RoundedCornerShape(18.dp),
            color = skin.cardOverlayColor.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, skin.glowPrimary.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "RexMods Logs",
                            tint = skin.glowPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "RexMods Feature Logs",
                                color = skin.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${rexLogs.size} events logged (Auto-Delete Retention, Shortcuts, Batch Send)",
                                color = skin.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { logRefreshTrigger++ },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rexmods_logs_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh RexMods Logs",
                                tint = skin.textPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                val text = if (rexLogs.isEmpty()) {
                                    "No RexMods logs recorded yet."
                                } else {
                                    rexLogs.joinToString("\n") { "[${it.dateTime}] [${it.logLevel.shortName}] ${it.message}" }
                                }
                                clipboardManager.setText(AnnotatedString(text))
                                context.shortToast("Copied ${rexLogs.size} RexMods log entries")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rexmods_logs_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy RexMods Logs",
                                tint = skin.glowPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                context.log.clearLogs()
                                rexLogs = emptyList()
                                context.shortToast("Cleared logs")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rexmods_logs_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear RexMods Logs",
                                tint = Color(0xFFFF6B6B)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(10.dp)
                ) {
                    if (rexLogs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isLoadingLogs) "Loading RexMods logs..." else "No RexMods logs recorded yet.",
                                color = skin.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Open Snapchat to see live logs for Classic Emoji Header, Send-To Select All, and Auto-Set Chat to Delete After Viewing.",
                                color = skin.textSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        val scrollState = rememberScrollState()
                        LaunchedEffect(rexLogs.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rexLogs.forEach { line ->
                                    val levelColor = when (line.logLevel) {
                                        LogLevel.ERROR, LogLevel.ASSERT -> Color(0xFFFF6B6B)
                                        LogLevel.WARN -> Color(0xFFFFD166)
                                        LogLevel.INFO -> Color(0xFF5CE1E6)
                                        else -> skin.textSecondary
                                    }
                                    Text(
                                        text = "${line.dateTime.substringAfter(" ")} [${line.logLevel.letter}] ${line.message}",
                                        color = levelColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
