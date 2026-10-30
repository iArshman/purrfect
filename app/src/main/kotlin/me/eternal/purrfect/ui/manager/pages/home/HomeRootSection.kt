package me.eternal.purrfect.ui.manager.pages.home

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.R
import me.eternal.purrfect.action.EnumQuickActions
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.action.EnumAction
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.storage.getQuickTiles
import me.eternal.purrfect.storage.setQuickTiles
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import java.text.DateFormat

class HomeRootSection : Routes.Section() {
    companion object {
        val cardMargin = 10.dp
    }

    lateinit var activityLauncherHelper: ActivityLauncherHelper

    private fun openExternalLink(link: String) {
        runCatching {
            context.androidContext.startActivity(
                Intent(Intent.ACTION_VIEW, link.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private val cards: MutableMap<Pair<String, ImageVector>, () -> Unit> by lazy {
        mutableMapOf<Pair<String, ImageVector>, () -> Unit>().apply {
            EnumQuickActions.entries.forEach { quickAction ->
                this[context.translation["actions.${quickAction.key}.name"] to quickAction.icon] = {
                    quickAction.action(routes)
                }
            }
            EnumAction.entries.forEach { action ->
                this[context.translation["actions.${action.key}.name"] to action.icon] = {
                    context.launchActionIntent(action)
                }
            }
            this[context.translation["manager.routes.logger_history"] to Icons.Rounded.History] = {
                routes.loggerHistory.navigate()
            }
            this[context.translation["manager.routes.friend_tracker"] to Icons.Rounded.PersonSearch] = {
                routes.friendTracker.navigate()
            }
            this[context.translation["manager.routes.better_location"] to Icons.Rounded.MyLocation] = {
                routes.betterLocation.navigate()
            }
            this[context.translation["manager.routes.file_imports"] to Icons.Rounded.UploadFile] = {
                routes.fileImports.navigate()
            }
            this[context.translation["manager.routes.manage_repos"] to Icons.Rounded.Source] = {
                routes.manageRepos.navigate()
            }
        }
    }

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    fun ExternalLinkIcon(
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 32.dp,
        imageVector: ImageVector,
        link: String
    ) {
        IconButton(
            onClick = {
                openExternalLink(link)
            },
            modifier = modifier.size(size)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.75f)
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val avenirNext = remember {
            runCatching { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }.getOrDefault(FontFamily.Default)
        }
        val coroutineScope = rememberCoroutineScope()
        val scrollState = remember { ScrollState(0) }
        val prefs = remember { context.sharedPreferences }

        var selectedTiles by remember { mutableStateOf(listOf<String>()) }

        LaunchedEffect(Unit) {
            selectedTiles = context.database.getQuickTiles()
        }

        val expandVal = remember { mutableStateOf(prefs.getBoolean("debug_Build_summary_expanded", false)) }
        var showQuickActionsMenu by remember { mutableStateOf(false) }

        setTopBarActions {
            IconButton(onClick = { routes.homeLogs.navigate() }) {
                Icon(Icons.Rounded.BugReport, contentDescription = null)
            }
            IconButton(onClick = { routes.settings.navigate() }) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
            }
        }

        if (showQuickActionsMenu) {
            LazyColumnBottomSheet(
                onDismiss = { showQuickActionsMenu = false }
            ) {
                item {
                    Text(
                        text = context.translation["manager.sections.home.quick_actions_title"],
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                cards.entries.forEachIndexed { index, (card, _) ->
                    fun toggle(state: Boolean? = null) {
                        selectedTiles = if (state == true || (state == null && !selectedTiles.contains(card.first))) {
                            listOf(card.first) + selectedTiles
                        } else {
                            selectedTiles.minus(card.first)
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            context.database.setQuickTiles(selectedTiles)
                        }
                    }

                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = me.eternal.purrfect.common.ui.cardShape(cards.size, index)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = card.second,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Text(
                                    text = card.first,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = selectedTiles.contains(card.first),
                                    onCheckedChange = { toggle(it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(screenContentPadding(horizontal = 0.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.translation["app_name"],
                    fontSize = 36.sp,
                    fontFamily = avenirNext,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Text(
                    text = remember {
                        context.translation.format(
                            "manager.sections.home.version_title",
                            "versionName" to BuildConfig.VERSION_NAME,
                            "versionCode" to BuildConfig.VERSION_CODE.toString()
                        )
                    },
                    fontSize = 13.sp,
                    fontFamily = avenirNext,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            openExternalLink("https://github.com/eternal-purrfect/PurrfectSnap")
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub", fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            openExternalLink("https://github.com/eternal-purrfect/PurrfectSnap/wiki")
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wiki", fontSize = 13.sp)
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(all = cardMargin)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                expandVal.value = !expandVal.value
                                prefs.edit { putBoolean("debug_Build_summary_expanded", expandVal.value) }
                            }
                        ),
                    shape = cardShapeSingle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.translation["manager.sections.home.debug_build_summary_title"],
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = if (expandVal.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (expandVal.value) {
                            val summary = buildString {
                                append("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                                append("Commit: ${BuildConfig.GIT_HASH}\n")
                                append(
                                    "Date: ${
                                        DateFormat.getDateTimeInstance().format(BuildConfig.BUILD_TIMESTAMP)
                                    }"
                                )
                            }
                            Text(
                                text = summary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = context.translation["manager.sections.home.quick_actions_title"],
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showQuickActionsMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .padding(all = cardMargin)
                    .fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                remember(selectedTiles, context.translation.loadedLocale) {
                    selectedTiles.mapNotNull { tileName ->
                        cards.entries.find { tileName == it.key.first }
                    }
                }.forEach { (card, action) ->
                    ElevatedCard(
                        modifier = Modifier
                            .height(115.dp)
                            .weight(1f),
                        shape = cardShapeSingle,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { action() }
                                .padding(all = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = card.second,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = card.first,
                                lineHeight = 15.sp,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
