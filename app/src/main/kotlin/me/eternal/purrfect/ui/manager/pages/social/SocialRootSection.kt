package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.R
import me.eternal.purrfect.common.data.FriendStreaks
import me.eternal.purrfect.common.data.MessagingFriendInfo
import me.eternal.purrfect.common.data.MessagingGroupInfo
import me.eternal.purrfect.common.data.MessagingRuleType
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.common.ui.AsyncUpdateDispatcher
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.lazyColumnContentPadding
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.common.util.snap.BitmojiSelfie
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.coil.BitmojiImage
import kotlin.math.max

class SocialRootSection : Routes.Section() {
    private val updateScopeLists = AsyncUpdateDispatcher()

    @Composable
    private fun ScopeList(
        scope: SocialScope,
        key: String,
        onManageScope: () -> Unit,
        hasSubAction: Boolean
    ) {
        val remainingHours = remember { context.config.root.streaksReminder.remainingHours.get() }

        when (scope) {
            SocialScope.GROUP -> {
                val groupList = rememberAsyncMutableStateList(defaultValue = listOf(), updateDispatcher = updateScopeLists) {
                    context.database.getGroups()
                }
                val filteredGroups = remember(groupList.size, key) {
                    if (key == "all") groupList
                    else {
                        val ruleIds = context.database.getRuleIds(key).toSet()
                        groupList.filter { it.conversationId in ruleIds }
                    }
                }

                if (filteredGroups.isEmpty()) {
                    EmptySocialState(isGroup = true)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = lazyColumnContentPadding(hasSubAction = hasSubAction, hasFAB = true),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(filteredGroups, key = { _, g -> g.conversationId }) { index, group ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = cardShape(filteredGroups.size, index)
                            ) {
                                ListItem(
                                    modifier = Modifier.clickable {
                                        routes.manageScope.navigate {
                                            put("id", group.conversationId)
                                            put("scope", SocialScope.GROUP.key)
                                        }
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Rounded.Group,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = group.name,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        FilledTonalIconButton(
                                            onClick = {
                                                routes.messagingPreview.navigate {
                                                    put("id", group.conversationId)
                                                    put("scope", SocialScope.GROUP.key)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.RemoveRedEye, contentDescription = null)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            SocialScope.FRIEND -> {
                val friendList = rememberAsyncMutableStateList(defaultValue = listOf(), updateDispatcher = updateScopeLists) {
                    context.database.getFriends(descOrder = true)
                }
                val streaksMap = remember(friendList.size) {
                    val map = mutableMapOf<String, FriendStreaks>()
                    friendList.forEach { friend ->
                        context.database.getFriendStreaks(friend.userId)?.let { map[friend.userId] = it }
                    }
                    map
                }

                val filteredFriends = remember(friendList.size, key) {
                    if (key == "all") friendList
                    else {
                        val ruleIds = context.database.getRuleIds(key).toSet()
                        friendList.filter { it.userId in ruleIds }
                    }
                }

                if (filteredFriends.isEmpty()) {
                    EmptySocialState(isGroup = false)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = lazyColumnContentPadding(hasSubAction = hasSubAction, hasFAB = true),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(filteredFriends, key = { _, f -> f.userId }) { index, friend ->
                            val streaks = streaksMap[friend.userId]
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = cardShape(filteredFriends.size, index)
                            ) {
                                ListItem(
                                    modifier = Modifier.clickable {
                                        routes.manageScope.navigate {
                                            put("id", friend.userId)
                                            put("scope", SocialScope.FRIEND.key)
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
                                            size = 42
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = friend.displayName ?: friend.mutableUsername,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = friend.mutableUsername,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (streaks != null && !streaks.isExpired && streaks.length > 0) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.streak_icon),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (streaks.isAboutToExpire(remainingHours))
                                                        MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = context.translation.format(
                                                        "manager.sections.social.streaks_expiration_short",
                                                        "hours" to max(0, ((streaks.expirationTimestamp - System.currentTimeMillis()) / 3600000).toInt()).toString()
                                                    ),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        FilledTonalIconButton(
                                            onClick = {
                                                routes.messagingPreview.navigate {
                                                    put("id", friend.userId)
                                                    put("scope", SocialScope.FRIEND.key)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.RemoveRedEye, contentDescription = null)
                                        }
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

    @Composable
    private fun EmptySocialState(isGroup: Boolean) {
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
                        imageVector = if (isGroup) Icons.Rounded.Group else Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(90.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.translation["manager.sections.social.${if (isGroup) "empty_groups" else "empty_friends"}"],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val titles = remember {
            listOf("friends", "groups").map { context.translation["manager.sections.social.$it"] }
        }
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        var currentTabIndex by remember { mutableIntStateOf(0) }

        val availableRules = remember {
            MessagingRuleType.entries.filter { it.listMode && context.config.root.rules.getRuleState(it) != null }.map { it.key }
        }
        var selectedFilterKey by remember { mutableStateOf("all") }
        var addFriendBottomSheet by remember { mutableStateOf(null as AddFriendBottomSheet?) }

        if (addFriendBottomSheet != null) {
            addFriendBottomSheet?.Content {
                addFriendBottomSheet = null
            }
        }

        setFloatingActionButton {
            FloatingActionButton(
                onClick = {
                    addFriendBottomSheet = AddFriendBottomSheet(
                        context,
                        AddFriendBottomSheet.Actions(
                            onFriendState = { friend, state ->
                                if (state) {
                                    context.bridgeService?.triggerScopeSync(SocialScope.FRIEND, friend.userId)
                                } else {
                                    context.database.deleteFriend(friend.userId)
                                }
                                coroutineScope.launch { updateScopeLists.dispatch() }
                            },
                            onGroupState = { group, state ->
                                if (state) {
                                    context.bridgeService?.triggerScopeSync(SocialScope.GROUP, group.conversationId)
                                } else {
                                    context.database.deleteGroup(group.conversationId)
                                }
                                coroutineScope.launch { updateScopeLists.dispatch() }
                            },
                            getFriendState = { friend -> context.database.getFriendInfo(friend.userId) != null },
                            getGroupState = { group -> context.database.getGroupInfo(group.conversationId) != null }
                        ),
                        pinnedIds = if (selectedFilterKey != "all") {
                            context.database.getRuleIds(selectedFilterKey)
                        } else null,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null
                )
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            currentTabIndex = pagerState.currentPage
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding(vertical = 0.dp, horizontal = 0.dp))
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

            if (availableRules.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = ( listOf("all") + availableRules ).indexOf(selectedFilterKey).coerceAtLeast(0),
                    edgePadding = 10.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    (listOf("all") + availableRules).forEach { ruleKey ->
                        FilterChip(
                            selected = selectedFilterKey == ruleKey,
                            onClick = { selectedFilterKey = ruleKey },
                            label = {
                                Text(
                                    text = if (ruleKey == "all") "All"
                                    else context.translation["rules.properties.$ruleKey.name"]
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> ScopeList(
                        scope = SocialScope.FRIEND,
                        key = selectedFilterKey,
                        onManageScope = { coroutineScope.launch { updateScopeLists.dispatch() } },
                        hasSubAction = false
                    )
                    1 -> ScopeList(
                        scope = SocialScope.GROUP,
                        key = selectedFilterKey,
                        onManageScope = { coroutineScope.launch { updateScopeLists.dispatch() } },
                        hasSubAction = false
                    )
                }
            }
        }
    }
}
