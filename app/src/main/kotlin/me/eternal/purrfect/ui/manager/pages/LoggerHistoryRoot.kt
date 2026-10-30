package me.eternal.purrfect.ui.manager.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.common.bridge.wrapper.LoggedMessage
import me.eternal.purrfect.common.bridge.wrapper.LoggerWrapper
import me.eternal.purrfect.common.data.ContentType
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.common.util.protobuf.ProtoReader
import me.eternal.purrfect.storage.findFriend
import me.eternal.purrfect.storage.getFriendInfo
import me.eternal.purrfect.storage.getGroupInfo
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import java.text.DateFormat
import java.util.Date

class LoggerHistoryRoot : Routes.Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    private lateinit var loggerWrapper: LoggerWrapper
    private var selectedConversionId by mutableStateOf<String?>(null)
    private var stringFilter by mutableStateOf("")
    private var reverseOrder by mutableStateOf(true)

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
        loggerWrapper = LoggerWrapper(context.androidContext)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        var conversations by remember { mutableStateOf<List<String>>(emptyList()) }
        val messages = remember { mutableStateListOf<LoggedMessage>() }
        var hasMoreMessages by remember { mutableStateOf(true) }

        fun loadMessages(reset: Boolean = false) {
            val convId = selectedConversionId ?: return
            coroutineScope.launch(Dispatchers.IO) {
                if (reset) {
                    withContext(Dispatchers.Main) {
                        messages.clear()
                        hasMoreMessages = true
                    }
                }
                val fromTimestamp = if (reverseOrder) {
                    messages.lastOrNull()?.sendTimestamp ?: Long.MAX_VALUE
                } else {
                    messages.lastOrNull()?.sendTimestamp ?: Long.MIN_VALUE
                }
                val fetched = loggerWrapper.fetchMessages(
                    convId,
                    fromTimestamp,
                    30,
                    reverseOrder
                ) { msg ->
                    if (stringFilter.isBlank()) true
                    else msg.username.contains(stringFilter, ignoreCase = true)
                }
                withContext(Dispatchers.Main) {
                    if (fetched.isEmpty()) hasMoreMessages = false
                    else messages.addAll(fetched)
                }
            }
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                conversations = loggerWrapper.getAllConversations()
                selectedConversionId = conversations.firstOrNull()
            }
        }

        LaunchedEffect(selectedConversionId, stringFilter, reverseOrder) {
            loadMessages(reset = true)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding())
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = cardShapeSingle
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        val selectedTitle = remember(selectedConversionId) {
                            selectedConversionId?.let { convId ->
                                context.database.getGroupInfo(convId)?.name
                                    ?: context.database.findFriend(convId)?.mutableUsername
                                    ?: convId
                            } ?: context.translation["manager.sections.logger_history.empty_conversations"]
                        }

                        OutlinedTextField(
                            value = selectedTitle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(context.translation["manager.sections.logger_history.conversation_hint"]) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            conversations.forEach { convId ->
                                val title = remember(convId) {
                                    context.database.getGroupInfo(convId)?.name
                                        ?: context.database.findFriend(convId)?.let { f -> "${f.displayName ?: f.mutableUsername} (${f.mutableUsername})" }
                                        ?: convId
                                }
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        selectedConversionId = convId
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stringFilter,
                            onValueChange = { stringFilter = it },
                            placeholder = { Text(context.translation["manager.sections.logger_history.search_messages_placeholder"]) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (stringFilter.isNotEmpty()) {
                                    IconButton(onClick = { stringFilter = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = reverseOrder,
                            onClick = { reverseOrder = !reverseOrder },
                            label = { Text(context.translation["manager.sections.logger_history.reverse_order_checkbox"]) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.messageId }) { index, message ->
                    val decodedText = remember(message) {
                        runCatching {
                            val obj = JsonParser.parseString(String(message.messageData, Charsets.UTF_8)).asJsonObject
                            val contentBytes = obj["mMessageContent"]?.asJsonObject?.getAsJsonArray("mContent")?.map { it.asByte }?.toByteArray()
                            val protoReader = contentBytes?.let { ProtoReader(it) }
                            val contentType = ContentType.fromMessageContainer(protoReader)
                            val chatText = protoReader?.getString(2, 1)
                            chatText ?: "[${contentType?.name ?: "Message"}]"
                        }.getOrDefault("[Logged Message]")
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(messages.size, index)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message.username,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.sendTimestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = decodedText,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (index == messages.lastIndex && hasMoreMessages) {
                        LaunchedEffect(messages.size) {
                            loadMessages(reset = false)
                        }
                    }
                }
            }
        }
    }
}
