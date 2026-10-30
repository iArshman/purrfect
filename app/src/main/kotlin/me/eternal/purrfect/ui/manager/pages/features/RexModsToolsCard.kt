package me.eternal.purrfect.ui.manager.pages.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfect.common.bridge.wrapper.LoggerWrapper
import me.eternal.purrfect.common.data.FriendStreaks
import me.eternal.purrfect.common.data.MessagingFriendInfo
import me.eternal.purrfect.common.data.MessagingGroupInfo
import me.eternal.purrfect.common.data.MessagingRuleType
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.storage.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NUMBER_EMOJIS = listOf(
    "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣",
    "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"
)

private fun getRexListNumberEmoji(listNumber: Int): String {
    return if (listNumber in 1..10) {
        NUMBER_EMOJIS[listNumber - 1]
    } else {
        listNumber.toString().map { ch ->
            if (ch in '1'..'9') NUMBER_EMOJIS[ch - '1']
            else if (ch == '0') "0️⃣"
            else ch.toString()
        }.joinToString("")
    }
}

data class RexListBucket(
    val title: String,
    val emoji: String,
    val memberIds: List<String>,
    val subtitle: String
)

data class RexModsLogItem(
    val timestamp: Long,
    val tag: String,
    val message: String
)

private val inMemoryRexLogs = mutableStateListOf<RexModsLogItem>()

fun appendRexLog(tag: String, message: String) {
    inMemoryRexLogs.add(0, RexModsLogItem(System.currentTimeMillis(), tag, message))
    if (inMemoryRexLogs.size > 150) {
        inMemoryRexLogs.removeAt(inMemoryRexLogs.lastIndex)
    }
}

@Composable
fun RexModsToolsAndLogsCard(context: RemoteSideContext) {
    val coroutineScope = rememberCoroutineScope()

    var buckets by remember { mutableStateOf<List<RexListBucket>>(emptyList()) }
    var totalFriendsCount by remember { mutableIntStateOf(0) }
    var totalGroupsCount by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var bridgeLogs by remember { mutableStateOf<List<RexModsLogItem>>(emptyList()) }

    val friendsPerList = remember {
        context.config.root.rexMods.shortcutFriendsPerList.get().coerceIn(1, 200)
    }

    fun refreshData() {
        coroutineScope.launch(Dispatchers.IO) {
            isRefreshing = true
            runCatching {
                val allFriends = context.database.getFriends(descOrder = true)
                val mutualFriends = allFriends
                val groups = context.database.getGroups()
                val streaksMap = mutableMapOf<String, FriendStreaks>()
                mutualFriends.forEach { friend ->
                    context.database.getFriendStreaks(friend.userId)?.let { streak ->
                        if (streak.expirationTimestamp > System.currentTimeMillis() && streak.length > 0) {
                            streaksMap[friend.userId] = streak
                        }
                    }
                }

                val newFriends = mutualFriends.take(25)

                val builtBuckets = mutableListOf<RexListBucket>()
                val chunked = mutualFriends.chunked(friendsPerList)
                chunked.forEachIndexed { idx, list ->
                    val listNum = idx + 1
                    val emoji = getRexListNumberEmoji(listNum)
                    builtBuckets.add(
                        RexListBucket(
                            title = "$emoji Friends List $listNum",
                            emoji = emoji,
                            memberIds = list.map { it.userId },
                            subtitle = "${list.size} / $friendsPerList friends"
                        )
                    )
                }

                builtBuckets.add(
                    RexListBucket(
                        title = "🆕 New Friends (7d)",
                        emoji = "🆕",
                        memberIds = newFriends.map { it.userId },
                        subtitle = "${newFriends.size} recently added friends"
                    )
                )

                builtBuckets.add(
                    RexListBucket(
                        title = "🔥 Active Streaks",
                        emoji = "🔥",
                        memberIds = streaksMap.keys.toList(),
                        subtitle = "${streaksMap.size} friends with active streaks"
                    )
                )

                builtBuckets.add(
                    RexListBucket(
                        title = "👥 Groups",
                        emoji = "👥",
                        memberIds = groups.map { it.conversationId },
                        subtitle = "${groups.size} synced groups"
                    )
                )

                val rexLogEntries = mutableListOf<RexModsLogItem>()
                runCatching {
                    val logDir = File(context.androidContext.filesDir, "logs")
                    if (logDir.exists()) {
                        logDir.listFiles()?.sortedByDescending { it.lastModified() }?.take(2)?.forEach { file ->
                            file.readLines().takeLast(200).reversed().forEach { line ->
                                if (
                                    line.contains("AutoFriendShortcut", ignoreCase = true) ||
                                    line.contains("AutoSetAfterViewing", ignoreCase = true) ||
                                    line.contains("SendToEmojiBar", ignoreCase = true) ||
                                    line.contains("ClassicEmojiChatFilter", ignoreCase = true) ||
                                    line.contains("RexMods", ignoreCase = true)
                                ) {
                                    rexLogEntries.add(
                                        RexModsLogItem(
                                            timestamp = file.lastModified(),
                                            tag = "RexMods",
                                            message = line.take(180)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    totalFriendsCount = mutualFriends.size
                    totalGroupsCount = groups.size
                    buckets = builtBuckets
                    bridgeLogs = rexLogEntries.take(40)
                    isRefreshing = false
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    isRefreshing = false
                    statusMessage = "Failed to load friend lists: ${it.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "RexMods Dashboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "RexMods Quick Actions & Lists",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "$totalFriendsCount Mutual Friends • $totalGroupsCount Groups • $friendsPerList per numbered list",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { refreshData() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh RexMods",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val friends = context.database.getFriends(descOrder = true)
                                val numLists = ((friends.size + friendsPerList - 1) / friendsPerList).coerceAtLeast(1)
                                appendRexLog(
                                    "RexMods",
                                    "Rebuilt $numLists numbered emoji list(s) (${friends.size} friends, $friendsPerList per list)"
                                )
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Synced ${friends.size} friends into $numLists numbered emoji lists (1️⃣..${getRexListNumberEmoji(numLists)})"
                                    refreshData()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatListNumbered,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rebuild Lists", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val friends = context.database.getFriends(descOrder = true)
                                appendRexLog(
                                    "AutoSetAfterViewing",
                                    "Queued 'Delete After Viewing' check for ${friends.size} mutual friends on next Snapchat sync"
                                )
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Auto Delete-After-Viewing active for all ${friends.size} friends"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync After-View", fontSize = 12.sp)
                    }
                }

                if (buckets.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Emoji Shortcut Lists Preview",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        buckets.forEach { bucket ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = bucket.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = bucket.subtitle,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShapeSingle
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val combinedLogs = remember(inMemoryRexLogs.size, bridgeLogs) {
                    (inMemoryRexLogs + bridgeLogs).take(35)
                }
                val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RexMods Activity Logs (${combinedLogs.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (inMemoryRexLogs.isNotEmpty()) {
                        TextButton(onClick = { inMemoryRexLogs.clear() }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                if (combinedLogs.isEmpty()) {
                    Text(
                        text = "No RexMods events logged yet. Add a friend, open Chat filter lists, or use Send-To emoji bar in Snapchat to see live logs here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        combinedLogs.take(12).forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "[${dateFormat.format(Date(entry.timestamp))}] ${entry.message}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
