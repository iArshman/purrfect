package me.eternal.purrfect.ui.manager.pages.home

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.action.EnumAction
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.columnPadding
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.setup.Requirements
import me.eternal.purrfect.ui.setup.SetupActivity
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.BottomSheets
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType

class HomeSettings : Routes.Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    private val bottomSheets by lazy { BottomSheets(context.translation) }

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    private fun RowTitle(title: String) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    @Composable
    private fun NavigationCardItem(
        title: String,
        summary: String? = null,
        icon: ImageVector,
        index: Int,
        groupSize: Int,
        onClick: () -> Unit
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape(groupSize, index)
        ) {
            ListItem(
                modifier = Modifier.clickable(onClick = onClick),
                leadingContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(text = title, fontWeight = FontWeight.SemiBold)
                },
                supportingContent = summary?.let {
                    { Text(text = it, fontSize = 12.sp) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    NavigationCardItem(
                        title = "Appearance",
                        summary = "App theme, dark mode, AMOLED, and language",
                        icon = Icons.Rounded.Palette,
                        index = 0,
                        groupSize = 3,
                        onClick = { routes.homeAppearance.navigate() }
                    )
                    NavigationCardItem(
                        title = "Updating",
                        summary = "Auto-updater and update check preferences",
                        icon = Icons.Rounded.Update,
                        index = 1,
                        groupSize = 3,
                        onClick = { routes.homeUpdating.navigate() }
                    )
                    NavigationCardItem(
                        title = "About",
                        summary = "Version info, credits, and open source licenses",
                        icon = Icons.Rounded.Info,
                        index = 2,
                        groupSize = 3,
                        onClick = { routes.homeAbout.navigate() }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                RowTitle(title = context.translation["manager.sections.settings.actions_title"])
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val actions = EnumAction.entries
                    actions.forEachIndexed { index, action ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShape(actions.size, index)
                        ) {
                            ListItem(
                                modifier = Modifier.clickable {
                                    context.launchActionIntent(action)
                                },
                                leadingContent = {
                                    Icon(imageVector = action.icon, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(text = context.translation["actions.${action.key}.name"])
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                RowTitle(title = "Mappings & Cache")
            }

            item {
                var showClearCacheConfirm by remember { mutableStateOf(false) }

                if (showClearCacheConfirm) {
                    bottomSheets.ConfirmBottomSheet(
                        title = "Clear Cache",
                        message = "Are you sure you want to clear temporary app cache?",
                        confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                        onConfirm = {
                            coroutineScope.launch(Dispatchers.IO) {
                                context.androidContext.cacheDir.deleteRecursively()
                            }
                            context.shortToast("Cache cleared!")
                            showClearCacheConfirm = false
                        },
                        onDismiss = { showClearCacheConfirm = false }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(2, 0)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                context.activity?.startActivity(
                                    Intent(context.androidContext, SetupActivity::class.java).apply {
                                        putExtra("requirements", Requirements.MAPPINGS)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            },
                            headlineContent = {
                                Text(text = "Regenerate Mappings")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(2, 1)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                showClearCacheConfirm = true
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            },
                            headlineContent = {
                                Text(text = "Clear Cache")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
