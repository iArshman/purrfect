package me.eternal.purrfect.core.features.impl.messaging

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.database.impl.FriendInfo
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.core.ModContext
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.core.features.impl.messaging.AutoSendSnapToAll
import me.eternal.purrfect.core.features.impl.messaging.Messaging
import me.eternal.purrfect.core.util.ktx.vibrateLongPress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class AutoFriendShortcuts : Feature("Auto Friend Shortcuts") {

    data class ShortcutBucket(
        val id: String,
        val emoji: String,
        val title: String,
        val subtitle: String,
        val friends: List<FriendInfo>,
        val isStory: Boolean = false,
        val isGroup: Boolean = false,
        val groupConversationIds: List<String> = emptyList()
    )

    companion object {
        private const val PREFS_NAME = "purrfect_auto_shortcuts_prefs"
        private const val KEY_PROCESSED_RETENTION_USERS = "retention_processed_users"
        private const val KEY_BUCKET_ASSIGNMENTS = "bucket_assignments"

        private val pendingRetentionUserIds = CopyOnWriteArraySet<String>()
        private val cachedGroupConversationIds = ConcurrentHashMap<String, Boolean>()

        fun numberToEmoji(num: Int): String {
            return when (num) {
                1 -> "\u0031\uFE0F\u20E3" // 1️⃣
                2 -> "\u0032\uFE0F\u20E3" // 2️⃣
                3 -> "\u0033\uFE0F\u20E3" // 3️⃣
                4 -> "\u0034\uFE0F\u20E3" // 4️⃣
                5 -> "\u0035\uFE0F\u20E3" // 5️⃣
                6 -> "\u0036\uFE0F\u20E3" // 6️⃣
                7 -> "\u0037\uFE0F\u20E3" // 7️⃣
                8 -> "\u0038\uFE0F\u20E3" // 8️⃣
                9 -> "\u0039\uFE0F\u20E3" // 9️⃣
                10 -> "\uD83D\uDD1F"       // 🔟
                else -> {
                    // Compose multi-digit keycaps e.g. 11️⃣
                    val digits = num.toString()
                    buildString {
                        for (ch in digits) {
                            append(ch)
                            append("\uFE0F\u20E3")
                        }
                    }
                }
            }
        }

        fun loadGroupConversationIds(context: ModContext): List<String> {
            return runCatching {
                val feedEntries = context.database.getFeedEntries(500)
                feedEntries.filter { it.conversationType == 1 }
                    .mapNotNull { it.key }
                    .distinct()
            }.getOrDefault(emptyList())
        }

        fun loadNewFriends(allFriends: List<FriendInfo>): List<FriendInfo> {
            val fourteenDaysAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
            val recent = allFriends.filter { it.addedTimestamp >= fourteenDaysAgo }
            return if (recent.isNotEmpty()) {
                recent.sortedByDescending { it.addedTimestamp }
            } else {
                // If timestamps are not populated, return the most recent 50 friends by ID order
                allFriends.takeLast(50).reversed()
            }
        }

        /**
         * Partitions friends into numbered buckets of `batchSize` (default 50).
         * Newly added friends automatically overflow into the next available bucket.
         */
        fun buildShortcutBuckets(
            context: ModContext,
            allFriends: List<FriendInfo>,
            batchSize: Int = 50
        ): List<ShortcutBucket> {
            val buckets = mutableListOf<ShortcutBucket>()

            // 1. Static Emoji: 🟣 Stories
            buckets.add(
                ShortcutBucket(
                    id = "stories",
                    emoji = "\uD83D\uDFE3", // 🟣
                    title = "Stories",
                    subtitle = "My Story",
                    friends = emptyList(),
                    isStory = true
                )
            )

            // 2. Static Emoji: 👥 Groups
            val groupIds = loadGroupConversationIds(context)
            buckets.add(
                ShortcutBucket(
                    id = "groups",
                    emoji = "\uD83D\uDC65", // 👥
                    title = "Groups",
                    subtitle = "${groupIds.size} Groups",
                    friends = emptyList(),
                    isGroup = true,
                    groupConversationIds = groupIds
                )
            )

            // 3. Static Emoji: 👶 New Friends
            val newFriends = loadNewFriends(allFriends)
            buckets.add(
                ShortcutBucket(
                    id = "new_friends",
                    emoji = "\uD83D\uDC76", // 👶
                    title = "New Friends",
                    subtitle = "${newFriends.size} Friends",
                    friends = newFriends
                )
            )

            // 4. Static Emoji Numbered Lists: 1️⃣, 2️⃣, 3️⃣... (50 friends per list)
            val chunks = allFriends.chunked(batchSize)
            chunks.forEachIndexed { index, chunk ->
                val listNumber = index + 1
                val emoji = numberToEmoji(listNumber)
                val startIdx = index * batchSize + 1
                val endIdx = startIdx + chunk.size - 1
                buckets.add(
                    ShortcutBucket(
                        id = "list_$listNumber",
                        emoji = emoji,
                        title = "List $listNumber",
                        subtitle = "$startIdx - $endIdx (${chunk.size})",
                        friends = chunk
                    )
                )
            }

            return buckets
        }

        fun onFriendAdded(context: ModContext, userId: String, friendName: String? = null) {
            val config = context.config.messaging
            if (!config.autoDeleteAfterViewingOnFriendAdd.get()) return

            val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processed = prefs.getStringSet(KEY_PROCESSED_RETENTION_USERS, emptySet()) ?: emptySet()
            if (userId in processed) return

            pendingRetentionUserIds.add(userId)

            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val dmConvId = context.database.getDMConversationId(userId)
                    if (!dmConvId.isNullOrBlank()) {
                        applyDeleteAfterViewing(context, dmConvId, userId, friendName)
                    } else {
                        val convManager = context.feature(Messaging::class).conversationManager
                        convManager?.getOneOnOneConversationIds(
                            listOf(userId),
                            onSuccess = { pairs ->
                                val pair = pairs.firstOrNull { it.first == userId }
                                if (pair != null && pair.second.isNotBlank()) {
                                    applyDeleteAfterViewing(context, pair.second, userId, friendName)
                                }
                            },
                            onError = {
                                context.log.warn("AutoFriendShortcuts: Failed to resolve DM conversation for $userId: $it")
                            }
                        )
                    }
                }
            }
        }

        fun applyDeleteAfterViewing(
            context: ModContext,
            conversationId: String,
            userId: String? = null,
            friendName: String? = null
        ) {
            val convManager = context.feature(Messaging::class).conversationManager ?: return

            convManager.updateConversationRetentionPolicy(
                conversationId = conversationId,
                deleteAfterViewing = true,
                onSuccess = {
                    if (userId != null) {
                        pendingRetentionUserIds.remove(userId)
                        val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val updated = (prefs.getStringSet(KEY_PROCESSED_RETENTION_USERS, emptySet())?.toMutableSet()
                            ?: mutableSetOf()).apply { add(userId) }
                        prefs.edit().putStringSet(KEY_PROCESSED_RETENTION_USERS, updated).apply()
                    }

                    val displayName = friendName
                        ?: userId?.let { uid ->
                            val info = context.database.getFriendInfo(uid)
                            info?.displayName?.takeIf { it.isNotBlank() } ?: info?.mutableUsername
                        } ?: "New Friend"

                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Default.DeleteSweep,
                        text = "Auto-set chat with $displayName to Delete After Viewing"
                    )
                },
                onError = { err ->
                    context.log.warn("AutoFriendShortcuts: Failed to set retention policy for $conversationId: $err")
                }
            )
        }

        fun checkAndApplyOnEnterConversation(context: ModContext, conversationId: String) {
            if (!context.config.messaging.autoDeleteAfterViewingOnFriendAdd.get()) return

            val otherParticipant = runCatching { context.database.getDMOtherParticipant(conversationId) }.getOrNull()
            if (otherParticipant != null && otherParticipant in pendingRetentionUserIds) {
                applyDeleteAfterViewing(context, conversationId, otherParticipant)
            }
        }
    }

    override fun init() {
        context.log.info("AutoFriendShortcuts initialized")
    }
}

