package me.eternal.purrfect.ui.manager.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.task.PendingTask
import me.eternal.purrfect.task.PendingTaskListener
import me.eternal.purrfect.task.Task
import me.eternal.purrfect.task.TaskStatus
import me.eternal.purrfect.ui.components.DismissibleCardItem
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType
import me.eternal.purrfect.ui.util.OnLifecycleEvent

class TasksRootSection : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }
    private var selectedTasks = mutableStateListOf<Long>()
    private var recentTasks by mutableStateOf<List<Pair<Long, Task>>>(emptyList())
    private var activeTasks by mutableStateOf<List<PendingTask>>(emptyList())

    private fun fetchTasks() {
        recentTasks = context.taskManager.fetchStoredTasks(limit = 50).toList()
        activeTasks = context.taskManager.getActiveTasks().values.sortedByDescending { it.taskId }
    }

    override fun canGoBack() = selectedTasks.isNotEmpty()

    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val scrollState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var showConfirmBottomSheet by remember { mutableStateOf(false) }
        var confirmBottomSheetContent by remember { mutableStateOf<@Composable () -> Unit>({}) }

        if (showConfirmBottomSheet) {
            confirmBottomSheetContent()
        }

        LaunchedEffect(Unit) {
            fetchTasks()
        }

        OnLifecycleEvent { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    fetchTasks()
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                selectedTasks.clear()
            }
        }

        val allTaskIds by remember {
            derivedStateOf {
                activeTasks.map { it.taskId } + recentTasks.map { it.first }
            }
        }

        val isAllSelected by remember {
            derivedStateOf {
                allTaskIds.isNotEmpty() && selectedTasks.size == allTaskIds.size
            }
        }

        setTopBarActions {
            if (selectedTasks.isNotEmpty()) {
                IconButton(onClick = {
                    if (isAllSelected) selectedTasks.clear()
                    else {
                        selectedTasks.clear()
                        selectedTasks.addAll(allTaskIds)
                    }
                }) {
                    Icon(
                        imageVector = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                        contentDescription = null
                    )
                }

                IconButton(onClick = {
                    val hasCompletedTasks = selectedTasks.any { taskId ->
                        val activeTask = activeTasks.find { it.taskId == taskId }
                        val recentTask = recentTasks.find { it.first == taskId }?.second
                        (activeTask?.task ?: recentTask)?.status == TaskStatus.SUCCESS
                    }

                    confirmBottomSheetContent = {
                        bottomSheets.ConfirmBottomSheet(
                            title = context.translation.format(
                                "manager.dialogs.delete_selected_tasks.title",
                                "count" to selectedTasks.size.toString()
                            ),
                            message = if (hasCompletedTasks) context.translation["manager.dialogs.delete_tasks.delete_files_option"] else null,
                            confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                            onConfirm = {
                                scope.launch(Dispatchers.IO) {
                                    selectedTasks.toList().forEach { taskId ->
                                        val taskObj = activeTasks.find { it.taskId == taskId }?.task
                                            ?: recentTasks.find { it.first == taskId }?.second
                                        if (taskObj != null) {
                                            context.taskManager.removeTask(taskObj)
                                        }
                                    }
                                    selectedTasks.clear()
                                    fetchTasks()
                                }
                                showConfirmBottomSheet = false
                            },
                            onDismiss = { showConfirmBottomSheet = false }
                        )
                    }
                    showConfirmBottomSheet = true
                }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                }
            } else if (recentTasks.isNotEmpty() || activeTasks.isNotEmpty()) {
                IconButton(onClick = {
                    confirmBottomSheetContent = {
                        bottomSheets.ConfirmBottomSheet(
                            title = context.translation["manager.dialogs.clear_all_tasks.title"],
                            confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                            onConfirm = {
                                scope.launch(Dispatchers.IO) {
                                    context.taskManager.clearAllTasks()
                                    fetchTasks()
                                }
                                showConfirmBottomSheet = false
                            },
                            onDismiss = { showConfirmBottomSheet = false }
                        )
                    }
                    showConfirmBottomSheet = true
                }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                }
            }
        }

        val combinedTasks = remember(activeTasks, recentTasks) {
            val activeIds = activeTasks.map { it.taskId }.toSet()
            activeTasks.map { it.taskId to (it.task to it) } +
                recentTasks.filter { it.first !in activeIds }.map { it.first to (it.second to null) }
        }

        if (combinedTasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenContentPadding()),
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
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.translation["manager.sections.tasks.no_tasks"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(combinedTasks, key = { _, item -> item.first }) { index, (taskId, pair) ->
                    val (task, pendingTask) = pair
                    var status by remember(task) { mutableStateOf(task.status) }
                    var progress by remember(task) { mutableIntStateOf(-1) }
                    val isSelected = selectedTasks.contains(taskId)
                    val shape = cardShape(combinedTasks.size, index)

                    DisposableEffect(pendingTask) {
                        val listener = PendingTaskListener(
                            onStateChange = { status = it },
                            onProgress = { _, prog -> progress = prog }
                        )
                        pendingTask?.addListener(listener)
                        onDispose {
                            pendingTask?.removeListener(listener)
                        }
                    }

                    DismissibleCardItem(
                        shape = shape,
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                pendingTask?.cancel()
                                context.taskManager.removeTask(task)
                                fetchTasks()
                            }
                        }
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
                            colors = if (isSelected) CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) else CardDefaults.elevatedCardColors()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedTasks.isNotEmpty()) {
                                                if (isSelected) selectedTasks.remove(taskId)
                                                else selectedTasks.add(taskId)
                                            }
                                        },
                                        onLongClick = {
                                            if (isSelected) selectedTasks.remove(taskId)
                                            else selectedTasks.add(taskId)
                                        }
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!task.author.isNullOrBlank()) {
                                            Text(
                                                text = task.author!!,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = when (status) {
                                            TaskStatus.SUCCESS -> Icons.Rounded.Check
                                            TaskStatus.FAILURE -> Icons.Rounded.Error
                                            TaskStatus.CANCELLED -> Icons.Rounded.Close
                                            else -> Icons.Rounded.Download
                                        },
                                        contentDescription = null,
                                        tint = when (status) {
                                            TaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                            TaskStatus.FAILURE -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                if (status == TaskStatus.RUNNING || status == TaskStatus.PENDING) {
                                    if (progress in 0..100) {
                                        LinearProgressIndicator(
                                            progress = { progress / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
