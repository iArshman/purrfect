package me.eternal.purrfect.ui.manager.pages

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.components.DismissibleCardItem
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.openFile
import java.io.File

class FileImportsRoot : Routes.Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        val files = remember { mutableStateListOf<File>() }

        fun reloadFiles() {
            coroutineScope.launch(Dispatchers.IO) {
                val stored = context.fileHandleManager.getStoredFiles()
                withContext(Dispatchers.Main) {
                    files.clear()
                    files.addAll(stored)
                }
            }
        }

        LaunchedEffect(Unit) {
            reloadFiles()
        }

        setFloatingActionButton {
            ExtendedFloatingActionButton(
                onClick = {
                    activityLauncherHelper.openFile("*/*") { uriString ->
                        val uri = Uri.parse(uriString)
                        val docFile = DocumentFile.fromSingleUri(context.androidContext, uri) ?: return@openFile
                        val fileName = docFile.name ?: "imported_${System.currentTimeMillis()}"
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                context.androidContext.contentResolver.openInputStream(uri)?.use { input ->
                                    context.fileHandleManager.importFile(fileName) {
                                        input.copyTo(this)
                                    }
                                }
                                reloadFiles()
                            }.onFailure {
                                context.longToast(context.translation.format("manager.sections.file_imports.file_import_failed", "error" to (it.message ?: "")))
                            }
                        }
                    }
                },
                icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                text = { Text(text = context.translation["manager.sections.file_imports.import_file_button"]) }
            )
        }

        if (files.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenContentPadding(hasFAB = true)),
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
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.translation["manager.sections.file_imports.no_files_hint"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(hasFAB = true),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(files, key = { _, file -> file.name }) { index, file ->
                    val shape = cardShape(files.size, index)
                    DismissibleCardItem(
                        shape = shape,
                        onDelete = {
                            coroutineScope.launch(Dispatchers.IO) {
                                context.fileHandleManager.deleteFile(file.name)
                                reloadFiles()
                            }
                        }
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(text = file.name, fontWeight = FontWeight.SemiBold)
                                },
                                supportingContent = {
                                    Text(
                                        text = android.text.format.Formatter.formatFileSize(context.androidContext, file.length()),
                                        fontSize = 12.sp
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