/**
 * Static Emoji Header Bar for Send-To:
 * Displays static, fixed-position emojis:
 * 🟣 Stories | 👥 Groups | 👶 New Friends | 1️⃣ List 1 | 2️⃣ List 2 | 3️⃣ List 3...
 * Tapping any emoji instantly toggles selecting that exact batch!
 */
@Composable
fun StaticEmojiShortcutHeader(
    context: ModContext,
    allFriends: List<FriendInfo>,
    selectedBucketIds: Set<String>,
    onBucketToggled: (bucket: AutoFriendShortcuts.ShortcutBucket, isSelected: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalPurrfectSkin.current
    val batchSize = context.config.messaging.shortcutFriendsPerList.get().coerceIn(5, 200)

    val buckets = remember(allFriends, batchSize) {
        AutoFriendShortcuts.buildShortcutBuckets(context, allFriends, batchSize)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(skin.textPrimary.copy(alpha = 0.04f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shortcuts & Quick Lists",
                color = skin.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${buckets.size} Categories • Static Emoji Header",
                color = skin.textSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buckets.forEach { bucket ->
                val isSelected = bucket.id in selectedBucketIds
                val activeBorder = if (isSelected) skin.glowPrimary else skin.textPrimary.copy(alpha = 0.12f)
                val activeBackground = if (isSelected) skin.glowPrimary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.04f)

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            context.androidContext.vibrateLongPress()
                            onBucketToggled(bucket, !isSelected)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = activeBackground,
                    border = BorderStroke(1.2.dp, activeBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = bucket.emoji,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bucket.title,
                            color = if (isSelected) skin.textPrimary else skin.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (bucket.isStory) "Story" else if (bucket.isGroup) "${bucket.groupConversationIds.size}" else "${bucket.friends.size}",
                            color = if (isSelected) skin.glowPrimary else skin.textSecondary.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
