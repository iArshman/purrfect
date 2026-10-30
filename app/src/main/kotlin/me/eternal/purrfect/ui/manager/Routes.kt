package me.eternal.purrfect.ui.manager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.ui.manager.pages.FileImportsRoot
import me.eternal.purrfect.ui.manager.pages.LoggerHistoryRoot
import me.eternal.purrfect.ui.manager.pages.ManageReposSection
import me.eternal.purrfect.ui.manager.pages.TasksRootSection
import me.eternal.purrfect.ui.manager.pages.features.FeaturesRootSection
import me.eternal.purrfect.ui.manager.pages.features.ManageRuleFeature
import me.eternal.purrfect.ui.manager.pages.home.HomeAbout
import me.eternal.purrfect.ui.manager.pages.home.HomeAppTheme
import me.eternal.purrfect.ui.manager.pages.home.HomeAppearance
import me.eternal.purrfect.ui.manager.pages.home.HomeCredits
import me.eternal.purrfect.ui.manager.pages.home.HomeLogs
import me.eternal.purrfect.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfect.ui.manager.pages.home.HomeSettings
import me.eternal.purrfect.ui.manager.pages.home.HomeThirdPartyLibraries
import me.eternal.purrfect.ui.manager.pages.home.HomeUpdating
import me.eternal.purrfect.ui.manager.pages.location.BetterLocationRoot
import me.eternal.purrfect.ui.manager.pages.scripting.ScriptingRootSection
import me.eternal.purrfect.ui.manager.pages.social.LoggedStories
import me.eternal.purrfect.ui.manager.pages.social.ManageScope
import me.eternal.purrfect.ui.manager.pages.social.MessagingPreview
import me.eternal.purrfect.ui.manager.pages.social.SocialRootSection
import me.eternal.purrfect.ui.manager.pages.tracker.EditRule
import me.eternal.purrfect.ui.manager.pages.tracker.FriendTrackerManagerRoot

