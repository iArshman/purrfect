package me.eternal.purrfect.ui.manager.pages.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.data.*
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.components.DefaultBottomSheet
import me.eternal.purrfect.ui.components.DismissibleCardItem
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.pages.social.AddFriendBottomSheet
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType

class EditRule : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    fun AddEventBottomSheet(
        onDismissRequest: () -> Unit,
        onEventAdd: (TrackerRuleEvent) -> Unit
    ) {
        var currentEventType by remember { mutableStateOf(TrackerEventType.CONVERSATION_ENTER.key) }
        var triggersOnlyOnCounter by remember { mutableStateOf(false) }
        val actions = remember { mutableStateSetOf(*TrackerRuleAction.entries.toTypedArray()) }
        var expandedEventMenu by remember { mutableStateOf(false) }

        DefaultBottomSheet(onDismiss = onDismissRequest) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.translation["manager.dialogs.add_event.title"],
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            onEventAdd(
                                TrackerRuleEvent(
                                    -1,
                                    true,
                                    currentEventType,
                                    TrackerRuleActionParams(onlyInsideConversation = triggersOnlyOnCounter),
                                    actions.toList()
                                )
                            )
                        }
                    ) {
                        Text(text = context.translation["manager.dialogs.add_event.add_button"])
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedEventMenu,
                    onExpandedChange = { expandedEventMenu = !expandedEventMenu }
                ) {
                    OutlinedTextField(
                        value = context.translation["tracker_events.$currentEventType"],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.translation["manager.dialogs.add_event.type"]) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEventMenu) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEventMenu,
                        onDismissRequest = { expandedEventMenu = false }
                    ) {
                        TrackerEventType.entries.forEach { eventType ->
                            DropdownMenuItem(
                                text = { Text(context.translation["tracker_events.${eventType.key}"]) },
                                onClick = {
                                    currentEventType = eventType.key
                                    expandedEventMenu = false
                                }
                            )
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrackerRuleAction.entries.forEach { action ->
                        FilterChip(
                            selected = actions.contains(action),
                            onClick = {
                                if (actions.contains(action)) actions.remove(action)
                                else actions.add(action)
                            },
                            label = { Text(context.translation["tracker_actions.${action.key}"]) }
                        )
                    }
                }
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val currentRuleId = navBackStackEntry.arguments?.getString("rule_id")?.toIntOrNull()

        val events = remember {
            mutableStateListOf<TrackerRuleEvent>().apply {
                if (currentRuleId != null) {
                    addAll(context.database.getTrackerEvents(currentRuleId))
                }
            }
        }
        var currentRule by remember {
            mutableStateOf(currentRuleId?.let { context.database.getTrackerRule(it) })
        }
        val ruleName = remember { mutableStateOf(currentRule?.name ?: "") }
        val ruleAuthor = remember { mutableStateOf(currentRule?.author ?: "") }
        var deleteConfirmation by remember { mutableStateOf(false) }
        var addEventSheet by remember { mutableStateOf(false) }

        fun saveRule() {
            val ruleId = currentRuleId ?: context.database.newTrackerRule()
            events.forEach { event ->
                context.database.addOrUpdateTrackerRuleEvent(
                    event.id.takeIf { it > -1 },
                    ruleId,
                    event.eventType,
                    event.params,
                    event.actions
                )
            }
            context.database.setTrackerRuleName(ruleId, ruleName.value.trim())
            context.database.setTrackerRuleAuthor(ruleId, ruleAuthor.value.trim())
        }

        if (deleteConfirmation) {
            bottomSheets.ConfirmBottomSheet(
                title = context.translation["manager.dialogs.delete_rule.title"],
                message = context.translation["manager.dialogs.delete_rule.content"],
                confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                onConfirm = {
                    if (currentRuleId != null) {
                        context.database.deleteTrackerRule(currentRuleId)
                    }
                    deleteConfirmation = false
                    routes.navController.popBackStack()
                },
                onDismiss = { deleteConfirmation = false }
            )
        }

        if (addEventSheet) {
            AddEventBottomSheet(
                onDismissRequest = { addEventSheet = false },
                onEventAdd = { event ->
                    events.add(event)
                    addEventSheet = false
                }
            )
        }

        setTopBarActions {
            if (currentRuleId != null) {
                IconButton(onClick = { deleteConfirmation = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                }
            }
            IconButton(
                onClick = {
                    saveRule()
                    routes.navController.popBackStack()
                }
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
            }
        }

        setFloatingActionButton {
            ExtendedFloatingActionButton(
                onClick = { addEventSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(context.translation["manager.sections.edit_rule.add_event_button"]) }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(hasFAB = true),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeSingle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ruleName.value,
                            onValueChange = { ruleName.value = it },
                            label = { Text(context.translation["manager.sections.edit_rule.rule_name_placeholder"]) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleAuthor.value,
                            onValueChange = { ruleAuthor.value = it },
                            label = { Text("Author") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Text(
                    text = context.translation["manager.sections.edit_rule.events_title"],
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            itemsIndexed(events) { index, event ->
                val shape = cardShape(events.size, index)
                DismissibleCardItem(
                    shape = shape,
                    onDelete = {
                        if (event.id > -1) {
                            context.database.deleteTrackerRuleEvent(event.id)
                        }
                        events.remove(event)
                    }
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = context.translation["tracker_events.${event.eventType}"],
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = event.actions.joinToString(", ") { context.translation["tracker_actions.${it.key}"] },
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
