package me.eternal.purrfect.core.features.impl.messaging

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.data.FriendLinkType
import me.eternal.purrfect.common.database.impl.FriendInfo
import me.eternal.purrfect.common.ui.createComposeAlertDialog
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.core.ModContext
import me.eternal.purrfect.core.event.events.impl.AddViewEvent
import me.eternal.purrfect.core.event.events.impl.ConversationUpdateEvent
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.core.ui.PurrfectOverlayTheme
import me.eternal.purrfect.core.util.hook.HookStage
import me.eternal.purrfect.core.util.hook.hook
import me.eternal.purrfect.core.util.ktx.vibrateLongPress
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

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

    data class PendingSendToSelection(
        val selectedFriends: List<FriendInfo>,
        val selectedGroupIds: List<String>,
        val unselectedUserIds: Set<String>
    )

    companion object {
        private const val PREFS_NAME = "purrfect_auto_shortcuts_prefs"
        private const val KEY_PROCESSED_RETENTION_USERS = "retention_processed_users"
        private const val KEY_KNOWN_MUTUAL_FRIENDS = "known_mutual_friends_v2"
        private const val KEY_BASELINE_INITIALIZED = "baseline_initialized_v2"

        private const val CHAT_SHORTCUT_BAR_TAG = "rexmods_chat_shortcut_bar"
        private const val SEND_TO_BAR_TAG = "rexmods_send_to_emoji_bar"
        private const val ROW_CLICK_HOOKED_TAG = "rexmods_sendto_row_hooked"

        private val pendingRetentionUserIds = CopyOnWriteArraySet<String>()
        private val inFlightRetentionUserIds = CopyOnWriteArraySet<String>()
        private val isUpdatingText = ThreadLocal.withInitial { false }

        // Active Send-To selection state (populated when user clicks ✅ All or 1️⃣, 2️⃣, 👥, 👶 on Send-To page)
        private val activeSendToBucketIds = CopyOnWriteArraySet<String>()
        private val activeSendToSelectedFriends = ConcurrentHashMap<String, FriendInfo>()
        private val activeSendToSelectedGroups = CopyOnWriteArraySet<String>()
        private val activeSendToUnselectedUserIds = CopyOnWriteArraySet<String>()
        @Volatile
        private var activeSendToTimestamp: Long = 0L

        // Map of classic Chat Homescreen filter labels -> Old-Style Static Emojis
        private val CLASSIC_FILTER_EMOJI_MAP = mapOf(
            "all" to "💬",
            "unread" to "🟦",
            "groups" to "👥",
            "group" to "👥",
            "reply" to "↩️",
            "replies" to "↩️",
            "stories" to "🟣",
            "story" to "🟣",
            "birthday" to "🎂",
            "birthdays" to "🎂",
            "new friends" to "👶",
            "new friend" to "👶",
            "streaks" to "🔥",
            "streak" to "🔥",
            "best friends" to "💛",
            "unanswered" to "⏳"
        )

        fun logRex(context: ModContext, message: String, isWarn: Boolean = false) {
            val formatted = if (message.startsWith("[RexMods]")) message else "[RexMods] $message"
            if (isWarn) {
                context.log.warn(formatted, "RexMods")
            } else {
                context.log.info(formatted, "RexMods")
            }
        }

        fun transformFilterLabelToClassicEmoji(rawText: String): String? {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty() || trimmed.length > 26) return null
            // Already transformed
            if (CLASSIC_FILTER_EMOJI_MAP.values.any { trimmed.startsWith(it) }) return null

            val lower = trimmed.lowercase()
            // Exact match (e.g. "All", "Groups", "Reply", "Birthday")
            CLASSIC_FILTER_EMOJI_MAP[lower]?.let { return it }

            // Match label + badge count (e.g. "Unread 9+", "Stories 6", "Groups 3", "Unread (4)")
            for ((key, emoji) in CLASSIC_FILTER_EMOJI_MAP) {
                if (lower.startsWith("$key ")) {
                    val suffix = trimmed.substring(key.length).trim()
                    if (suffix.matches(Regex("^[0-9+()•·\\s]+$"))) {
                        return "$emoji $suffix"
                    }
                }
            }
            return null
        }

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
                allFriends.takeLast(50).reversed()
            }
        }

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
            val chunks = allFriends.chunked(batchSize.coerceIn(5, 200))
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

        fun hasPendingSendToSelection(): Boolean {
            if (activeSendToSelectedFriends.isEmpty() && activeSendToSelectedGroups.isEmpty()) return false
            // Expire selection if older than 5 minutes
            if (System.currentTimeMillis() - activeSendToTimestamp > 5 * 60 * 1000L) {
                clearPendingSendToSelection()
                return false
            }
            return true
        }

        fun clearPendingSendToSelection() {
            activeSendToBucketIds.clear()
            activeSendToSelectedFriends.clear()
            activeSendToSelectedGroups.clear()
            activeSendToUnselectedUserIds.clear()
            activeSendToTimestamp = 0L
        }

        fun consumePendingSendToSelection(context: ModContext): PendingSendToSelection? {
            if (!hasPendingSendToSelection()) return null
            val friends = activeSendToSelectedFriends.values.filter { it.userId !in activeSendToUnselectedUserIds }
            val groups = activeSendToSelectedGroups.toList()
            val unselected = activeSendToUnselectedUserIds.toSet()
            clearPendingSendToSelection()
            logRex(context, "Consumed Send-To selection: ${friends.size} active friends, ${groups.size} groups, ${unselected.size} unselected")
            return PendingSendToSelection(
                selectedFriends = friends,
                selectedGroupIds = groups,
                unselectedUserIds = unselected
            )
        }

        fun onFriendGrpcCall(context: ModContext, uri: String) {
            if (!context.config.rexMods.autoDeleteAfterViewingOnFriendAdd.get()) return
            logRex(context, "Detected friend gRPC call: $uri — scheduling friend diff scan")
            CoroutineScope(Dispatchers.IO).launch {
                delay(1200)
                scanForNewFriends(context, "gRPC:$uri")
                delay(3500)
                scanForNewFriends(context, "gRPC-delayed:$uri")
            }
        }

        fun scanForNewFriends(context: ModContext, source: String) {
            if (!context.config.rexMods.autoDeleteAfterViewingOnFriendAdd.get()) return
            runCatching {
                val myUserId = runCatching { context.database.myUserId }.getOrNull() ?: return
                val allMutuals = context.database.getAllFriends().filter { friend ->
                    friend.friendLinkType == FriendLinkType.MUTUAL.value &&
                        friend.userId != myUserId &&
                        friend.userId !in AutoSendSnapToAll.SYSTEM_EXCLUDED_USER_IDS
                }
                if (allMutuals.isEmpty()) return

                val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val processed = prefs.getStringSet(KEY_PROCESSED_RETENTION_USERS, emptySet()) ?: emptySet()
                val known = prefs.getStringSet(KEY_KNOWN_MUTUAL_FRIENDS, null)
                val isInitialized = prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)
                val now = System.currentTimeMillis()
                val fifteenMinutesAgo = now - (15L * 60 * 1000L)

                if (!isInitialized || known == null) {
                    // Seed baseline with friends added more than 15 minutes ago
                    val baselineIds = allMutuals
                        .filter { it.addedTimestamp in 1 until fifteenMinutesAgo }
                        .mapNotNull { it.userId }
                        .toSet()
                    prefs.edit()
                        .putStringSet(KEY_KNOWN_MUTUAL_FRIENDS, allMutuals.mapNotNull { it.userId }.toSet())
                        .putBoolean(KEY_BASELINE_INITIALIZED, true)
                        .apply()
                    logRex(context, "Initialized mutual friends baseline (${allMutuals.size} friends) via $source")

                    // Check if any friend was just added in the last 15 minutes
                    allMutuals.filter { it.addedTimestamp >= fifteenMinutesAgo && it.userId !in processed && it.userId !in baselineIds }
                        .forEach { recentFriend ->
                            val uid = recentFriend.userId ?: return@forEach
                            onFriendAdded(
                                context,
                                uid,
                                recentFriend.displayName ?: recentFriend.mutableUsername,
                                source = "$source(recent-15m)"
                            )
                        }
                    return
                }

                val updatedKnown = known.toMutableSet()
                var changed = false
                for (friend in allMutuals) {
                    val uid = friend.userId ?: continue
                    val isNewInDb = uid !in known
                    val isVeryRecent = friend.addedTimestamp >= fifteenMinutesAgo && uid !in processed
                    if (isNewInDb || isVeryRecent) {
                        updatedKnown.add(uid)
                        changed = true
                        if (uid !in processed) {
                            onFriendAdded(
                                context,
                                uid,
                                friend.displayName ?: friend.mutableUsername,
                                source = source
                            )
                        }
                    }
                }
                if (changed) {
                    prefs.edit().putStringSet(KEY_KNOWN_MUTUAL_FRIENDS, updatedKnown).apply()
                }
            }.onFailure { err ->
                logRex(context, "scanForNewFriends failed ($source): ${err.message}", isWarn = true)
            }
        }

        fun onFriendAdded(context: ModContext, userId: String, friendName: String? = null, source: String = "event") {
            if (!context.config.rexMods.autoDeleteAfterViewingOnFriendAdd.get()) return
            if (userId in AutoSendSnapToAll.SYSTEM_EXCLUDED_USER_IDS) return

            val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processed = prefs.getStringSet(KEY_PROCESSED_RETENTION_USERS, emptySet()) ?: emptySet()
            if (userId in processed) return

            val isFirstQueue = pendingRetentionUserIds.add(userId)
            val batchSize = context.config.rexMods.shortcutFriendsPerList.get().coerceIn(5, 200)
            val totalFriends = AutoSendSnapToAll.loadAllMutualFriends(context).size
            val listNum = ((totalFriends - 1).coerceAtLeast(0) / batchSize) + 1

            if (isFirstQueue) {
                logRex(
                    context,
                    "New friend added ($source): ${friendName ?: userId} -> Assigned to Shortcut List ${numberToEmoji(listNum)} & queued for Delete After Viewing"
                )
            }

            if (!inFlightRetentionUserIds.add(userId)) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val delays = listOf(0L, 1800L, 4500L, 9000L)
                    for ((attemptIdx, waitMs) in delays.withIndex()) {
                        if (waitMs > 0) delay(waitMs)
                        if (userId !in pendingRetentionUserIds) break

                        val dmConvId = runCatching { context.database.getDMConversationId(userId) }.getOrNull()
                        if (!dmConvId.isNullOrBlank()) {
                            logRex(context, "Resolved DM conversation $dmConvId for $userId on attempt #${attemptIdx + 1}")
                            applyDeleteAfterViewing(context, dmConvId, userId, friendName)
                            break
                        } else {
                            val convManager = context.feature(Messaging::class).conversationManager
                            if (convManager != null) {
                                convManager.getOneOnOneConversationIds(
                                    listOf(userId),
                                    onSuccess = { pairs ->
                                        val pair = pairs.firstOrNull { it.first == userId }
                                        if (pair != null && pair.second.isNotBlank()) {
                                            logRex(context, "Fetched 1-on-1 conversation ${pair.second} for $userId via ConversationManager")
                                            applyDeleteAfterViewing(context, pair.second, userId, friendName)
                                        }
                                    },
                                    onError = { err ->
                                        logRex(context, "getOneOnOneConversationIds attempt #${attemptIdx + 1} for $userId failed: $err", isWarn = true)
                                    }
                                )
                            } else {
                                logRex(context, "ConversationManager not ready yet for $userId (attempt #${attemptIdx + 1})", isWarn = true)
                            }
                        }
                    }
                } finally {
                    inFlightRetentionUserIds.remove(userId)
                }
            }
        }

        fun applyDeleteAfterViewing(
            context: ModContext,
            conversationId: String,
            userId: String? = null,
            friendName: String? = null
        ) {
            val convManager = context.feature(Messaging::class).conversationManager
            if (convManager == null) {
                logRex(context, "Cannot set Delete After Viewing for $conversationId: ConversationManager is null", isWarn = true)
                return
            }

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
                            val info = runCatching { context.database.getFriendInfo(uid) }.getOrNull()
                            info?.displayName?.takeIf { it.isNotBlank() } ?: info?.mutableUsername
                        } ?: "New Friend"

                    logRex(context, "Successfully set chat with $displayName ($conversationId) to Delete After Viewing!")

                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Default.DeleteSweep,
                        text = "RexMods: Chat with $displayName set to Delete After Viewing"
                    )
                },
                onError = { err ->
                    logRex(context, "Failed to set Delete After Viewing for $conversationId: $err", isWarn = true)
                }
            )
        }

        fun checkAndApplyOnEnterConversation(context: ModContext, conversationId: String) {
            if (!context.config.rexMods.autoDeleteAfterViewingOnFriendAdd.get()) return

            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val otherParticipant = context.database.getDMOtherParticipant(conversationId) ?: return@launch
                    if (otherParticipant in AutoSendSnapToAll.SYSTEM_EXCLUDED_USER_IDS) return@launch

                    val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val processed = prefs.getStringSet(KEY_PROCESSED_RETENTION_USERS, emptySet()) ?: emptySet()
                    if (otherParticipant in processed) return@launch

                    val friendInfo = context.database.getFriendInfo(otherParticipant)
                    val oneDayAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000L)
                    val isRecentlyAdded = friendInfo != null &&
                        friendInfo.friendLinkType == FriendLinkType.MUTUAL.value &&
                        friendInfo.addedTimestamp >= oneDayAgo

                    if (otherParticipant in pendingRetentionUserIds || isRecentlyAdded) {
                        logRex(
                            context,
                            "Entering conversation $conversationId with new friend ${friendInfo?.mutableUsername ?: otherParticipant} -> Applying Delete After Viewing"
                        )
                        applyDeleteAfterViewing(
                            context = context,
                            conversationId = conversationId,
                            userId = otherParticipant,
                            friendName = friendInfo?.displayName ?: friendInfo?.mutableUsername
                        )
                    }
                }
            }
        }
    }

    private val decoratedFilterContainers = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val decoratedSendToContainers = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val backgroundScannerStarted = AtomicBoolean(false)

    override fun init() {
        logRex(context, "Initializing RexMods AutoFriendShortcuts & Classic Emoji Headers")

        // 1. Subscribe to ConversationUpdateEvent to catch newly created 1-on-1 conversations
        context.event.subscribe(ConversationUpdateEvent::class) { event ->
            checkAndApplyOnEnterConversation(context, event.conversationId)
        }

        // 2. Hook AddViewEvent for Chat Homescreen filter bar & Send-To screen shortcut bar
        context.event.subscribe(AddViewEvent::class) { event ->
            val classicChatHeader = context.config.rexMods.classicEmojiChatHeader.get()
            val sendToBarEnabled = context.config.rexMods.sendToEmojiSelectBar.get()

            if (classicChatHeader) {
                inspectViewForChatHomescreenHeader(event.view, event.parent)
            }
            if (sendToBarEnabled) {
                inspectViewForSendToScreen(event.view, event.parent)
            }
        }

        onNextActivityCreate {
            // Start background mutual friend diff scanner
            if (backgroundScannerStarted.compareAndSet(false, true)) {
                context.coroutineScope.launch(Dispatchers.IO) {
                    delay(3000)
                    scanForNewFriends(context, "startup")
                    while (true) {
                        delay(8000)
                        if (!context.isMainActivityPaused) {
                            scanForNewFriends(context, "periodic")
                        }
                    }
                }
            }

            // 3. Hook TextView.setText so Chat Homescreen filter pills ("All", "Unread 9+", "Groups", "Reply", "Stories 6", "Birthday")
            // immediately render as classic emojis ("💬", "🟦 9+", "👥", "↩️", "🟣 6", "🎂")
            TextView::class.java.hook("setText", HookStage.BEFORE) { param ->
                if (isUpdatingText.get() == true) return@hook
                if (!context.config.rexMods.classicEmojiChatHeader.get()) return@hook

                val rawCharSeq = param.argNullable<CharSequence>(0) ?: return@hook
                val rawText = rawCharSeq.toString()
                val emojiText = transformFilterLabelToClassicEmoji(rawText) ?: return@hook

                val tv = param.thisObject() as? TextView ?: return@hook
                if (isLikelyFilterPillTextView(tv)) {
                    param.setArg(0, emojiText)
                }
            }

            // 4. Also hook Canvas.drawText in case Snapchat's filter pills use custom canvas text views (SnapFontTextView)
            Canvas::class.java.hook("drawText", HookStage.BEFORE) { param ->
                if (!context.config.rexMods.classicEmojiChatHeader.get()) return@hook
                val firstArg = param.argNullable<Any>(0) ?: return@hook
                if (firstArg is String) {
                    val replaced = transformFilterLabelToClassicEmoji(firstArg) ?: return@hook
                    // Only replace short filter labels when drawn with pill-sized text (11sp..18sp)
                    val paint = param.args().lastOrNull() as? android.graphics.Paint ?: return@hook
                    val density = context.resources.displayMetrics.density
                    val textSizeDp = paint.textSize / density
                    if (textSizeDp in 10f..19f) {
                        param.setArg(0, replaced)
                    }
                }
            }
        }
    }

    private fun isLikelyFilterPillTextView(tv: TextView): Boolean {
        var current: View? = tv
        var depth = 0
        while (current != null && depth < 7) {
            val cls = current.javaClass.name.lowercase()
            val resName = runCatching {
                if (current.id != View.NO_ID && current.id > 0) context.resources.getResourceEntryName(current.id).lowercase() else ""
            }.getOrDefault("")

            if (cls.contains("recyclerview") ||
                cls.contains("shortcut") ||
                cls.contains("filter") ||
                cls.contains("chip") ||
                cls.contains("pill") ||
                cls.contains("tab") ||
                cls.contains("header") ||
                resName.contains("shortcut") ||
                resName.contains("filter") ||
                resName.contains("pill") ||
                resName.contains("feed")
            ) {
                return true
            }
            current = current.parent as? View
            depth++
        }
        // Fallback: if the TextView isn't attached yet during inflate/bind, allow replacement for exact filter pill words
        return tv.parent == null || tv.maxLines == 1
    }

    private fun collectViewTexts(view: View, depth: Int = 0, maxDepth: Int = 4): List<String> {
        if (depth > maxDepth) return emptyList()
        val own = buildList {
            if (view is TextView) {
                view.text?.toString()?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            }
            view.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }
        if (view !is ViewGroup) return own
        val childrenTexts = mutableListOf<String>()
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue
            childrenTexts.addAll(collectViewTexts(child, depth + 1, maxDepth))
        }
        return own + childrenTexts
    }

    private fun replaceFilterTextViewsRecursively(view: View) {
        if (view is TextView) {
            val currentText = view.text?.toString() ?: ""
            val replaced = transformFilterLabelToClassicEmoji(currentText)
            if (replaced != null && replaced != currentText) {
                isUpdatingText.set(true)
                try {
                    view.text = replaced
                } finally {
                    isUpdatingText.set(false)
                }
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                view.getChildAt(i)?.let { replaceFilterTextViewsRecursively(it) }
            }
        }
    }

    /**
     * Detects the Chat Homescreen filter bar below the Account icon
     * ("All", "Unread", "Groups", "Reply", "Stories", "Birthday")
     * converts its labels to classic emojis, and injects our numbered 50-friend shortcut pills (1️⃣, 2️⃣, 3️⃣...).
     */
    private fun inspectViewForChatHomescreenHeader(view: View, parent: ViewGroup) {
        view.post {
            runCatching {
                replaceFilterTextViewsRecursively(view)

                val candidateGroup = (view as? ViewGroup) ?: parent
                val texts = collectViewTexts(candidateGroup, maxDepth = 3).map { it.lowercase() }
                val filterMatches = texts.count { t ->
                    t == "all" || t == "💬" ||
                        t.startsWith("unread") || t.startsWith("🟦") ||
                        t == "groups" || t == "👥" ||
                        t == "reply" || t == "↩️" ||
                        t.startsWith("stories") || t.startsWith("🟣") ||
                        t.startsWith("birthday") || t.startsWith("🎂")
                }

                if (filterMatches >= 2 && context.config.rexMods.staticEmojiShortcutHeader.get()) {
                    val hostParent = candidateGroup.parent as? ViewGroup ?: return@post
                    if ( decoratedFilterContainers.contains(hostParent)) return@post
                    if (hostParent.findViewWithTag<View>(CHAT_SHORTCUT_BAR_TAG) != null) return@post

                    decoratedFilterContainers.add(hostParent)
                    val shortcutStrip = buildNativeChatHomescreenNumberedListBar(hostParent.context)
                    if (hostParent is LinearLayout && hostParent.orientation == LinearLayout.VERTICAL) {
                        val idx = hostParent.indexOfChild(candidateGroup).coerceAtLeast(0)
                        hostParent.addView(shortcutStrip, (idx + 1).coerceAtMost(hostParent.childCount))
                        logRex(context, "Attached Classic Emoji & Numbered Friend Lists bar (1️⃣ 2️⃣ 3️⃣) to Chat Homescreen header")
                    }
                }
            }
        }
    }

    private fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }

    private fun buildNativeChatHomescreenNumberedListBar(ctx: Context): View {
        val scroll = HorizontalScrollView(ctx).apply {
            tag = CHAT_SHORTCUT_BAR_TAG
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(ctx, 2)
                bottomMargin = dp(ctx, 4)
            }
            setPadding(dp(ctx, 10), dp(ctx, 2), dp(ctx, 10), dp(ctx, 2))
            clipToPadding = false
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun populateChips() {
            row.removeAllViews()
            val allFriends = AutoSendSnapToAll.loadAllMutualFriends(context)
            val batchSize = context.config.rexMods.shortcutFriendsPerList.get().coerceIn(5, 200)
            val buckets = buildShortcutBuckets(context, allFriends, batchSize)

            buckets.forEach { bucket ->
                val chip = TextView(ctx).apply {
                    val countLabel = when {
                        bucket.isStory -> ""
                        bucket.isGroup -> if (bucket.groupConversationIds.isNotEmpty()) " ${bucket.groupConversationIds.size}" else ""
                        else -> " ${bucket.friends.size}"
                    }
                    text = "${bucket.emoji}$countLabel"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(AndroidColor.WHITE)
                    setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 18).toFloat()
                        setColor(AndroidColor.parseColor("#26FFFFFF"))
                        setStroke(dp(ctx, 1), AndroidColor.parseColor("#40FFFFFF"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(ctx, 6)
                    }
                    setOnClickListener {
                        ctx.vibrateLongPress()
                        showChatHomescreenBucketDialog(bucket, allFriends.size)
                    }
                }
                row.addView(chip)
            }
        }

        populateChips()
        scroll.addView(row)
        return scroll
    }

    private fun showChatHomescreenBucketDialog(bucket: ShortcutBucket, totalFriendsCount: Int) {
        val mainActivity = context.mainActivity ?: return
        logRex(context, "Opened Chat Homescreen shortcut list ${bucket.emoji} ${bucket.title} (${bucket.subtitle})")
        mainActivity.runOnUiThread {
            createComposeAlertDialog(mainActivity) { alertDialog ->
                PurrfectOverlayTheme {
                    val skin = LocalPurrfectSkin.current
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredFriends = remember(bucket.friends, searchQuery) {
                        if (searchQuery.isBlank()) bucket.friends
                        else bucket.friends.filter {
                            (it.displayName ?: "").contains(searchQuery, ignoreCase = true) ||
                                (it.mutableUsername ?: "").contains(searchQuery, ignoreCase = true)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        color = skin.cardOverlayColor,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, skin.glowPrimary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = bucket.emoji, fontSize = 26.sp)
                                    Column {
                                        Text(
                                            text = "${bucket.title} • ${bucket.subtitle}",
                                            color = skin.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Total Mutual Friends: $totalFriendsCount",
                                            color = skin.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { alertDialog.dismiss() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = skin.textSecondary)
                                }
                            }

                            if (bucket.friends.isNotEmpty()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Search in ${bucket.title}...") }
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 320.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(filteredFriends, key = { it.userId ?: it.hashCode() }) { friend ->
                                        val friendUid = friend.userId ?: ""
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    context.androidContext.vibrateLongPress()
                                                    if (friendUid.isNotBlank()) {
                                                        val dmId = context.database.getDMConversationId(friendUid)
                                                        if (!dmId.isNullOrBlank()) {
                                                            applyDeleteAfterViewing(
                                                                context = context,
                                                                conversationId = dmId,
                                                                userId = friendUid,
                                                                friendName = friend.displayName ?: friend.mutableUsername
                                                            )
                                                        }
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = skin.textPrimary.copy(alpha = 0.05f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = friend.displayName?.takeIf { it.isNotBlank() }
                                                            ?: friend.mutableUsername ?: "Friend",
                                                        color = skin.textPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "@${friend.mutableUsername ?: "unknown"}",
                                                        color = skin.textSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Text(
                                                    text = "Set After View",
                                                    color = skin.glowPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (bucket.isGroup) "${bucket.groupConversationIds.size} Group Conversations ready for Send-To."
                                    else "Use ${bucket.emoji} on the Send-To screen to quickly select ${bucket.title}.",
                                    color = skin.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects the Send-To screen when sending a direct Snap and injects the
     * Emoji Shortcut & ✅ Select All Bar right into the Send-To header area.
     */
    private fun inspectViewForSendToScreen(view: View, parent: ViewGroup) {
        view.post {
            runCatching {
                val clsName = view.javaClass.name.lowercase()
                val parentClsName = parent.javaClass.name.lowercase()
                val resName = runCatching {
                    if (view.id != View.NO_ID && view.id > 0) context.resources.getResourceEntryName(view.id).lowercase() else ""
                }.getOrDefault("")

                // Also hook recipient row clicks on the Send-To list so clicking a friend row toggles them in activeSendToUnselectedUserIds
                if (parentClsName.contains("recyclerview") && hasPendingSendToSelection()) {
                    hookSendToRecipientRowForDirectUnselect(view)
                }

                val texts = collectViewTexts(view, maxDepth = 3)
                val looksLikeSendTo = resName.contains("send_to") ||
                    resName.contains("sendto") ||
                    clsName.contains("sendto") ||
                    texts.any {
                        val lower = it.lowercase()
                        lower == "send to..." || lower == "send to" || lower == "last snap"
                    }

                if (!looksLikeSendTo) return@post

                // Walk up to find a suitable ViewGroup container on the Send-To page
                var container: ViewGroup? = (view as? ViewGroup) ?: parent
                var depth = 0
                while (container != null && depth < 5) {
                    val p = container.parent as? ViewGroup
                    if (p != null && (p is LinearLayout || p is FrameLayout || p.javaClass.name.endsWith("ConstraintLayout") || p.javaClass.name.endsWith("CoordinatorLayout"))) {
                        if (p.height >= context.resources.displayMetrics.heightPixels / 2 || p is LinearLayout) {
                            container = p
                            break
                        }
                    }
                    container = p
                    depth++
                }

                val targetRoot = container ?: parent
                if (decoratedSendToContainers.contains(targetRoot)) return@post
                if (targetRoot.findViewWithTag<View>(SEND_TO_BAR_TAG) != null) return@post

                decoratedSendToContainers.add(targetRoot)
                val sendToBar = buildNativeSendToEmojiSelectBar(targetRoot.context, targetRoot)

                when (targetRoot) {
                    is LinearLayout -> {
                        if (targetRoot.orientation == LinearLayout.VERTICAL) {
                            val insertIdx = if (targetRoot.childCount > 0) 1 else 0
                            targetRoot.addView(sendToBar, insertIdx)
                        } else {
                            (targetRoot.parent as? ViewGroup)?.addView(sendToBar)
                        }
                    }
                    else -> {
                        val lp = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.TOP
                            topMargin = dp(targetRoot.context, 92)
                        }
                        targetRoot.addView(sendToBar, lp)
                    }
                }
                logRex(context, "Injected Emoji Shortcut & ✅ Select All Bar onto Send-To screen")
            }
        }
    }

    /**
     * Hooks a recipient row in Snapchat's Send-To RecyclerView so when the user clicks
     * a person on the Send-To page after clicking ✅ Select All or 1️⃣, 2️⃣, it directly
     * unselects (or re-selects) that person from the batch send!
     */
    private fun hookSendToRecipientRowForDirectUnselect(rowView: View) {
        if (rowView.getTag(rowView.id.takeIf { it > 0 } ?: 0x7f000001) == ROW_CLICK_HOOKED_TAG) return
        rowView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {}
        })

        // Observe touch/click without breaking Snapchat's native click listener
        rowView.setOnTouchListener { v, motionEvent ->
            if (motionEvent.action == android.view.MotionEvent.ACTION_UP && hasPendingSendToSelection()) {
                val labels = collectViewTexts(v, maxDepth = 4)
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 1 }
                if (labels.isNotEmpty()) {
                    val matchedFriend = activeSendToSelectedFriends.values.firstOrNull { friend ->
                        val disp = friend.displayName?.trim()
                        val uname = friend.mutableUsername?.trim()
                        labels.any { label ->
                            (disp != null && label.equals(disp, ignoreCase = true)) ||
                                (uname != null && label.equals(uname, ignoreCase = true))
                        }
                    }
                    if (matchedFriend != null) {
                        val uid = matchedFriend.userId
                        val name = matchedFriend.displayName ?: matchedFriend.mutableUsername ?: uid
                        if (activeSendToUnselectedUserIds.contains(uid)) {
                            activeSendToUnselectedUserIds.remove(uid)
                            logRex(context, "Re-selected $name on Send-To page")
                        } else {
                            activeSendToUnselectedUserIds.add(uid)
                            val remaining = activeSendToSelectedFriends.size - activeSendToUnselectedUserIds.size
                            logRex(context, "Unselected $name on Send-To page ($remaining recipients remaining)")
                            context.inAppOverlay.showStatusToast(
                                icon = Icons.Default.PersonRemove,
                                text = "Unselected $name ($remaining selected)"
                            )
                        }
                    }
                }
            }
            false // Let Snapchat handle native row click as well
        }
    }

    /**
     * Activates Snapchat's native Send bottom bar by clicking the first unselected recipient row
     * in the Send-To RecyclerView if no row is currently selected.
     */
    private fun ensureNativeSendToRowClicked(rootView: View) {
        rootView.post {
            runCatching {
                fun findFirstRecyclerView(v: View): ViewGroup? {
                    if (v is ViewGroup) {
                        if (v.javaClass.name.lowercase().contains("recyclerview") && v.childCount > 1) {
                            return v
                        }
                        for (i in 0 until v.childCount) {
                            val found = findFirstRecyclerView(v.getChildAt(i))
                            if (found != null) return found
                        }
                    }
                    return null
                }

                val rv = findFirstRecyclerView(rootView.rootView ?: rootView) ?: return@post
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i) ?: continue
                    if (child.isClickable && child.height > dp(rootView.context, 36)) {
                        child.performClick()
                        break
                    }
                }
            }
        }
    }

    private fun buildNativeSendToEmojiSelectBar(ctx: Context, hostRoot: ViewGroup): View {
        val outerContainer = LinearLayout(ctx).apply {
            tag = SEND_TO_BAR_TAG
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
            }
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(AndroidColor.parseColor("#E6141824"))
                setStroke(dp(ctx, 1), AndroidColor.parseColor("#555CE1E6"))
            }
            elevation = dp(ctx, 6).toFloat()
        }

        val statusLabel = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(AndroidColor.parseColor("#CCFFFFFF"))
            setPadding(dp(ctx, 4), 0, dp(ctx, 4), dp(ctx, 4))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val allFriends = AutoSendSnapToAll.loadAllMutualFriends(context)
        val batchSize = context.config.rexMods.shortcutFriendsPerList.get().coerceIn(5, 200)
        val buckets = buildShortcutBuckets(context, allFriends, batchSize)

        fun refreshSendToBarUI() {
            chipRow.removeAllViews()
            val activeCount = activeSendToSelectedFriends.size - activeSendToUnselectedUserIds.size
            val groupCount = activeSendToSelectedGroups.size

            statusLabel.text = if (activeCount > 0 || groupCount > 0) {
                "✅ $activeCount Friends${if (groupCount > 0) " + $groupCount Groups" else ""} Selected (${activeSendToUnselectedUserIds.size} unselected) • Tap Send below!"
            } else {
                "RexMods Send-To Shortcuts • Tap ✅ All or 1️⃣ 2️⃣ 👥 👶 to auto-select"
            }

            fun makeChip(label: String, isSelected: Boolean, accentHex: String = "#5CE1E6", onClick: () -> Unit): TextView {
                return TextView(ctx).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setTextColor(AndroidColor.WHITE)
                    setPadding(dp(ctx, 11), dp(ctx, 6), dp(ctx, 11), dp(ctx, 6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        if (isSelected) {
                            setColor(AndroidColor.parseColor("#445CE1E6"))
                            setStroke(dp(ctx, 1), AndroidColor.parseColor(accentHex))
                        } else {
                            setColor(AndroidColor.parseColor("#1FFFFFFF"))
                            setStroke(dp(ctx, 1), AndroidColor.parseColor("#33FFFFFF"))
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(ctx, 6)
                    }
                    setOnClickListener {
                        ctx.vibrateLongPress()
                        onClick()
                    }
                }
            }

            // 1. ✅ Select All Button
            val isAllSelected = "all_friends" in activeSendToBucketIds
            chipRow.addView(
                makeChip("✅ All (${allFriends.size})", isAllSelected) {
                    if (isAllSelected) {
                        clearPendingSendToSelection()
                        logRex(context, "Cleared Select All on Send-To bar")
                    } else {
                        activeSendToBucketIds.add("all_friends")
                        buckets.filter { !it.isStory && !it.isGroup }.forEach { activeSendToBucketIds.add(it.id) }
                        allFriends.forEach { f ->
                            val uid = f.userId ?: return@forEach
                            activeSendToSelectedFriends[uid] = f
                        }
                        activeSendToTimestamp = System.currentTimeMillis()
                        ensureNativeSendToRowClicked(hostRoot)
                        logRex(context, "Selected ALL ${allFriends.size} friends on Send-To bar")
                        context.inAppOverlay.showStatusToast(
                            icon = Icons.Default.CheckCircle,
                            text = "Selected all ${allFriends.size} friends! Tap any person to unselect, then hit Send."
                        )
                    }
                    refreshSendToBarUI()
                }
            )

            // 2. Unselect / Edit Selected Friends Pill (shown when any friends are selected)
            if (activeSendToSelectedFriends.isNotEmpty()) {
                chipRow.addView(
                    makeChip("👤 Unselect ($activeCount)", true, "#FFD166") {
                        showDirectUnselectFriendsModal()
                    }
                )
                chipRow.addView(
                    makeChip("❌ Clear", false, "#FF6B6B") {
                        clearPendingSendToSelection()
                        refreshSendToBarUI()
                    }
                )
            }

            // 3. Emoji Shortcut Buckets (🟣, 👥, 👶, 1️⃣, 2️⃣, 3️⃣...)
            buckets.forEach { bucket ->
                if (bucket.isStory) return@forEach // Stories use Snapchat's native My Story row
                val isSelected = bucket.id in activeSendToBucketIds
                val countBadge = if (bucket.isGroup) bucket.groupConversationIds.size else bucket.friends.size
                chipRow.addView(
                    makeChip("${bucket.emoji} $countBadge", isSelected) {
                        if (isSelected) {
                            activeSendToBucketIds.remove(bucket.id)
                            activeSendToBucketIds.remove("all_friends")
                            if (bucket.isGroup) {
                                activeSendToSelectedGroups.removeAll(bucket.groupConversationIds.toSet())
                            } else {
                                bucket.friends.forEach { f ->
                                    f.userId?.let { activeSendToSelectedFriends.remove(it) }
                                }
                            }
                            logRex(context, "Deselected shortcut ${bucket.emoji} ${bucket.title} on Send-To bar")
                        } else {
                            activeSendToBucketIds.add(bucket.id)
                            if (bucket.isGroup) {
                                activeSendToSelectedGroups.addAll(bucket.groupConversationIds)
                            } else {
                                bucket.friends.forEach { f ->
                                    val uid = f.userId ?: return@forEach
                                    activeSendToSelectedFriends[uid] = f
                                    activeSendToUnselectedUserIds.remove(uid)
                                }
                            }
                            activeSendToTimestamp = System.currentTimeMillis()
                            ensureNativeSendToRowClicked(hostRoot)
                            logRex(context, "Selected shortcut ${bucket.emoji} ${bucket.title} ($countBadge) on Send-To bar")
                        }
                        refreshSendToBarUI()
                    }
                )
            }
        }

        refreshSendToBarUI()
        scroll.addView(chipRow)
        outerContainer.addView(statusLabel)
        outerContainer.addView(scroll)
        return outerContainer
    }

    private fun showDirectUnselectFriendsModal() {
        val mainActivity = context.mainActivity ?: return
        mainActivity.runOnUiThread {
            createComposeAlertDialog(mainActivity) { alertDialog ->
                PurrfectOverlayTheme {
                    val skin = LocalPurrfectSkin.current
                    var searchQuery by remember { mutableStateOf("") }
                    var unselectedState by remember { mutableStateOf(activeSendToUnselectedUserIds.toSet()) }
                    val selectedList = remember { activeSendToSelectedFriends.values.toList() }

                    val filtered = remember(selectedList, searchQuery) {
                        if (searchQuery.isBlank()) selectedList
                        else selectedList.filter {
                            (it.displayName ?: "").contains(searchQuery, ignoreCase = true) ||
                                (it.mutableUsername ?: "").contains(searchQuery, ignoreCase = true)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        color = skin.cardOverlayColor,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, skin.glowPrimary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Unselect Friends for This Snap",
                                        color = skin.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${selectedList.size - unselectedState.size} of ${selectedList.size} friends will receive this Snap",
                                        color = skin.glowPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                                Button(
                                    onClick = { alertDialog.dismiss() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = skin.glowPrimary,
                                        contentColor = skin.primaryButtonText
                                    )
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Search friend to unselect...") }
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filtered, key = { it.userId ?: it.hashCode() }) { friend ->
                                    val friendUid = friend.userId ?: ""
                                    val isReceiving = friendUid !in unselectedState
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                context.androidContext.vibrateLongPress()
                                                if (friendUid.isNotEmpty()) {
                                                    if (isReceiving) {
                                                        activeSendToUnselectedUserIds.add(friendUid)
                                                    } else {
                                                        activeSendToUnselectedUserIds.remove(friendUid)
                                                    }
                                                    unselectedState = activeSendToUnselectedUserIds.toSet()
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isReceiving) skin.glowPrimary.copy(alpha = 0.14f) else skin.textPrimary.copy(alpha = 0.04f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = friend.displayName?.takeIf { it.isNotBlank() }
                                                        ?: friend.mutableUsername ?: "Friend",
                                                    color = skin.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "@${friend.mutableUsername ?: "unknown"}",
                                                    color = skin.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Checkbox(
                                                checked = isReceiving,
                                                onCheckedChange = { checked ->
                                                    if (friendUid.isNotEmpty()) {
                                                        if (!checked) {
                                                            activeSendToUnselectedUserIds.add(friendUid)
                                                        } else {
                                                            activeSendToUnselectedUserIds.remove(friendUid)
                                                        }
                                                        unselectedState = activeSendToUnselectedUserIds.toSet()
                                                    }
                                                }
                                            )
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
}

/**
 * Reusable Compose Static Emoji Header Bar
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
    val batchSize = context.config.rexMods.shortcutFriendsPerList.get().coerceIn(5, 200)

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
                        .testTag("shortcut_bucket_${bucket.id}")
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
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (bucket.isStory) "Story" else if (bucket.isGroup) "${bucket.groupConversationIds.size}" else "${bucket.friends.size}",
                            color = if (isSelected) skin.glowPrimary else skin.textSecondary.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
