package me.eternal.purrfect.ui.manager.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.ui.AsyncUpdateDispatcher
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.storage.addRepo
import me.eternal.purrfect.storage.getRepositories
import me.eternal.purrfect.storage.removeRepo
import me.eternal.purrfect.ui.components.DefaultBottomSheet
import me.eternal.purrfect.ui.components.DismissibleCardItem
import me.eternal.purrfect.ui.manager.Routes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class ManageReposSection : Routes.Section() {
    private val updateDispatcher = AsyncUpdateDispatcher()
    private val okHttpClient by lazy { OkHttpClient() }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        val repositories = rememberAsyncMutableStateList(defaultValue = emptyList(), updateDispatcher = updateDispatcher) {
            context.database.getRepositories("script")
        }

        var showAddSheet by remember { mutableStateOf(false) }

        if (showAddSheet) {
            val clipboardManager = LocalClipboardManager.current
            var url by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                clipboardManager.getText()?.text?.let { text ->
                    if (text.startsWith("http")) url = text
                }
            }

            DefaultBottomSheet(
                onDismiss = { showAddSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = context.translation["manager.dialogs.add_repo.title"],
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { focusRequester.requestFocus() },
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(context.translation["manager.dialogs.add_repo.url_field_hint"]) },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = !loading && url.isNotBlank(),
                            onClick = {
                                loading = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    var modifiedUrl = url.trim().removeSuffix("/")
                                    if (modifiedUrl.startsWith("https://github.com/")) {
                                        val splitUrl = modifiedUrl.removePrefix("https://github.com/").split("/")
                                        if (splitUrl.size >= 2) {
                                            val repoName = splitUrl[0] + "/" + splitUrl[1]
                                            for (branch in listOf("main", "master", "dev")) {
                                                val rawIndex = "https://raw.githubusercontent.com/$repoName/$branch/index.json"
                                                runCatching {
                                                    okHttpClient.newCall(Request.Builder().url(rawIndex).build()).execute().use { resp ->
                                                        if (resp.isSuccessful) {
                                                            modifiedUrl = "https://raw.githubusercontent.com/$repoName/$branch"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val indexUri = "$modifiedUrl/index.json".toHttpUrlOrNull()
                                    if (indexUri == null) {
                                        context.shortToast(context.translation["manager.dialogs.add_repo.invalid_url_toast"])
                                        loading = false
                                        return@launch
                                    }
                                    runCatching {
                                        okHttpClient.newCall(Request.Builder().url(indexUri).build()).execute().use { response ->
                                            if (!response.isSuccessful) {
                                                context.shortToast("Failed to fetch index.json: ${response.code}")
                                                loading = false
                                                return@launch
                                            }
                                            context.database.addRepo("script", modifiedUrl)
                                            context.shortToast(context.translation["manager.dialogs.add_repo.repo_successfully_added_toast"])
                                            showAddSheet = false
                                            updateDispatcher.dispatch()
                                        }
                                    }.onFailure {
                                        context.shortToast("Error: ${it.message}")
                                    }
                                    loading = false
                                }
                            }
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(text = context.translation["manager.dialogs.add_repo.add_button"])
                            }
                        }
                    }
                }
            }
        }

        setFloatingActionButton {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(text = context.translation["manager.sections.manage_repos.add_repo_button"]) }
            )
        }

        if (repositories.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenContentPadding(hasFAB = true)),
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
                            imageVector = Icons.Rounded.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.translation["manager.sections.manage_repos.no_repos_hint"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(hasFAB = true),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(repositories, key = { _, url -> url }) { index, url ->
                    val shape = cardShape(repositories.size, index)
                    DismissibleCardItem(
                        shape = shape,
                        onDelete = {
                            coroutineScope.launch(Dispatchers.IO) {
                                context.database.removeRepo("script", url)
                                updateDispatcher.dispatch()
                            }
                        }
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Rounded.Public, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(
                                        text = url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
