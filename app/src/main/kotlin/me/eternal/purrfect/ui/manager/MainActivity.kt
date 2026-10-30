package me.eternal.purrfect.ui.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.SharedContextHolder
import me.eternal.purrfect.common.ui.AppMaterialTheme

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    lateinit var context: RemoteSideContext

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            intent.getStringExtra("route")?.let { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        context = SharedContextHolder.remote(this).apply {
            activity = this@MainActivity
            checkForRequirements()
        }

        val startDef = intent.getStringExtra("route") ?: Routes.DEFAULT_ROUTE

        setContent {
            val navHostController = rememberNavController()
            val navigation = Navigation(context, navHostController)

            LaunchedEffect(Unit) {
                navController = navHostController
            }

            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { navigation.TopBar() },
                    bottomBar = { navigation.BottomBar() },
                    floatingActionButton = { navigation.Fab() },
                    floatingActionButtonPosition = FabPosition.End
                ) { innerPadding ->
                    val _unused = innerPadding
                    navigation.NavContent(
                        PaddingValues(0.dp),
                        startDef
                    )
                }
            }
        }
    }
}