data class RouteInfo(
    val id: String,
    val key: String = id,
    val primaryIcon: ImageVector = Icons.Filled.Home,
    val secondaryIcon: ImageVector = Icons.Outlined.Home,
    val primary: Boolean = false,
    val showInNavBar: Boolean = primary,
) {
    var translatedKey: Lazy<String>? = null
    val childIds = mutableListOf<String>()
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Routes(
    private val context: RemoteSideContext,
) {
    companion object {
        const val DEFAULT_ROUTE = "home"
    }

    lateinit var navController: NavController
    var bottomPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)
    private val routes = mutableListOf<Route>()
    val currentRoute: Route?
        get() = getCurrentRoute(navController.currentBackStackEntry)

    val currentDestination: String?
        get() = navController.currentBackStackEntry?.destination?.route

    val tasks = route(
        RouteInfo(
            id = "tasks",
            primaryIcon = Icons.Filled.TaskAlt,
            secondaryIcon = Icons.Outlined.TaskAlt,
            primary = true,
        ),
        TasksRootSection()
    )

    val features = route(
        RouteInfo(
            id = "features",
            primaryIcon = Icons.Filled.Stars,
            secondaryIcon = Icons.Outlined.Stars,
            primary = true
        ),
        FeaturesRootSection()
    )

    val manageRuleFeature = route(
        RouteInfo(
            id = "manage_rule_feature/?rule_type={rule_type}",
            key = "features",
        ),
        ManageRuleFeature()
    ).parent(features)

    val home = route(
        RouteInfo(
            id = "home",
            primaryIcon = Icons.Filled.Home,
            secondaryIcon = Icons.Outlined.Home,
            primary = true,
        ),
        HomeRootSection()
    )

    val settings = route(
        RouteInfo(
            id = "home_settings",
            primaryIcon = Icons.Filled.Settings,
        ),
        HomeSettings()
    ).parent(home)

    val homeLogs = route(
        RouteInfo(
            id = "home_logs",
        ),
        HomeLogs()
    ).parent(home)

    val loggerHistory = route(
        RouteInfo(
            id = "logger_history",
        ),
        LoggerHistoryRoot()
    ).parent(home)

    val loggedStories = route(
        RouteInfo(
            id = "logged_stories/?id={id}",
        ),
        LoggedStories()
    ).parent(home)

    val friendTracker = route(
        RouteInfo(
            id = "friend_tracker",
        ),
        FriendTrackerManagerRoot()
    ).parent(home)

    val editRule = route(
        RouteInfo(
            id = "edit_rule/?rule_id={rule_id}",
        ),
        EditRule()
    ).parent(home)

    val fileImports = route(
        RouteInfo(
            id = "file_imports",
        ),
        FileImportsRoot()
    ).parent(home)

    val manageRepos = route(
        RouteInfo(
            id = "manage_repos",
        ),
        ManageReposSection()
    ).parent(home)

    val betterLocation = route(
        RouteInfo(
            id = "better_location",
            primaryIcon = Icons.Filled.Explore,
            secondaryIcon = Icons.Outlined.Explore,
            primary = false,
        ),
        BetterLocationRoot()
    ).parent(home)

    val social = route(
        RouteInfo(
            id = "social",
            primaryIcon = Icons.Filled.Group,
            secondaryIcon = Icons.Outlined.Group,
            primary = true
        ),
        SocialRootSection()
    )

    val manageScope = route(
        RouteInfo(
            id = "manage_scope/?scope={scope}&id={id}",
        ),
        ManageScope()
    ).parent(social)

    val messagingPreview = route(
        RouteInfo(
            id = "messaging_preview/?scope={scope}&id={id}",
        ),
        MessagingPreview()
    ).parent(social)

    val scripts = route(
        RouteInfo(
            id = "scripts",
            primaryIcon = Icons.Filled.DataObject,
            secondaryIcon = Icons.Outlined.DataObject,
            primary = true
        ),
        ScriptingRootSection()
    )

    val scripting: Route
        get() = scripts

    val homeAppearance = route(
        RouteInfo(
            id = "home_appearance",
        ),
        HomeAppearance()
    ).parent(home)

    val homeAppTheme = route(
        RouteInfo(
            id = "home_app_theme",
        ),
        HomeAppTheme()
    ).parent(home)

    val homeUpdating = route(
        RouteInfo(
            id = "home_updating",
        ),
        HomeUpdating()
    ).parent(home)

    val homeAbout = route(
        RouteInfo(
            id = "home_about",
        ),
        HomeAbout()
    ).parent(home)

    val about: Route
        get() = homeAbout

    val homeCredits = route(
        RouteInfo(
            id = "home_credits",
        ),
        HomeCredits()
    ).parent(home)

    val homeThirdPartyLibraries = route(
        RouteInfo(
            id = "home_third_party_libraries",
        ),
        HomeThirdPartyLibraries()
    ).parent(home)

    open class Route {
        lateinit var context: RemoteSideContext
        lateinit var routeInfo: RouteInfo
        lateinit var routes: Routes
        var parentRoute: Route? = null
        var currentBackStackEntry: NavBackStackEntry? = null

        open val translation by lazy { context.translation }

        open val arguments: List<NamedNavArgument> = emptyList()

        private var topBarActionsComposable: (@Composable RowScope.() -> Unit)? = null
        var topBarSubActions: (@Composable RowScope.() -> Unit)? = null
            private set

        private var floatingActionButtonComposable: (@Composable () -> Unit)? = null
        private var titleComposable: (@Composable () -> Unit)? = null

        open fun init() {}

        open val content: @Composable (NavBackStackEntry) -> Unit = {}

        open val customComposables: @Composable () -> Unit = {}

        val isPrimary: Boolean
            get() = routeInfo.primary && parentRoute == null

        val translatedKey: String?
            get() = routeInfo.translatedKey?.value

        fun topBarActions() = topBarActionsComposable
        fun title() = titleComposable
        val floatingActionButton: (@Composable () -> Unit)?
            get() = floatingActionButtonComposable

        fun setTopBarActions(actions: @Composable RowScope.() -> Unit) {
            topBarActionsComposable = actions
        }

        fun topBarActions(actions: @Composable RowScope.() -> Unit): @Composable (NavBackStackEntry) -> Unit {
            topBarActionsComposable = actions
            return content
        }

        fun setTopBarSubActions(actions: (@Composable RowScope.() -> Unit)?) {
            topBarSubActions = actions
        }

        fun topBarSubActions(actions: @Composable RowScope.() -> Unit): @Composable (NavBackStackEntry) -> Unit {
            topBarSubActions = actions
            return content
        }

        fun setFloatingActionButton(fab: @Composable () -> Unit) {
            floatingActionButtonComposable = fab
        }

        fun floatingActionButton(fab: @Composable () -> Unit): @Composable (NavBackStackEntry) -> Unit {
            floatingActionButtonComposable = fab
            return content
        }

        fun title(title: @Composable () -> Unit): @Composable (NavBackStackEntry) -> Unit {
            titleComposable = title
            return content
        }

        open fun canGoBack(): Boolean = false

        private fun replaceArguments(id: String, args: Map<String, String>): String {
            return args.entries.fold(id) { acc, (key, value) ->
                acc.replace("{$key}", value)
            }
        }

        fun navigate(args: MutableMap<String, String>.() -> Unit = {}) {
            if (parentRoute == null) {
                navigateReset(args)
                return
            }
            routes.navController.navigate(replaceArguments(routeInfo.id, HashMap<String, String>().apply(args))) {
                launchSingleTop = true
            }
        }

        fun navigateReload() {
            routes.navController.navigate(routeInfo.id) {
                popUpTo(routeInfo.id) { inclusive = true }
            }
        }

        fun navigateReset(args: MutableMap<String, String>.() -> Unit = {}) {
            routes.navController.navigate(replaceArguments("main_" + routeInfo.id, HashMap<String, String>().apply(args))) {
                popUpTo(routes.navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
                restoreState = false
            }
        }

        fun parent(parent: Route): Route {
            parentRoute = parent
            parent.routeInfo.childIds.add(routeInfo.id)
            return this
        }
    }

    open class Section : Route()

    fun getCurrentRoute(navBackStackEntry: NavBackStackEntry?): Route? {
        if (navBackStackEntry == null) return null

        return navBackStackEntry.destination.hierarchy.firstNotNullOfOrNull { destination ->
            routes.firstOrNull { route ->
                route.routeInfo.id == destination.route || "main_" + route.routeInfo.id == destination.route
            }
        }
    }

    fun getRoutes(): List<Route> = routes

    private fun route(routeInfo: RouteInfo, section: Route): Route {
        return section.apply {
            this.context = this@Routes.context
            this.routes = this@Routes
            this.routeInfo = routeInfo.apply {
                translatedKey = lazy { "manager.routes.${routeInfo.key.substringBefore("/")}" }
            }
            this.init()
            this@Routes.routes.add(this)
        }
    }
}
