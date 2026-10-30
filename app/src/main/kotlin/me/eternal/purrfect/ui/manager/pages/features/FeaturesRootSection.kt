package me.eternal.purrfect.ui.manager.pages.features

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.More
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.config.ConfigContainer
import me.eternal.purrfect.common.config.ConfigFlag
import me.eternal.purrfect.common.config.DataProcessors
import me.eternal.purrfect.common.config.FeatureNotice
import me.eternal.purrfect.common.config.PropertyKey
import me.eternal.purrfect.common.config.PropertyPair
import me.eternal.purrfect.common.config.PropertyValue
import me.eternal.purrfect.common.data.MessagingRuleType
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.AlertDialogs
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.OnLifecycleEvent
import me.eternal.purrfect.ui.util.chooseFolder
import me.eternal.purrfect.ui.util.openFile
import me.eternal.purrfect.ui.util.saveFile

class FeaturesRootSection : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }
    private val alertDialogs by lazy { AlertDialogs(context.translation) }
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    private var featureSearchBarVisible by mutableStateOf(false)

    companion object {
        const val FEATURE_CONTAINER = "__container__"
        const val SEARCH_FEATURE = "__search__"
    }

    override fun canGoBack(): Boolean {
        return featureSearchBarVisible
    }

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    private fun PropertiesNoticeList(notices: List<FeatureNotice>) {
        if (notices.isEmpty()) return
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = cardShapeSingle,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                notices.forEach { notice ->
                    Text(
                        text = context.translation["features.notices.${notice.key}"],
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun FeatureAccessButton(
        modifier: Modifier = Modifier,
        text: String,
        icon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew,
        onClick: () -> Unit
    ) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun PropertyAction(
        property: PropertyPair<*>,
        onNavigate: (String) -> Unit,
        registerClickCallback: (() -> Unit) -> Unit
    ) {
        var showBottomSheet by remember { mutableStateOf(false) }
        var bottomSheetContent by remember { mutableStateOf<@Composable () -> Unit>({}) }

        fun dismiss() {
            showBottomSheet = false
        }

        if (showBottomSheet) {
            bottomSheetContent()
        }

        @Composable
        fun ConfirmButton(onClick: () -> Unit = {}) {
            Button(
                onClick = {
                    dismiss()
                    onClick()
                }
            ) {
                Text(text = context.translation["button.ok"])
            }
        }

        @Composable
        fun DismissButton(onClick: () -> Unit = {}) {
            OutlinedButton(
                onClick = {
                    dismiss()
                    onClick()
                }
            ) {
                Text(text = context.translation["button.cancel"])
            }
        }

        @Composable
        fun ResetButton(onClick: () -> Unit = {}) {
            FilledTonalButton(
                onClick = {
                    dismiss()
                    onClick()
                }
            ) {
                Text(text = context.translation["button.reset"])
            }
        }

        val value = property.value

        if (property.key.params.flags.contains(ConfigFlag.FOLDER)) {
            val saveFolder = value.getNullable()?.toString() ?: ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeatureAccessButton(
                    text = if (saveFolder.isBlank()) {
                        context.translation["features.properties.downloader.properties.save_folder.select_folder_button"]
                    } else {
                        context.translation["features.properties.downloader.properties.save_folder.change_folder_button"]
                    },
                    icon = Icons.Rounded.FolderOpen
                ) {
                    activityLauncherHelper.chooseFolder {
                        value.setAny(it)
                    }
                }
                if (saveFolder.isNotEmpty()) {
                    var showResetBottomSheet by remember { mutableStateOf(false) }

                    if (showResetBottomSheet) {
                        bottomSheets.ConfirmBottomSheet(
                            title = context.translation["manager.dialogs.reset_save_folder.title"],
                            message = context.translation["manager.dialogs.reset_save_folder.body"],
                            onConfirm = {
                                value.setAny("")
                                showResetBottomSheet = false
                            },
                            onDismiss = { showResetBottomSheet = false }
                        )
                    }

                    OutlinedIconButton(
                        onClick = { showResetBottomSheet = true }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            }
            return
        }

        when (val dataType = property.key.dataType.type) {
            DataProcessors.Type.CONTAINER -> {
                val container = value.get() as ConfigContainer
                registerClickCallback { onNavigate(property.key.propertyTranslationPath()) }

                if (container.hasGlobalState) {
                    var state by remember { mutableStateOf(container.globalState ?: false) }
                    VerticalDivider(
                        modifier = Modifier
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    )
                    Switch(
                        checked = state,
                        onCheckedChange = {
                            state = state.not()
                            container.globalState = state
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.More,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            DataProcessors.Type.BOOLEAN -> {
                var state by remember { mutableStateOf(value.get() as Boolean) }
                registerClickCallback {
                    state = state.not()
                    value.setAny(state)
                }
                Switch(
                    checked = state,
                    onCheckedChange = {
                        state = state.not()
                        value.setAny(state)
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                registerClickCallback {
                    bottomSheetContent = {
                        bottomSheets.UniqueSelectionBottomSheet(property)
                    }
                    showBottomSheet = true
                }

                Text(
                    text = (value.getNullable() as? String ?: "null").let {
                        remember(showBottomSheet, it) {
                            property.key.propertyOption(context.translation, it)
                        }
                    },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp)
                )
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION,
            DataProcessors.Type.STRING,
            DataProcessors.Type.INTEGER,
            DataProcessors.Type.FLOAT -> {
                registerClickCallback {
                    bottomSheetContent = {
                        when (dataType) {
                            DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                                bottomSheets.MultipleSelectionBottomSheet(property)
                            }
                            DataProcessors.Type.STRING,
                            DataProcessors.Type.INTEGER,
                            DataProcessors.Type.FLOAT -> {
                                bottomSheets.KeyboardInputBottomSheet(
                                    property,
                                    { DismissButton(it) },
                                    { ConfirmButton(it) },
                                    dismiss = { dismiss() }
                                )
                            }
                            else -> {}
                        }
                    }
                    showBottomSheet = true
                }

                if (dataType == DataProcessors.Type.INTEGER || dataType == DataProcessors.Type.FLOAT || dataType == DataProcessors.Type.STRING) {
                    var currentValue by remember(showBottomSheet) {
                        mutableStateOf(value.getNullable()?.toString() ?: "")
                    }

                    property.key.params.filenameFilter?.let { filenameFilter ->
                        var importDialog by remember { mutableStateOf(false) }

                        if (importDialog) {
                            LazyColumnBottomSheet(
                                onDismiss = { importDialog = false }
                            ) {
                                val files = context.fileHandleManager.getStoredFiles().filter {
                                    filenameFilter(it.name)
                                }
                                item {
                                    Text(
                                        text = context.translation["features.dialogs.select_file_title"],
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                item {
                                    if (files.isEmpty()) {
                                        Text(
                                            text = context.translation["features.notices.no_files_found"],
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                itemsIndexed(files) { index, file ->
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = cardShape(files.size, index)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clickable {
                                                    value.setAny(file.name)
                                                    currentValue = file.name
                                                    importDialog = false
                                                }
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                            Text(
                                                text = file.name,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .weight(1f),
                                                fontSize = 14.sp,
                                                lineHeight = 15.sp
                                            )
                                            if (currentValue == file.name) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FeatureAccessButton(
                                text = currentValue.ifEmpty {
                                    context.translation["features.options.select_file_button"]
                                },
                                icon = Icons.Rounded.UploadFile
                            ) {
                                importDialog = true
                            }
                            if (currentValue.isNotEmpty()) {
                                OutlinedIconButton(
                                    onClick = {
                                        value.setAny("")
                                        currentValue = ""
                                    }
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null)
                                }
                            }
                        }
                    } ?: run {
                        Text(
                            text = currentValue,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier
                                .widthIn(0.dp, 130.dp)
                                .padding(end = 10.dp)
                        )
                    }
                } else {
                    Text(
                        text = remember(showBottomSheet) {
                            (value.get() as List<*>).let {
                                if (it.isEmpty()) context.translation["features.options.empty"]
                                else "${it.size}"
                            }
                        },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier
                            .widthIn(0.dp, 120.dp)
                            .padding(end = 6.dp)
                    )
                }
            }

            DataProcessors.Type.INT_COLOR -> {
                registerClickCallback {
                    bottomSheetContent = {
                        bottomSheets.ChooseColorBottomSheet(
                            property,
                            { DismissButton(it) },
                            { ConfirmButton(it) },
                            { ResetButton(it) },
                            dismiss = { dismiss() }
                        )
                    }
                    showBottomSheet = true
                }

                CircularAlphaTile(selectedColor = (value.getNullable() as? Int)?.let { Color(it) })
            }

            DataProcessors.Type.MAP_COORDINATES -> {
                registerClickCallback {
                    bottomSheetContent = {
                        alertDialogs.ChooseLocationDialog(property) {
                            dismiss()
                        }
                    }
                    showBottomSheet = true
                }
                Text(
                    text = (value.getNullable() as? Pair<*, *>)?.let {
                        "${(it.first as? Number)?.toDouble()?.toFloat() ?: 0f}, ${(it.second as? Number)?.toDouble()?.toFloat() ?: 0f}"
                    } ?: "0, 0",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(0.dp, 120.dp)
                )
            }
        }
    }

    @Composable
    private fun CircularAlphaTile(selectedColor: Color?) {
        Canvas(
            modifier = Modifier.size(36.dp),
            onDraw = {
                val radius = size.minDimension / 2
                drawCircle(
                    color = selectedColor ?: Color.White,
                    radius = radius - 3,
                )
            }
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PropertyCard(
        property: PropertyPair<*>,
        index: Int,
        groupSize: Int,
        onNavigate: (String) -> Unit
    ) {
        var clickCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

        val versionCheck = property.key.params.versionCheck
        val isComponentDisabled = remember(property) {
            versionCheck?.let { check ->
                context.installationSummary.snapchatInfo?.let {
                    check.checkVersion(it.versionCode) != null && check.isDisabled
                }
            } == true
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape(groupSize, index),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (clickCallback != null && !isComponentDisabled) {
                            Modifier.clickable { clickCallback?.invoke() }
                        } else Modifier
                    )
                    .padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = remember(property.key) { context.translation[property.key.propertyName()] },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        )
                        val desc = remember(property.key) { context.translation[property.key.propertyDescription()] }
                        if (desc.isNotBlank() && !desc.startsWith("features.")) {
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isComponentDisabled) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                when (property.key.name) {
                                    "friend_mutation_observer" -> {
                                        FeatureAccessButton(
                                            text = context.translation["manager.routes.friend_tracker"],
                                            icon = Icons.Rounded.Assignment
                                        ) {
                                            routes.friendTracker.navigate()
                                        }
                                    }
                                    "message_logger" -> {
                                        FeatureAccessButton(
                                            text = context.translation["manager.routes.logger_history"],
                                            icon = Icons.Rounded.BookmarkAdded
                                        ) {
                                            routes.loggerHistory.navigate()
                                        }
                                    }
                                    "location" -> {
                                        FeatureAccessButton(
                                            text = context.translation["manager.routes.better_location"],
                                            icon = Icons.Rounded.Tune
                                        ) {
                                            routes.betterLocation.navigate()
                                        }
                                    }
                                }
                            }
                        }

                        PropertiesNoticeList(property.key.params.notices)
                    }

                    if (!isComponentDisabled) {
                        PropertyAction(
                            property = property,
                            onNavigate = onNavigate,
                            registerClickCallback = {
                                clickCallback = it
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 5.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RuleCard(ruleType: MessagingRuleType, index: Int, groupSize: Int) {
        val ruleState = remember { context.config.root.rules.getRuleState(ruleType) }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape(groupSize, index)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        routes.manageRuleFeature.navigate {
                            put("rule_type", ruleType.key)
                        }
                    }
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = context.translation["rules.properties.${ruleType.key}.name"],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = context.translation["rules.properties.${ruleType.key}.description"],
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = {
                        if (ruleType.listMode && ruleState != null) {
                            routes.social.navigate()
                        } else {
                            routes.manageRuleFeature.navigate {
                                put("rule_type", ruleType.key)
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (ruleState == null) {
                            context.translation["rules.toasts.disabled"]
                        } else {
                            if (!ruleType.listMode) {
                                context.translation["rules.toasts.enabled"]
                            } else {
                                context.translation["rules.modes.${ruleState.key}"]
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun PropertiesView(
        properties: List<PropertyPair<*>>,
        rules: List<MessagingRuleType> = emptyList(),
        notices: List<FeatureNotice> = emptyList(),
        onNavigate: (String) -> Unit,
        headerContent: (@Composable () -> Unit)? = null
    ) {
        val visibleProperties = remember(properties) {
            properties.filter { !it.key.params.flags.contains(ConfigFlag.HIDDEN) }
        }
        val totalItems = visibleProperties.size + rules.size

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (notices.isNotEmpty()) {
                item {
                    PropertiesNoticeList(notices)
                }
            }
            if (headerContent != null) {
                item {
                    headerContent()
                }
            }
            itemsIndexed(visibleProperties, key = { _, item -> item.key.name }) { index, item ->
                PropertyCard(item, index, totalItems, onNavigate)
            }
            itemsIndexed(rules, key = { _, item -> item.key }) { index, ruleType ->
                RuleCard(ruleType, visibleProperties.size + index, totalItems)
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val localNavController = rememberNavController()

        OnLifecycleEvent { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                context.config.writeConfig()
            }
        }

        val allProperties = remember {
            val map = linkedMapOf<PropertyKey<*>, PropertyValue<*>>()
            fun collect(container: ConfigContainer) {
                container.properties.forEach { (prop, value) ->
                    map[prop] = value
                    if (prop.dataType.type == DataProcessors.Type.CONTAINER) {
                        collect(value.get() as ConfigContainer)
                    }
                }
            }
            collect(context.config.root)
            map
        }

        var searchFilter by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()

        setTopBarActions {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AnimatedVisibility(
                    visible = featureSearchBarVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BackHandler {
                        featureSearchBarVisible = false
                        searchFilter = ""
                        if (localNavController.currentDestination?.route == SEARCH_FEATURE) {
                            localNavController.popBackStack()
                        }
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    TextField(
                        value = searchFilter,
                        onValueChange = {
                            searchFilter = it
                            searchJob?.cancel()
                            searchJob = scope.launch {
                                delay(150)
                                if (searchFilter.isEmpty()) {
                                    if (localNavController.currentDestination?.route == SEARCH_FEATURE) {
                                        localNavController.popBackStack()
                                    }
                                } else if (localNavController.currentDestination?.route != SEARCH_FEATURE) {
                                    localNavController.navigate(SEARCH_FEATURE)
                                }
                            }
                        },
                        placeholder = { Text(text = context.translation["manager.sections.features.search"]) },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .focusRequester(focusRequester)
                            .padding(end = 8.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )
                }

                IconButton(
                    onClick = {
                        featureSearchBarVisible = !featureSearchBarVisible
                        if (!featureSearchBarVisible) {
                            searchFilter = ""
                            if (localNavController.currentDestination?.route == SEARCH_FEATURE) {
                                localNavController.popBackStack()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (featureSearchBarVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = null
                    )
                }

                if (!featureSearchBarVisible) {
                    var showExportMenu by remember { mutableStateOf(false) }
                    var showResetDialog by remember { mutableStateOf(false) }

                    if (showResetDialog) {
                        bottomSheets.ConfirmBottomSheet(
                            title = context.translation["manager.dialogs.reset_config.title"],
                            message = context.translation["manager.dialogs.reset_config.body"],
                            onConfirm = {
                                context.config.reset()
                                context.activity?.recreate()
                                showResetDialog = false
                            },
                            onDismiss = { showResetDialog = false }
                        )
                    }

                    IconButton(onClick = { showExportMenu = !showExportMenu }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.More, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = context.translation["manager.sections.features.export_option"]) },
                            onClick = {
                                showExportMenu = false
                                activityLauncherHelper.saveFile("config.json", "application/json") { uri ->
                                    runCatching {
                                        context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                                            context.config.exportToString(exportSensitiveData = true).byteInputStream().copyTo(it)
                                        }
                                        context.shortToast(context.translation["manager.sections.features.config_export_success_toast"])
                                    }.onFailure {
                                        context.longToast(context.translation.format("manager.sections.features.config_export_failure_toast", "error" to (it.message ?: "")))
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = context.translation["manager.sections.features.import_option"]) },
                            onClick = {
                                showExportMenu = false
                                activityLauncherHelper.openFile("application/json") { uri ->
                                    runCatching {
                                        val content = context.androidContext.contentResolver.openInputStream(Uri.parse(uri))?.use {
                                            it.bufferedReader().readText()
                                        } ?: return@openFile
                                        context.config.loadFromString(content)
                                        context.config.writeConfig()
                                        context.shortToast(context.translation["manager.sections.features.config_import_success_toast"])
                                        context.activity?.recreate()
                                    }.onFailure {
                                        context.longToast(context.translation.format("manager.sections.features.config_import_failure_toast", "error" to (it.message ?: "")))
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = context.translation["manager.sections.features.reset_option"]) },
                            onClick = {
                                showExportMenu = false
                                showResetDialog = true
                            }
                        )
                    }
                }
            }
        }

        NavHost(
            navController = localNavController,
            startDestination = FEATURE_CONTAINER,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(FEATURE_CONTAINER) {
                val rootProperties = remember {
                    context.config.root.properties.map { PropertyPair(it.key, it.value) }
                }
                PropertiesView(
                    properties = rootProperties,
                    onNavigate = { localNavController.navigate(it) }
                )
            }

            composable(SEARCH_FEATURE) {
                val filteredProperties = remember(searchFilter) {
                    if (searchFilter.isBlank()) emptyList()
                    else allProperties.filter { (prop, _) ->
                        prop.dataType.type != DataProcessors.Type.CONTAINER &&
                            !prop.params.flags.contains(ConfigFlag.HIDDEN) &&
                            (context.translation[prop.propertyName()].contains(searchFilter, ignoreCase = true) ||
                                context.translation[prop.propertyDescription()].contains(searchFilter, ignoreCase = true))
                    }.map { PropertyPair(it.key, it.value) }
                }

                val filteredRules = remember(searchFilter) {
                    if (searchFilter.isBlank()) emptyList()
                    else MessagingRuleType.entries.filter { rule ->
                        context.translation["rules.properties.${rule.key}.name"].contains(searchFilter, ignoreCase = true) ||
                            context.translation["rules.properties.${rule.key}.description"].contains(searchFilter, ignoreCase = true)
                    }
                }

                PropertiesView(
                    properties = filteredProperties,
                    rules = filteredRules,
                    onNavigate = { localNavController.navigate(it) }
                )
            }

            allProperties.filter { it.key.dataType.type == DataProcessors.Type.CONTAINER }.forEach { (prop, value) ->
                val category = value.get() as ConfigContainer
                composable(prop.propertyTranslationPath()) {
                    val categoryProperties = remember {
                        category.properties.map { PropertyPair(it.key, it.value) }
                    }
                    val categoryRules = remember {
                        if (prop.name == "rules") MessagingRuleType.entries else emptyList()
                    }
                    val isRexMods = prop.name == "rex_mods"
                    PropertiesView(
                        properties = categoryProperties,
                        rules = categoryRules,
                        notices = prop.params.notices,
                        onNavigate = { localNavController.navigate(it) },
                        headerContent = if (isRexMods) {
                            { RexModsToolsAndLogsCard(context) }
                        } else null
                    )
                }
            }
        }
    }
}
