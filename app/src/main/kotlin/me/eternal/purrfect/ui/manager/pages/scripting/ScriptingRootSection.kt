package me.eternal.purrfect.ui.manager.pages.scripting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.*
import me.eternal.purrfect.common.scripting.type.ModuleInfo
import me.eternal.purrfect.common.scripting.ui.EnumScriptInterface
import me.eternal.purrfect.common.scripting.ui.InterfaceManager
import me.eternal.purrfect.common.scripting.ui.ScriptInterface
import me.eternal.purrfect.common.ui.*
import me.eternal.purrfect.storage.isScriptEnabled
import me.eternal.purrfect.storage.setScriptEnabled
import me.eternal.purrfect.ui.components.DefaultBottomSheet
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType
import me.eternal.purrfect.ui.util.chooseFolder
import me.eternal.purrfect.ui.util.pullrefresh.PullRefreshIndicator
import me.eternal.purrfect.ui.util.pullrefresh.pullRefresh
import me.eternal.purrfect.ui.util.pullrefresh.rememberPullRefreshState

class ScriptingRootSection : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }
    private val reloadDispatcher = AsyncUpdateDispatcher(updateOnFirstComposition = false)
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    private fun ImportRemoteScript(dismiss: () -> Unit) {
        val coroutineScope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        var isLoading by remember { mutableStateOf(false) }

        DefaultBottomSheet(onDismiss = dismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = context.translation["manager.dialogs.import_script.title"],
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(context.translation["manager.dialogs.import_script.url_field_hint"]) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { focusRequester.requestFocus() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        enabled = !isLoading && url.isNotBlank(),
                        onClick = {
                            isLoading = true
                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    context.scriptManager.importFromUrl(url)
                                    withContext(Dispatchers.Main) {
                                        dismiss()
                                        reloadDispatcher.dispatch()
                                    }
                                }.onFailure {
                                    context.longToast("Failed to import script: ${it.message}")
                                    isLoading = false
                                }
                            }
                        }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = context.translation["manager.dialogs.import_script.import_button"])
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModuleItem(script: ModuleInfo, index: Int, groupSize: Int) {
        val coroutineScope = rememberCoroutineScope()
        var enabled by rememberAsyncMutableState(defaultValue = false, keys = arrayOf(script)) {
            context.database.isScriptEnabled(script.name)
        }
        var openSettings by remember(script) { mutableStateOf(false) }
        var openDeleteConfirm by remember { mutableStateOf(false) }

        val dispatcher = rememberAsyncUpdateDispatcher()

        val buildObject by rememberAsyncMutableState(defaultValue = null, updateDispatcher = dispatcher, keys = arrayOf(script, enabled)) {
            if (!enabled) return@rememberAsyncMutableState null
            context.scriptManager.runtime.getModuleByName(script.name)
                ?.getBinding(InterfaceManager::class)
                ?.buildInterface(EnumScriptInterface.SETTINGS)
        }

        if (openDeleteConfirm) {
            bottomSheets.ConfirmBottomSheet(
                title = context.translation.format(
                    "manager.dialogs.delete_script.title",
                    "name" to (script.displayName ?: script.name)
                ),
                message = context.translation["manager.dialogs.delete_script.body"],
                confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            val modulePath = context.scriptManager.getModulePath(script.name)!!
                            context.scriptManager.unloadScript(modulePath)
                            context.scriptManager.getScriptsFolder()?.findFile(modulePath)?.delete()
                            reloadDispatcher.dispatch()
                            context.shortToast(context.translation["manager.sections.scripting.actions.script_deleted_toast"])
                        }
                    }
                    openDeleteConfirm = false
                },
                onDismiss = { openDeleteConfirm = false }
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape(groupSize, index)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable {
                        if (enabled && buildObject != null) {
                            openSettings = !openSettings
                        }
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Code, contentDescription = null)
                    },
                    headlineContent = {
                        Text(text = script.displayName ?: script.name, fontWeight = FontWeight.Bold)
                    },
                    supportingContent = {
                        Column {
                            Text(text = "v${script.version} by ${script.author ?: "Unknown"}", fontSize = 12.sp)
                            script.description?.let { Text(text = it, fontSize = 12.sp) }
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (buildObject != null) {
                                IconButton(onClick = { openSettings = !openSettings }) {
                                    Icon(
                                        imageVector = if (openSettings) Icons.Rounded.ExpandLess else Icons.Rounded.Tune,
                                        contentDescription = null
                                    )
                                }
                            }
                            IconButton(onClick = { openDeleteConfirm = true }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { isChecked ->
                                    openSettings = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            val modulePath = context.scriptManager.getModulePath(script.name)!!
                                            context.scriptManager.unloadScript(modulePath)
                                            if (isChecked) {
                                                context.scriptManager.loadScript(modulePath)
                                                context.scriptManager.runtime.getModuleByName(script.name)
                                                    ?.callFunction("module.onPurrfectLoad")
                                                context.shortToast(context.translation["manager.sections.scripting.actions.script_loaded_toast"])
                                            } else {
                                                context.shortToast(context.translation["manager.sections.scripting.actions.script_unloaded_toast"])
                                            }
                                            context.database.setScriptEnabled(script.name, isChecked)
                                            withContext(Dispatchers.Main) {
                                                dispatcher.dispatch()
                                                enabled = isChecked
                                            }
                                        }.onFailure { throwable ->
                                            withContext(Dispatchers.Main) {
                                                enabled = !isChecked
                                            }
                                            context.longToast("Script error: ${throwable.message}")
                                        }
                                    }
                                }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (openSettings && buildObject != null) {
                    ScriptInterface(interfaceBuilder = buildObject!!)
                }
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val scriptingFolder by rememberAsyncMutableState(
            defaultValue = null,
            updateDispatcher = reloadDispatcher
        ) {
            context.scriptManager.sync()
            context.scriptManager.getScriptsFolder()
        }
        val scriptModules by rememberAsyncMutableState(
            defaultValue = emptyList(),
            updateDispatcher = reloadDispatcher
        ) {
            context.scriptManager.getSyncedModules()
        }

        val coroutineScope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        var importFromUrlDialog by remember { mutableStateOf(false) }
        var fabMenuExpanded by remember { mutableStateOf(false) }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = refreshing,
            onRefresh = {
                coroutineScope.launch {
                    refreshing = true
                    reloadDispatcher.dispatch()
                    delay(300)
                    refreshing = false
                }
            }
        )

        if (importFromUrlDialog) {
            ImportRemoteScript { importFromUrlDialog = false }
        }

        LaunchedEffect(Unit) {
            reloadDispatcher.dispatch()
        }

        setFloatingActionButton {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fabMenuExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabMenuExpanded = false
                            activityLauncherHelper.chooseFolder { uri ->
                                context.config.root.scripting.moduleFolder.set(uri)
                                context.config.writeConfig()
                                coroutineScope.launch {
                                    reloadDispatcher.dispatch()
                                }
                            }
                        },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        text = { Text(context.translation["manager.sections.scripting.select_folder_button"]) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    if (scriptingFolder != null) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabMenuExpanded = false
                                importFromUrlDialog = true
                            },
                            icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            text = { Text(context.translation["manager.sections.scripting.import_from_url_button"]) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabMenuExpanded = false
                            routes.manageRepos.navigate()
                        },
                        icon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                        text = { Text("Manage Repositories") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }

                FloatingActionButton(
                    onClick = { fabMenuExpanded = !fabMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (fabMenuExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = null
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
                .padding(screenContentPadding(hasFAB = true))
        ) {
            if (scriptingFolder == null) {
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
                                imageVector = Icons.Rounded.FolderOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(90.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = context.translation["manager.sections.scripting.no_folder_selected_title"],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (scriptModules.isEmpty()) {
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
                                imageVector = Icons.Rounded.DataObject,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(90.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = context.translation["manager.sections.scripting.no_scripts_hint"],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(scriptModules, key = { _, mod -> mod.name }) { index, module ->
                        ModuleItem(module, index, scriptModules.size)
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
