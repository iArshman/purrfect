package me.eternal.purrfect.ui.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import me.eternal.purrfect.RemoteSideContext

class Navigation(
    private val context: RemoteSideContext,
    private val navHostController: NavHostController,
    val routes: Routes = Routes(context).also {
        it.navController = navHostController
    },
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopBar() {
        val navBackStackEntry by navHostController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        val canGoBack = remember(navBackStackEntry) {
            currentRoute != null && (!currentRoute.isPrimary || currentRoute.canGoBack())
        }

        TopAppBar(
            title = {
                currentRoute?.apply {
                    title()
                        ?: translatedKey?.let {
                            Text(
                                text = context.translation[it],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                }
            },
            navigationIcon = {
                val backButtonProgress by androidx.compose.animation.core.animateFloatAsState(
                    if (canGoBack) 1f else 0f,
                    label = "backButtonProgress"
                )

                if (canGoBack || backButtonProgress > 0f) {
                    IconButton(
                        onClick = {
                            if (canGoBack) {
                                navHostController.popBackStack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = { currentRoute?.topBarActions()?.invoke(this) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified
            )
        )
    }

    @Composable
    fun BottomBar() {
        val navBackStackEntry by navHostController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        val primaryRoutes = remember { routes.getRoutes().filter { it.isPrimary } }

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            primaryRoutes.forEach { route ->
                val selected = navBackStackEntry?.destination?.hierarchy?.any {
                    it.route == "main_" + route.routeInfo.id
                } == true

                NavigationBarItem(
                    alwaysShowLabel = true,
                    icon = {
                        Icon(
                            imageVector = if (selected) route.routeInfo.primaryIcon else route.routeInfo.secondaryIcon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            text = remember(context.translation.loadedLocale) {
                                route.translatedKey?.let { context.translation[it] } ?: ""
                            },
                            maxLines = 1,
                            fontSize = 11.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = selected,
                    onClick = {
                        if (currentRoute != route || route.canGoBack()) {
                            route.navigateReset()
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun Fab() {
        val navBackStackEntry by navHostController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        AnimatedVisibility(
            visible = remember(currentRoute) { currentRoute?.floatingActionButton != null },
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            remember { currentRoute }?.floatingActionButton?.invoke()
        }
    }

    @Composable
    fun NavContent(paddingValues: PaddingValues, startDestination: String) {
        Box(Modifier.padding(paddingValues)) {
            val navBackStackEntry by navHostController.currentBackStackEntryAsState()
            NavHost(
                navController = navHostController,
                startDestination = "main_$startDestination",
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) }
            ) {
                routes.getRoutes().filter { it.parentRoute == null }.forEach { route ->
                    val children = routes.getRoutes().filter { it.parentRoute == route }
                    if (children.isEmpty()) {
                        composable(
                            "main_" + route.routeInfo.id,
                            route.arguments
                        ) {
                            route.apply {
                                currentBackStackEntry = navBackStackEntry
                                content.invoke(it)
                            }
                        }
                    } else {
                        navigation(
                            startDestination = route.routeInfo.id,
                            route = "main_" + route.routeInfo.id
                        ) {
                            composable(route.routeInfo.id) {
                                route.apply {
                                    currentBackStackEntry = navBackStackEntry
                                    content.invoke(it)
                                }
                            }
                            children.forEach { child ->
                                composable(child.routeInfo.id, child.arguments) {
                                    child.apply {
                                        currentBackStackEntry = navBackStackEntry
                                        content.invoke(it)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            routes.currentRoute?.topBarSubActions?.let {
                val navigationBarMaxHeight = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                        .offset {
                            IntOffset(0, -navigationBarMaxHeight.roundToPx())
                        }
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth()
                    ) {
                        var rowScope: RowScope? = null
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowScope = this
                        }
                        rowScope?.let { scope -> it.invoke(scope) }
                    }
                }
            }
        }
    }
}
