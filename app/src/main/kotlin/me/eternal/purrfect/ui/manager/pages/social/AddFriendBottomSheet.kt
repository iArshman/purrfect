package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.data.MessagingFriendInfo
import me.eternal.purrfect.common.data.MessagingGroupInfo
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.util.snap.BitmojiSelfie
import me.eternal.purrfect.storage.getFriends
import me.eternal.purrfect.storage.getGroups
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet
import me.eternal.purrfect.ui.util.coil.BitmojiImage

class AddFriendBottomSheet(
    private val context: RemoteSideContext,
    private val actionHandler: Actions,
    private val pinnedIds: List<String>? = null,
) {
    class Actions(
        val onFriendState: (friend: MessagingFriendInfo, state: Boolean) -> Unit,
        val onGroupState: (group: MessagingGroupInfo, state: Boolean) -> Unit,
        val getFriendState: (friend: MessagingFriendInfo) -> Boolean,
        val getGroupState: (group: MessagingGroupInfo) -> Boolean,
    )

    @Composable
    private fun DialogHeader(searchKeyword: MutableState<String>) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.translation["manager.dialogs.add_friend.title"],
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            OutlinedTextField(
                value = searchKeyword.value,
                onValueChange = { searchKeyword.value = it },
                placeholder = {
                    Text(text = context.translation["manager.dialogs.add_friend.search_hint"])
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true
            )
        }
    }

    @Composable
    fun Content(dismiss: () -> Unit = {}) {
        var cachedFriends by remember { mutableStateOf(null as List<MessagingFriendInfo>?) }
        var cachedGroups by remember { mutableStateOf(null as List<MessagingGroupInfo>?) }

        val coroutineScope = rememberCoroutineScope()

        var timeoutJob: Job? = null
        var hasFetchError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            fun applyData(friends: List<MessagingFriendInfo>, groups: List<MessagingGroupInfo>) {
                cachedFriends = friends.run {
                    if (pinnedIds != null) {
                        sortedBy { -pinnedIds.indexOf(it.userId) }
                    } else this
                }
                cachedGroups = groups.run {
                    if (pinnedIds != null) {
                        sortedBy { -pinnedIds.indexOf(it.conversationId) }
                    } else this
                }
                timeoutJob?.cancel()
                hasFetchError = false
            }

            coroutineScope.launch(Dispatchers.IO) {
                val dbFriends = context.database.getFriends(descOrder = true)
                val dbGroups = context.database.getGroups()
                if (dbFriends.isNotEmpty() || dbGroups.isNotEmpty()) {
                    applyData(dbFriends, dbGroups)
                }
            }

            context.bridgeService?.requestEphemeralSocialSnapshot { friends, groups ->
                applyData(friends, groups)
            }
            context.requestSocialSnapshotRefresh(openSnapchatFirst = false)

            timeoutJob = coroutineScope.launch {
                delay(15000)
                if (cachedFriends == null && cachedGroups == null) {
                    hasFetchError = true
                }
            }

            context.database.messagingDataFlow.collect { (friends, groups) ->
                applyData(friends, groups)
            }
        }

        val searchKeyword = remember { mutableStateOf("") }

        val filteredGroups = remember(searchKeyword.value, cachedGroups) {
            cachedGroups?.filter {
                it.name.contains(searchKeyword.value, ignoreCase = true)
            } ?: emptyList()
        }

        val filteredFriends = remember(searchKeyword.value, cachedFriends) {
            cachedFriends?.filter {
                it.mutableUsername.contains(searchKeyword.value, ignoreCase = true) ||
                    it.displayName?.contains(searchKeyword.value, ignoreCase = true) == true
            } ?: emptyList()
        }

        LazyColumnBottomSheet(
            onDismiss = { dismiss() }
        ) {
            item {
                DialogHeader(searchKeyword)
            }

            item {
                if (hasFetchError) {
                    Text(
                        text = context.translation["manager.dialogs.add_friend.fetch_error"],
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else if (cachedFriends == null || cachedGroups == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            if (filteredGroups.isNotEmpty()) {
                item {
                    Text(
                        text = context.translation["manager.dialogs.add_friend.category_groups"],
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                itemsIndexed(filteredGroups, key = { _, g -> g.conversationId }) { index, group ->
                    var currentState by remember(group.conversationId) {
                        mutableStateOf(actionHandler.getGroupState(group))
                    }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.5.dp),
                        shape = cardShape(filteredGroups.size, index)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                currentState = !currentState
                                coroutineScope.launch(Dispatchers.IO) {
                                    actionHandler.onGroupState(group, currentState)
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = group.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = currentState,
                                    onCheckedChange = {
                                        currentState = it
                                        coroutineScope.launch(Dispatchers.IO) {
                                            actionHandler.onGroupState(group, currentState)
                                        }
                                    }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            if (filteredFriends.isNotEmpty()) {
                item {
                    Text(
                        text = context.translation["manager.dialogs.add_friend.category_friends"],
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                itemsIndexed(filteredFriends, key = { _, f -> f.userId }) { index, friend ->
                    var currentState by remember(friend.userId) {
                        mutableStateOf(actionHandler.getFriendState(friend))
                    }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.5.dp),
                        shape = cardShape(filteredFriends.size, index)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                currentState = !currentState
                                coroutineScope.launch(Dispatchers.IO) {
                                    actionHandler.onFriendState(friend, currentState)
                                }
                            },
                            leadingContent = {
                                BitmojiImage(
                                    context = context,
                                    url = BitmojiSelfie.getBitmojiSelfie(
                                        friend.selfieId,
                                        friend.bitmojiId,
                                        BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                                    ),
                                    size = 38
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = friend.displayName ?: friend.mutableUsername,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = friend.mutableUsername,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = currentState,
                                    onCheckedChange = {
                                        currentState = it
                                        coroutineScope.launch(Dispatchers.IO) {
                                            actionHandler.onFriendState(friend, currentState)
                                        }
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
}

typealias AddFriendDialog = AddFriendBottomSheet
