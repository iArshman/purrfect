package me.eternal.purrfect.ui.manager.pages.tracker

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.common.bridge.wrapper.TrackerLog
import me.eternal.purrfect.common.data.TrackerRule
import me.eternal.purrfect.common.ui.AsyncUpdateDispatcher
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfect.common.ui.rememberAsyncUpdateDispatcher
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.openFile
import me.eternal.purrfect.ui.util.saveFile

enum class ConfigImportType {
    MERGE,
    OVERWRITE
}

class FriendTrackerManagerRoot : Routes.Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    private val reloadRulesDispatcher = AsyncUpdateDispatcher()

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    private fun RulesTab() {
        val updateRules = rememberAsyncUpdateDispatcher()
        val rules = rememberAsyncMutableStateList(defaultValue = listOf(), updateDispatcher = updateRules) {
            context.database.getTrackerRulesDesc()
        }

        LaunchedEffect(reloadRulesDispatcher) {
            reloadRulesDispatcher.addCallback {
                updateRules.dispatch()
            }
        }

        if (rules.isEmpty()) {
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
                            imageVector = Icons.Rounded.RuleFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.translation["manager.sections.friend_tracker.no_rules"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    var ruleEnabled by remember(rule.id) { mutableStateOf(rule.enabled) }
                    val eventCount by remember(rule.id) {
                        mutableIntStateOf(context.database.getTrackerEvents(rule.id).size)
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(rules.size, index)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                routes.editRule.navigate {
                                    put("rule_id", rule.id.toString())
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = rule.name,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = buildString {
                                        if (!rule.author.isNullOrBlank()) append("by ${rule.author} • ")
                                        append("$eventCount events")
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = ruleEnabled,
                                    onCheckedChange = {
                                        ruleEnabled = it
                                        context.database.setTrackerRuleState(rule.id, it)
                                    }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val titles = remember {
            listOf("logs", "rules").map { context.translation["manager.sections.friend_tracker.$it"] }
        }
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        var currentTabIndex by remember { mutableIntStateOf(0) }
        var logDeleteAction by remember { mutableStateOf<() -> Unit>({}) }
        var logExportAction by remember { mutableStateOf<() -> Unit>({}) }

        LaunchedEffect(pagerState.currentPage) {
            currentTabIndex = pagerState.currentPage
        }

        setTopBarActions {
            if (currentTabIndex == 0) {
                IconButton(onClick = { logDeleteAction() }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                }
                IconButton(onClick = { logExportAction() }) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                }
            } else {
                IconButton(onClick = {
                    routes.editRule.navigate()
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding(horizontal = 0.dp, vertical = 0.dp))
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                titles.forEachIndexed { i, text ->
                    SegmentedButton(
                        selected = currentTabIndex == i,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = titles.size)
                    ) {
                        Text(text = text)
                    }
                }
            }

            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> LogsTab(
                        context = context,
                        activityLauncherHelper = activityLauncherHelper,
                        deleteAction = { logDeleteAction = it },
                        exportAction = { logExportAction = it }
                    )
                    1 -> RulesTab()
                }
            }
        }
    }
}
