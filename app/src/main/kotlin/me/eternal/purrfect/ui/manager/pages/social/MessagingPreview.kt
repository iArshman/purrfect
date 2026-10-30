package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.withContext
import me.eternal.purrfect.common.bridge.wrapper.LoggerWrapper
import me.eternal.purrfect.common.data.ContentType
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.common.util.protobuf.ProtoReader
import me.eternal.purrfect.storage.getFriendInfo
import me.eternal.purrfect.ui.manager.Routes

class MessagingPreview : Routes.Section() {
    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val scope = navBackStackEntry.arguments?.getString("scope")?.let { SocialScope.getByName(it) } ?: SocialScope.FRIEND
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        val messages = remember { mutableStateListOf<Pair<String, String>>() }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(id) {
            withContext(Dispatchers.IO) {
                isLoading = true
                runCatching {
                    val logger = LoggerWrapper(context.androidContext)
                    val convId = if (scope == SocialScope.GROUP) id else {
                        context.database.getFriendInfo(id)?.dmConversationId ?: id
                    }
                    val fetched = logger.fetchMessages(convId, Long.MAX_VALUE, 50, true) { true }
                    val parsed = fetched.map { msg ->
                        val text = runCatching {
                            val obj = JsonParser.parseString(String(msg.messageData, Charsets.UTF_8)).asJsonObject
                            val contentBytes = obj["mMessageContent"]?.asJsonObject?.getAsJsonArray("mContent")?.map { it.asByte }?.toByteArray()
                            val protoReader = contentBytes?.let { ProtoReader(it) }
                            val contentType = ContentType.fromMessageContainer(protoReader)
                            val chatText = protoReader?.getString(2, 1)
                            chatText ?: "[${contentType?.name ?: "Message"}]"
                        }.getOrDefault("[Message]")
                        msg.username to text
                    }
                    withContext(Dispatchers.Main) {
                        messages.clear()
                        messages.addAll(parsed)
                    }
                }
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding())
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (messages.isEmpty()) {
                Text(
                    text = context.translation["manager.sections.messaging_preview.no_message_hint"],
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(messages) { index, (sender, text) ->
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
                                Text(
                                    text = sender,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = text,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
