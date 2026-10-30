package me.eternal.purrfect.core.features.impl.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.data.ContentType
import me.eternal.purrfect.common.data.FriendLinkType
import me.eternal.purrfect.common.database.impl.FriendInfo
import me.eternal.purrfect.common.ui.createComposeAlertDialog
import me.eternal.purrfect.common.util.protobuf.ProtoReader
import me.eternal.purrfect.core.ModContext
import me.eternal.purrfect.core.event.events.impl.SendMessageWithContentEvent
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.core.ui.PurrfectOverlayTheme
import me.eternal.purrfect.core.util.CallbackBuilder
import me.eternal.purrfect.core.util.ktx.getObjectFieldOrNull
import me.eternal.purrfect.core.util.ktx.vibrateLongPress
import me.eternal.purrfect.core.wrapper.impl.MessageContent
import me.eternal.purrfect.core.wrapper.impl.MessageDestinations
import me.eternal.purrfect.core.wrapper.impl.SnapUUID
import me.eternal.purrfect.core.wrapper.impl.toSnapUUID
import me.eternal.purrfect.mapper.impl.CallbackMapper
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AutoSendSnapToAll : Feature("Auto Send Snap To All") {

    companion object {
        private const val PREFS_NAME = "purrfect_send_to_all_prefs"
        private const val KEY_EXEMPT_USER_IDS = "exempt_user_ids"
        private const val NOTIFICATION_CHANNEL_ID = "auto_send_to_all_status"
        private const val NOTIFICATION_ID = 98421

        val SYSTEM_EXCLUDED_USER_IDS = setOf(
            "b42f1f70-5a8b-4c53-8c25-34e7ec9e6781", // My AI
            "84ee8839-3911-492d-8b94-72dd80f3713a"  // Team Snapchat
        )

        val isInternalBatchSend: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
        private val handledEventInstances: MutableSet<Any> = Collections.newSetFromMap(ConcurrentHashMap<Any, Boolean>())

        fun markEventHandled(event: SendMessageWithContentEvent) {
            handledEventInstances.add(event)
        }

        fun consumeEventHandled(event: SendMessageWithContentEvent): Boolean {
            return handledEventInstances.remove(event)
        }

        fun loadExemptUserIds(context: ModContext): Set<String> {
            return runCatching {
                val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getStringSet(KEY_EXEMPT_USER_IDS, emptySet())?.toSet() ?: emptySet()
            }.getOrDefault(emptySet())
        }

        fun saveExemptUserIds(context: ModContext, exemptIds: Set<String>) {
            runCatching {
                val prefs = context.androidContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putStringSet(KEY_EXEMPT_USER_IDS, exemptIds.toSet()).apply()
            }
        }

        fun loadAllMutualFriends(context: ModContext): List<FriendInfo> {
            val myUserId = runCatching { context.database.myUserId }.getOrNull()
            return runCatching {
                context.database.getAllFriends()
                    .filter { friend ->
                        friend.friendLinkType == FriendLinkType.MUTUAL.value &&
                            friend.addedTimestamp > 0 &&
                            friend.userId != myUserId &&
                            friend.userId !in SYSTEM_EXCLUDED_USER_IDS
                    }
                    .sortedWith(
                        compareBy<FriendInfo> {
                            (it.displayName?.takeIf { name -> name.isNotBlank() } ?: it.mutableUsername ?: "").lowercase()
                        }.thenBy { (it.mutableUsername ?: "").lowercase() }
                    )
            }.getOrDefault(emptyList())
        }

        fun filterFriendsByMode(friends: List<FriendInfo>, targetFilter: String): List<FriendInfo> {
            return when (targetFilter) {
                "streaks_only" -> friends.filter { it.streakLength > 0 }
                else -> friends
            }
        }

        fun cloneAndScrubMessageContent(context: ModContext, originalMessageJson: String): MessageContent {
            return MessageContent(
                context.gson.fromJson(originalMessageJson, context.classCache.localMessageContent)
            ).also { messageContent ->
                val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

                fun shouldScrubField(fieldName: String): Boolean {
                    if (fieldName == "mId") return false
                    return fieldName in setOf("mMessageId", "mQuotedMessageId") ||
                        fieldName.contains("AttemptId", ignoreCase = true) ||
                        fieldName.contains("ClientMessageId", ignoreCase = true) ||
                        fieldName.contains("ClientId", ignoreCase = true) ||
                        fieldName.contains("MessageUuid", ignoreCase = true) ||
                        fieldName.contains("UUID", ignoreCase = true)
                }

                fun scrubValue(value: Any?) {
                    if (value == null) return
                    if (!visited.add(value)) return

                    when (value) {
                        is String, is Number, is Boolean, is ByteArray, is Enum<*> -> return
                        is Iterable<*> -> {
                            value.forEach { scrubValue(it) }
                            return
                        }
                        is Map<*, *> -> {
                            value.values.forEach { scrubValue(it) }
                            return
                        }
                    }

                    sequence<Class<*>> {
                        var current: Class<*>? = value.javaClass
                        while (current != null && current != Any::class.java && current != Object::class.java) {
                            yield(current)
                            current = current.superclass
                        }
                    }.flatMap { it.declaredFields.asSequence() }
                        .forEach { field ->
                            runCatching {
                                field.isAccessible = true
                                if (shouldScrubField(field.name)) {
                                    when (field.type) {
                                        java.lang.Long.TYPE -> field.setLong(value, 0L)
                                        java.lang.Integer.TYPE -> field.setInt(value, 0)
                                        java.lang.Boolean.TYPE -> field.setBoolean(value, false)
                                        else -> field.set(value, null)
                                    }
                                } else {
                                    scrubValue(field.get(value))
                                }
                            }
                        }
                }

                scrubValue(messageContent.instanceNonNull())
            }
        }

        private suspend fun resolveTargetConversationIds(
            context: ModContext,
            targetFriends: List<FriendInfo>,
            initialConversationIds: List<String>,
            exemptUserIds: Set<String>
        ): List<String> {
            val resolvedIds = LinkedHashSet<String>()

            // Keep any originally selected group conversations or non-exempted DM conversations
            initialConversationIds.forEach { convId ->
                val dmParticipant = runCatching { context.database.getDMOtherParticipant(convId) }.getOrNull()
                if (dmParticipant == null || dmParticipant !in exemptUserIds) {
                    resolvedIds.add(convId)
                }
            }

            val missingUserIds = mutableListOf<String>()
            targetFriends.forEach { friend ->
                val uid = friend.userId ?: return@forEach
                if (uid in exemptUserIds) return@forEach
                val cachedConvId = runCatching { context.database.getDMConversationId(uid) }.getOrNull()
                if (!cachedConvId.isNullOrBlank()) {
                    resolvedIds.add(cachedConvId)
                } else {
                    missingUserIds.add(uid)
                }
            }

            if (missingUserIds.isNotEmpty()) {
                val convManager = context.feature(Messaging::class).conversationManager
                if (convManager != null) {
                    missingUserIds.chunked(200).forEach { chunk ->
                        val fetchedIds = suspendCoroutine<List<String>> { continuation ->
                            runCatching {
                                convManager.getOneOnOneConversationIds(
                                    chunk,
                                    onError = {
                                        continuation.resume(emptyList())
                                    },
                                    onSuccess = { pairs ->
                                        continuation.resume(pairs.map { it.second })
                                    }
                                )
                            }.onFailure {
                                continuation.resume(emptyList())
                            }
                        }
                        resolvedIds.addAll(fetchedIds)
                    }
                }
            }

            return resolvedIds.toList()
        }

        private fun updateBatchNotification(
            context: ModContext,
            title: String,
            content: String,
            progressCurrent: Int? = null,
            progressMax: Int? = null,
            ongoing: Boolean = false
        ) {
            runCatching {
                val notificationManager =
                    context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Auto Send Snap to All",
                        NotificationManager.IMPORTANCE_LOW
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                val builder = NotificationCompat.Builder(context.androidContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setOnlyAlertOnce(true)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)

                if (progressCurrent != null && progressMax != null && progressMax > 0) {
                    builder.setProgress(progressMax, progressCurrent, false)
                    builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                }

                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        }

        fun executeAutoBatchSend(
            context: ModContext,
            event: SendMessageWithContentEvent,
            targetFriends: List<FriendInfo>,
            exemptUserIds: Set<String>,
            messageContentFactory: () -> MessageContent
        ) {
            val batchSize = context.config.messaging.sendSnapToAll.batchSize.get().coerceIn(1, 200)
            val batchDelayMs = context.config.messaging.sendSnapToAll.batchDelayMs.get().coerceIn(0, 60000).toLong()
            val initialConversations = event.destinations.conversations?.map { it.toString() } ?: emptyList()
            val originalCallback = event.adapter.args().getOrNull(2)

            val sendMessageCallbackClass = runCatching {
                lateinit var result: Class<*>
                context.mappings.useMapper(CallbackMapper::class) {
                    result = callbacks.getClass("SendMessageCallback") ?: error("Failed to resolve SendMessageCallback")
                }
                result
            }.getOrNull()

            val sendMessageWithContentMethod = runCatching {
                sequence {
                    var current: Class<*>? = context.classCache.conversationManager
                    while (current != null && current != Any::class.java && current != Object::class.java) {
                        yield(current)
                        current = current.superclass
                    }
                }.flatMap { it.declaredMethods.asSequence() }
                    .first { it.name == "sendMessageWithContent" }
            }.getOrNull()

            val conversationManagerInstance = context.feature(Messaging::class).conversationManager?.instanceNonNull()

            if (sendMessageCallbackClass == null || sendMessageWithContentMethod == null || conversationManagerInstance == null) {
                context.inAppOverlay.showStatusToast(
                    icon = Icons.Default.WarningAmber,
                    text = "Unable to initialize batch sender"
                )
                return
            }

            context.coroutineScope.launch {
                val allConversationIds = resolveTargetConversationIds(
                    context = context,
                    targetFriends = targetFriends,
                    initialConversationIds = initialConversations,
                    exemptUserIds = exemptUserIds
                )

                if (allConversationIds.isEmpty()) {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Default.WarningAmber,
                        text = context.translation.getOrNull("send_to_all_dialog.no_recipients_toast")
                            ?: "No eligible recipients after exemptions"
                    )
                    return@launch
                }

                val batches = allConversationIds.chunked(batchSize)
                val totalRecipients = allConversationIds.size
                val totalBatches = batches.size

                context.inAppOverlay.showStatusToast(
                    icon = Icons.Default.Send,
                    text = "Sending snap to $totalRecipients recipients in $totalBatches batch(es) of up to $batchSize..."
                )

                var sentRecipients = 0
                var failedBatches = 0

                for ((index, batch) in batches.withIndex()) {
                    val batchNumber = index + 1
                    updateBatchNotification(
                        context = context,
                        title = "Auto Sending Snap (Batch $batchNumber/$totalBatches)",
                        content = "Sending ${batch.size} recipients ($sentRecipients/$totalRecipients completed) • Limit $batchSize/batch",
                        progressCurrent = sentRecipients,
                        progressMax = totalRecipients,
                        ongoing = true
                    )

                    val clonedDestinationsObj = context.gson.fromJson(
                        context.gson.toJson(event.destinations.instanceNonNull()),
                        context.classCache.messageDestinations
                    )
                    val batchDestinations = MessageDestinations(clonedDestinationsObj).apply {
                        conversations = ArrayList(batch.map { it.toSnapUUID() })
                        if (index > 0) {
                            stories = arrayListOf<Any>()
                        }
                    }

                    val batchMessageContent = runCatching { messageContentFactory() }.getOrNull()
                    if (batchMessageContent == null) {
                        failedBatches++
                        continue
                    }

                    val batchSuccess = suspendCoroutine<Boolean> { continuation ->
                        val callback = CallbackBuilder(sendMessageCallbackClass)
                            .override("onSuccess") {
                                continuation.resume(true)
                            }
                            .override("onError") {
                                continuation.resume(false)
                            }
                            .build()

                        runCatching {
                            isInternalBatchSend.set(true)
                            try {
                                sendMessageWithContentMethod.invoke(
                                    conversationManagerInstance,
                                    batchDestinations.instanceNonNull(),
                                    batchMessageContent.instanceNonNull(),
                                    callback
                                )
                            } finally {
                                isInternalBatchSend.set(false)
                            }
                        }.onFailure {
                            continuation.resume(false)
                        }
                    }

                    if (batchSuccess) {
                        sentRecipients += batch.size
                    } else {
                        failedBatches++
                    }

                    if (index < batches.lastIndex && batchDelayMs > 0) {
                        delay(batchDelayMs)
                    }
                }

                if (sentRecipients > 0) {
                    runCatching {
                        originalCallback?.javaClass?.methods?.firstOrNull { method ->
                            method.name == "onSuccess" && method.parameterCount == 0
                        }?.invoke(originalCallback)
                    }
                } else {
                    runCatching {
                        originalCallback?.javaClass?.methods?.firstOrNull { method ->
                            method.name == "onError" && method.parameterCount == 1
                        }?.invoke(originalCallback, null)
                    }
                }

                val summaryText = if (failedBatches == 0) {
                    "Sent snap to all $sentRecipients friends across $totalBatches batch(es)!"
                } else {
                    "Sent to $sentRecipients/$totalRecipients friends ($failedBatches batch(es) failed)"
                }

                updateBatchNotification(
                    context = context,
                    title = "Auto Send to All Complete",
                    content = summaryText,
                    ongoing = false
                )

                context.inAppOverlay.showStatusToast(
                    icon = if (failedBatches == 0) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    text = summaryText
                )
            }
        }

        fun shouldTriggerPrompt(context: ModContext, event: SendMessageWithContentEvent): Boolean {
            val config = context.config.messaging.sendSnapToAll
            if (config.globalState != true) return false
            if (isInternalBatchSend.get() == true) return false
            if (event.destinations.stories?.isNotEmpty() == true && event.destinations.conversations?.isEmpty() == true) {
                return false
            }

            val localMessageContent = event.messageContent
            val contentType = localMessageContent.contentType
            val isExternalMedia = contentType == ContentType.EXTERNAL_MEDIA ||
                localMessageContent.instanceNonNull().getObjectFieldOrNull("mExternalContentMetadata") != null
            val isCameraSnap = contentType == ContentType.SNAP && !isExternalMedia

            if (!isCameraSnap && !isExternalMedia) return false
            if (isCameraSnap && !config.includeCameraSnaps.get()) return false
            if (isExternalMedia && !config.includeGalleryMedia.get()) return false

            val protoReader = ProtoReader(localMessageContent.content ?: return false)
            if (protoReader.contains(7)) return false // skip story replies

            val selectedConversations = event.destinations.conversations?.map { it.toString() } ?: return false
            if (selectedConversations.isEmpty()) return false

            val triggerMode = config.triggerMode.get()
            val limitThreshold = config.limitThreshold.get().coerceIn(1, 200)
            if (triggerMode == "when_limit_reached" && selectedConversations.size < limitThreshold) {
                return false
            }
            return context.mainActivity != null
        }
    }

    override fun init() {
        val config = context.config.messaging.sendSnapToAll
        if (config.globalState != true) return

        // Subscribe at priority -50 so SendOverride (-100) runs first in ascending TreeMap order
        context.event.subscribe(SendMessageWithContentEvent::class, priority = -50) { event ->
            if (isInternalBatchSend.get() == true) return@subscribe
            if (consumeEventHandled(event)) return@subscribe
            if (!shouldTriggerPrompt(context, event)) return@subscribe

            val selectedConversations = event.destinations.conversations?.map { it.toString() } ?: return@subscribe
            val limitThreshold = config.limitThreshold.get().coerceIn(1, 200)
            val mainActivity = context.mainActivity ?: return@subscribe

            event.canceled = true
            event.adapter.setResult(null)

            val originalMessageJson = context.gson.toJson(event.messageContent.instanceNonNull())

            fun invokeNormalSend() {
                val conversationManagerInstance =
                    context.feature(Messaging::class).conversationManager?.instanceNonNull() ?: return
                val sendMethod = sequence {
                    var current: Class<*>? = context.classCache.conversationManager
                    while (current != null && current != Any::class.java && current != Object::class.java) {
                        yield(current)
                        current = current.superclass
                    }
                }.flatMap { it.declaredMethods.asSequence() }
                    .firstOrNull { it.name == "sendMessageWithContent" } ?: return

                val clonedDestinations = context.gson.fromJson(
                    context.gson.toJson(event.destinations.instanceNonNull()),
                    context.classCache.messageDestinations
                )
                val clonedContent = cloneAndScrubMessageContent(context, originalMessageJson)
                val originalCallback = event.adapter.args().getOrNull(2)

                isInternalBatchSend.set(true)
                try {
                    sendMethod.invoke(
                        conversationManagerInstance,
                        clonedDestinations,
                        clonedContent.instanceNonNull(),
                        originalCallback
                    )
                } finally {
                    isInternalBatchSend.set(false)
                }
            }

            mainActivity.runOnUiThread {
                showSendToAllPromptDialog(
                    context = context,
                    selectedCount = selectedConversations.size,
                    limitThreshold = limitThreshold,
                    onSendNormal = {
                        invokeNormalSend()
                    },
                    onSendToAll = { targetFriends, exemptUserIds ->
                        executeAutoBatchSend(
                            context = context,
                            event = event,
                            targetFriends = targetFriends,
                            exemptUserIds = exemptUserIds,
                            messageContentFactory = {
                                cloneAndScrubMessageContent(context, originalMessageJson)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SendToAllAndExemptSection(
    context: ModContext,
    allFriends: List<FriendInfo>,
    targetFilter: String,
    onTargetFilterChange: (String) -> Unit,
    exemptUserIds: Set<String>,
    onExemptUserIdsChange: (Set<String>) -> Unit,
    batchSize: Int
) {
    val skin = LocalPurrfectSkin.current
    var showExemptList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyExempted by remember { mutableStateOf(false) }

    val eligibleFriends = remember(allFriends, targetFilter) {
        AutoSendSnapToAll.filterFriendsByMode(allFriends, targetFilter)
    }
    val activeRecipientsCount = remember(eligibleFriends, exemptUserIds) {
        eligibleFriends.count { it.userId !in exemptUserIds }
    }
    val batchCount = remember(activeRecipientsCount, batchSize) {
        if (activeRecipientsCount <= 0) 0 else ((activeRecipientsCount + batchSize - 1) / batchSize)
    }

    val filteredFriendsForPicker = remember(eligibleFriends, searchQuery, showOnlyExempted, exemptUserIds) {
        eligibleFriends.filter { friend ->
            val matchesSearch = searchQuery.isBlank() ||
                (friend.displayName?.contains(searchQuery, ignoreCase = true) == true) ||
                (friend.mutableUsername?.contains(searchQuery, ignoreCase = true) == true)
            val matchesExempt = !showOnlyExempted || (friend.userId in exemptUserIds)
            matchesSearch && matchesExempt
        }
    }

    val borderColor = skin.textPrimary.copy(alpha = 0.14f)
    var selectedBucketIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(skin.textPrimary.copy(alpha = 0.05f))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (context.config.messaging.staticEmojiShortcutHeader.get()) {
            StaticEmojiShortcutHeader(
                context = context,
                allFriends = allFriends,
                selectedBucketIds = selectedBucketIds,
                onBucketToggled = { bucket, isSelected ->
                    val updatedBuckets = if (isSelected) {
                        selectedBucketIds + bucket.id
                    } else {
                        selectedBucketIds - bucket.id
                    }
                    selectedBucketIds = updatedBuckets

                    if (bucket.isStory) {
                        context.inAppOverlay.showStatusToast(
                            icon = Icons.Default.CheckCircle,
                            text = if (isSelected) "🟣 Story recipient enabled" else "🟣 Story recipient disabled"
                        )
                        return@StaticEmojiShortcutHeader
                    }

                    if (bucket.isGroup) {
                        context.inAppOverlay.showStatusToast(
                            icon = Icons.Default.Groups,
                            text = if (isSelected) "👥 Selected ${bucket.groupConversationIds.size} Groups" else "👥 Groups deselected"
                        )
                        return@StaticEmojiShortcutHeader
                    }

                    if (updatedBuckets.isEmpty()) {
                        val saved = AutoSendSnapToAll.loadExemptUserIds(context)
                        onExemptUserIdsChange(saved)
                    } else {
                        val bucketsList = AutoFriendShortcuts.buildShortcutBuckets(
                            context,
                            allFriends,
                            context.config.messaging.shortcutFriendsPerList.get().coerceIn(5, 200)
                        )
                        val targetUserIds = bucketsList
                            .filter { it.id in updatedBuckets }
                            .flatMap { it.friends }
                            .mapNotNull { it.userId }
                            .toSet()

                        val newExempt = allFriends.mapNotNull { it.userId }.filter { it !in targetUserIds }.toSet()
                        onExemptUserIdsChange(newExempt)
                        context.inAppOverlay.showStatusToast(
                            icon = Icons.Default.CheckCircle,
                            text = "${bucket.emoji} ${bucket.title} (${bucket.friends.size} Friends)"
                        )
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recipients: $activeRecipientsCount / ${eligibleFriends.size} Friends",
                    color = skin.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Auto-sends in $batchCount batch(es) of up to $batchSize • ${exemptUserIds.size} exempted",
                    color = skin.textSecondary,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(skin.glowPrimary.copy(alpha = 0.14f))
                    .border(1.dp, skin.glowPrimary.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "200 Bypass",
                    color = skin.glowPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        // Target filter chips: All Friends vs Streaks Only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "all_friends" to "All Friends (${allFriends.size})",
                "streaks_only" to "Streaks Only (${allFriends.count { it.streakLength > 0 }})"
            )
            filters.forEach { (key, label) ->
                val selected = targetFilter == key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            context.androidContext.vibrateLongPress()
                            onTargetFilterChange(key)
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) skin.glowPrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(
                        1.dp,
                        if (selected) skin.glowPrimary else borderColor
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) skin.textPrimary else skin.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Exempt Friends Toggle Button
        OutlinedButton(
            onClick = {
                context.androidContext.vibrateLongPress()
                showExemptList = !showExemptList
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Icon(
                imageVector = if (showExemptList) Icons.Default.ExpandLess else Icons.Default.PersonOff,
                contentDescription = "Manage Exempt Friends",
                tint = skin.glowPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showExemptList) {
                    "Hide Exempt Friends (${exemptUserIds.size} Exempted)"
                } else {
                    "Exempt Friends (${exemptUserIds.size} Exempted)"
                },
                color = skin.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        AnimatedVisibility(visible = showExemptList) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Search friends to exempt...",
                            fontSize = 12.sp,
                            color = skin.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = skin.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = skin.textSecondary
                                )
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showOnlyExempted = !showOnlyExempted }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showOnlyExempted,
                            onCheckedChange = { showOnlyExempted = it }
                        )
                        Text(
                            text = "Exempted only",
                            color = skin.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                val updated = exemptUserIds.toMutableSet().apply {
                                    filteredFriendsForPicker.forEach { friend ->
                                        friend.userId?.let { add(it) }
                                    }
                                }
                                onExemptUserIdsChange(updated)
                                AutoSendSnapToAll.saveExemptUserIds(context, updated)
                            }
                        ) {
                            Text("Exempt All", fontSize = 11.sp, color = skin.glowPrimary)
                        }
                        TextButton(
                            onClick = {
                                val updated = emptySet<String>()
                                onExemptUserIdsChange(updated)
                                AutoSendSnapToAll.saveExemptUserIds(context, updated)
                            }
                        ) {
                            Text("Clear", fontSize = 11.sp, color = skin.textSecondary)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (filteredFriendsForPicker.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching friends found",
                                    color = skin.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(filteredFriendsForPicker, key = { it.userId ?: it.hashCode().toString() }) { friend ->
                            val friendUserId = friend.userId ?: return@items
                            val isExempted = friendUserId in exemptUserIds
                            val displayName = friend.displayName?.takeIf { it.isNotBlank() }
                                ?: friend.mutableUsername
                                ?: friendUserId
                            val username = friend.mutableUsername ?: ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = exemptUserIds.toMutableSet().apply {
                                            if (isExempted) remove(friendUserId) else add(friendUserId)
                                        }
                                        onExemptUserIdsChange(updated)
                                        AutoSendSnapToAll.saveExemptUserIds(context, updated)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            color = if (isExempted) skin.textSecondary else skin.textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (friend.streakLength > 0) {
                                            Text(
                                                text = "\uD83D\uDD25 ${friend.streakLength}",
                                                fontSize = 11.sp,
                                                color = skin.glowPrimary
                                            )
                                        }
                                    }
                                    if (username.isNotBlank() && username != displayName) {
                                        Text(
                                            text = "@$username",
                                            color = skin.textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isExempted) "EXEMPT" else "SEND",
                                        color = if (isExempted) Color(0xFFFF6B6B) else skin.glowPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Checkbox(
                                        checked = isExempted,
                                        onCheckedChange = { checked ->
                                            val updated = exemptUserIds.toMutableSet().apply {
                                                if (checked) add(friendUserId) else remove(friendUserId)
                                            }
                                            onExemptUserIdsChange(updated)
                                            AutoSendSnapToAll.saveExemptUserIds(context, updated)
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

fun showSendToAllPromptDialog(
    context: ModContext,
    selectedCount: Int,
    limitThreshold: Int,
    onSendNormal: () -> Unit,
    onSendToAll: (targetFriends: List<FriendInfo>, exemptUserIds: Set<String>) -> Unit
) {
    val mainActivity = context.mainActivity ?: return
    val config = context.config.messaging.sendSnapToAll
    val batchSize = config.batchSize.get().coerceIn(1, 200)

    createComposeAlertDialog(mainActivity) { alertDialog ->
        PurrfectOverlayTheme {
            val skin = LocalPurrfectSkin.current
            val allFriends = remember { AutoSendSnapToAll.loadAllMutualFriends(context) }
            var targetFilter by remember { mutableStateOf(config.defaultTargetFilter.get()) }
            var exemptUserIds by remember { mutableStateOf(AutoSendSnapToAll.loadExemptUserIds(context)) }

            val eligibleFriends = remember(allFriends, targetFilter) {
                AutoSendSnapToAll.filterFriendsByMode(allFriends, targetFilter)
            }
            val activeRecipientsCount = remember(eligibleFriends, exemptUserIds) {
                eligibleFriends.count { it.userId !in exemptUserIds }
            }

            val borderBrush = remember(skin.glowPrimary, skin.glowSecondary) {
                Brush.linearGradient(
                    listOf(
                        skin.glowPrimary.copy(alpha = 0.55f),
                        skin.glowSecondary.copy(alpha = 0.35f)
                    )
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp)),
                color = skin.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.2.dp, borderBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            skin.glowPrimary.copy(alpha = 0.36f),
                                            skin.glowSecondary.copy(alpha = 0.30f)
                                        )
                                    )
                                )
                                .border(1.dp, skin.textPrimary.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Send Snap to All",
                                tint = skin.textPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send Snap to All Friends",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = skin.textPrimary
                            )
                            Text(
                                text = if (selectedCount >= limitThreshold) {
                                    "200 recipient limit reached ($selectedCount selected) • Auto-send to all & exempt"
                                } else {
                                    "$selectedCount selected • Send to all friends automatically or send normally"
                                },
                                fontSize = 12.sp,
                                color = skin.textSecondary
                            )
                        }
                    }

                    SendToAllAndExemptSection(
                        context = context,
                        allFriends = allFriends,
                        targetFilter = targetFilter,
                        onTargetFilterChange = { targetFilter = it },
                        exemptUserIds = exemptUserIds,
                        onExemptUserIdsChange = { exemptUserIds = it },
                        batchSize = batchSize
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                alertDialog.dismiss()
                            },
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text(
                                text = context.translation["button.cancel"],
                                maxLines = 1,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                context.androidContext.vibrateLongPress()
                                alertDialog.dismiss()
                                onSendNormal()
                            },
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = "Send ($selectedCount)",
                                maxLines = 1,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                context.androidContext.vibrateLongPress()
                                alertDialog.dismiss()
                                onSendToAll(eligibleFriends, exemptUserIds)
                            },
                            enabled = activeRecipientsCount > 0,
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = skin.glowPrimary,
                                contentColor = skin.primaryButtonText
                            )
                        ) {
                            Text(
                                text = "Send All ($activeRecipientsCount)",
                                color = skin.primaryButtonText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }.show()
}
