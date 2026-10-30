package me.eternal.purrfect.ui.manager.pages.tracker

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.bridge.wrapper.TrackerLog
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.util.snap.BitmojiSelfie
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.components.DismissibleCardItem
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType
import me.eternal.purrfect.ui.util.coil.BitmojiImage
import me.eternal.purrfect.ui.util.saveFile
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    context: RemoteSideContext,
    activityLauncherHelper: ActivityLauncherHelper,
    deleteAction: (() -> Unit) -> Unit,
    exportAction: (() -> Unit) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val bottomSheets = remember { BottomSheets(context.translation) }

    val logs = remember { mutableStateListOf<TrackerLog>() }
    var isLoading by remember { mutableStateOf(false) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var showDeleteSheet by remember { mutableStateOf(false) }

    fun loadNewLogs(reset: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            if (reset) {
                pageIndex = 0
                withContext(Dispatchers.Main) { logs.clear() }
            }
            val newLogs = context.messageLogger.getLogs(
                pageIndex = pageIndex,
                pageSize = 30,
                filter = { log ->
                    if (filter.isBlank()) true
                    else log.username.contains(filter, ignoreCase = true) ||
                        log.conversationTitle?.contains(filter, ignoreCase = true) == true
                }
            )
            withContext(Dispatchers.Main) {
                logs.addAll(newLogs)
                pageIndex += 1
                isLoading = false
            }
        }
    }

    LaunchedEffect(filter) {
        loadNewLogs(reset = true)
    }

    SideEffect {
        deleteAction { showDeleteSheet = true }
        exportAction {
            activityLauncherHelper.saveFile("tracker_logs_${System.currentTimeMillis()}.json", "application/json") { uri ->
                coroutineScope.launch(Dispatchers.IO) {
                    runCatching {
                        context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use { out ->
                            JsonWriter(out.writer()).use { writer ->
                                writer.setIndent("  ")
                                writer.beginArray()
                                logs.forEach { log ->
                                    writer.jsonValue(log.toJson().toString())
                                }
                                writer.endArray()
                            }
                        }
                        context.shortToast("Exported logs!")
                    }
                }
            }
        }
    }

    if (showDeleteSheet) {
        bottomSheets.ConfirmBottomSheet(
            title = context.translation["manager.sections.friend_tracker.clear_all_logs_button"],
            confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    context.messageLogger.purgeTrackerLogs(0L)
                    loadNewLogs(reset = true)
                }
                showDeleteSheet = false
            },
            onDismiss = { showDeleteSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(context.translation["manager.sections.friend_tracker.search_placeholder"]) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { filter = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )

        if (logs.isEmpty() && !isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(180.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.translation["manager.sections.friend_tracker.no_events"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(logs, key = { _, log -> log.id }) { index, log ->
                    val friendInfo = remember(log.userId) { context.database.getFriendInfo(log.userId) }
                    val shape = cardShape(logs.size, index)

                    DismissibleCardItem(
                        shape = shape,
                        onDelete = {
                            coroutineScope.launch(Dispatchers.IO) {
                                context.messageLogger.deleteTrackerLog(log.id)
                                withContext(Dispatchers.Main) {
                                    logs.remove(log)
                                }
                            }
                        }
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape
                        ) {
                            ListItem(
                                leadingContent = {
                                    BitmojiImage(
                                        context = context,
                                        url = friendInfo?.let {
                                            BitmojiSelfie.getBitmojiSelfie(
                                                it.selfieId,
                                                it.bitmojiId,
                                                BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                                            )
                                        },
                                        size = 40
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = friendInfo?.displayName ?: log.username,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = "${context.translation["tracker_events.${log.eventType}"]} in ${log.conversationTitle ?: log.username}",
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.timestamp)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    if (index == logs.lastIndex && !isLoading) {
                        LaunchedEffect(logs.size) {
                            loadNewLogs(reset = false)
                        }
                    }
                }
            }
        }
    }
}
