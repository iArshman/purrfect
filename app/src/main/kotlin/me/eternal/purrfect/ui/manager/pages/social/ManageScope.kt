package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.data.FriendStreaks
import me.eternal.purrfect.common.data.MessagingFriendInfo
import me.eternal.purrfect.common.data.MessagingGroupInfo
import me.eternal.purrfect.common.data.MessagingRuleType
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.rememberAsyncMutableState
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.common.util.snap.BitmojiSelfie
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType
import me.eternal.purrfect.ui.util.coil.BitmojiImage

class ManageScope : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }

    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val scope = navBackStackEntry.arguments?.getString("scope")?.let { SocialScope.getByName(it) } ?: SocialScope.FRIEND
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        val coroutineScope = rememberCoroutineScope()
        var showDeleteConfirm by remember { mutableStateOf(false) }

        if (showDeleteConfirm) {
            bottomSheets.ConfirmBottomSheet(
                title = context.translation.format(
                    "manager.dialogs.delete_scope.title",
                    "scope" to context.translation["manager.sections.manage_scope.${scope.key}"]
                ),
                message = context.translation["manager.dialogs.delete_scope.body"],
                confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        when (scope) {
                            SocialScope.FRIEND -> context.database.deleteFriend(id)
                            SocialScope.GROUP -> context.database.deleteGroup(id)
                        }
                    }
                    showDeleteConfirm = false
                    routes.navController.popBackStack()
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        setTopBarActions {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
            }
        }

        val rules = remember { MessagingRuleType.entries.filter { it.listMode } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                when (scope) {
                    SocialScope.FRIEND -> {
                        val friend by rememberAsyncMutableState(null) { context.database.getFriendInfo(id) }
                        val streaks by rememberAsyncMutableState(null) { context.database.getFriendStreaks(id) }

                        friend?.let { f ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = cardShapeSingle
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BitmojiImage(
                                        context = context,
                                        url = BitmojiSelfie.getBitmojiSelfie(
                                            f.selfieId,
                                            f.bitmojiId,
                                            BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                                        ),
                                        size = 84
                                    )
                                    Text(
                                        text = f.displayName ?: f.mutableUsername,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = f.mutableUsername,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    streaks?.takeIf { !it.isExpired && it.length > 0 }?.let { s ->
                                        Text(
                                            text = "🔥 ${s.length} Streak",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                routes.loggedStories.navigate {
                                                    put("id", id)
                                                }
                                            }
                                        ) {
                                            Text(text = context.translation["manager.sections.manage_scope.logged_stories_button"])
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                routes.messagingPreview.navigate {
                                                    put("id", id)
                                                    put("scope", SocialScope.FRIEND.key)
                                                }
                                            }
                                        ) {
                                            Text(text = "Messages")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SocialScope.GROUP -> {
                        val group by rememberAsyncMutableState(null) { context.database.getGroupInfo(id) }
                        group?.let { g ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = cardShapeSingle
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = g.name,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${g.participantsCount} members",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            routes.messagingPreview.navigate {
                                                put("id", id)
                                                put("scope", SocialScope.GROUP.key)
                                            }
                                        }
                                    ) {
                                        Text(text = "Messages")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = context.translation["manager.sections.manage_scope.rules_title"],
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    rules.forEachIndexed { index, ruleType ->
                        val ruleState = context.config.root.rules.getRuleState(ruleType)
                        var enabled by remember(id, ruleType) {
                            mutableStateOf(context.database.getRuleIds(ruleType.key).contains(id))
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShape(rules.size, index)
                        ) {
                            ListItem(
                                modifier = Modifier.clickable(enabled = ruleState != null) {
                                    enabled = !enabled
                                    context.database.setRule(id, ruleType.key, enabled)
                                },
                                headlineContent = {
                                    Text(
                                        text = context.translation["rules.properties.${ruleType.key}.name"],
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (ruleState == null) context.translation["rules.toasts.disabled"]
                                        else context.translation["rules.modes.${ruleState.key}"],
                                        fontSize = 12.sp
                                    )
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = enabled,
                                        enabled = ruleState != null,
                                        onCheckedChange = {
                                            enabled = it
                                            context.database.setRule(id, ruleType.key, it)
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
}
