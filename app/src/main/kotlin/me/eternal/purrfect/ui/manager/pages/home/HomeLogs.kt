package me.eternal.purrfect.ui.manager.pages.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.LogReader
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.saveFile

class HomeLogs : Routes.Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        var logReader by remember { mutableStateOf<LogReader?>(null) }
        var lineCount by remember { mutableIntStateOf(0) }
        val lazyListState = rememberLazyListState()

        fun refreshLogs() {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    logReader = context.log.newReader {
                        lineCount++
                    }
                    lineCount = logReader?.lineCount ?: 0
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshLogs()
        }

        setTopBarActions {
            IconButton(
                onClick = {
                    context.log.clearLogs()
                    refreshLogs()
                }
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Logs")
            }

            IconButton(
                onClick = {
                    activityLauncherHelper.saveFile("purrfect_logs_${System.currentTimeMillis()}.zip", "application/zip") { uri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use { output ->
                                    context.log.exportLogsToZip(output)
                                }
                                withContext(Dispatchers.Main) {
                                    context.shortToast(context.translation["manager.sections.logs.saved_logs_success_toast"])
                                }
                            }.onFailure {
                                withContext(Dispatchers.Main) {
                                    context.shortToast(context.translation["manager.sections.logs.saved_logs_failure_toast"])
                                }
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.Save, contentDescription = "Export Logs")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding())
        ) {
            val reader = logReader
            if (reader == null || lineCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = context.translation["manager.sections.logs.empty_logs"],
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .horizontalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(lineCount) { index ->
                        val line = remember(index, lineCount) { reader.getLogLine(index)?.toString() ?: "" }
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
