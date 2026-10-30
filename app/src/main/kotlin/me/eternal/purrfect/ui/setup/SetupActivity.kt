package me.eternal.purrfect.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.eternal.purrfect.SharedContextHolder
import me.eternal.purrfect.common.ui.AppMaterialTheme
import me.eternal.purrfect.ui.setup.screens.SetupScreen
import me.eternal.purrfect.ui.setup.screens.impl.MappingsScreen
import me.eternal.purrfect.ui.setup.screens.impl.PermissionsScreen
import me.eternal.purrfect.ui.setup.screens.impl.PickLanguageScreen
import me.eternal.purrfect.ui.setup.screens.impl.SaveFolderScreen

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val setupContext = SharedContextHolder.remote(this).apply {
            activity = this@SetupActivity
        }

        fun endActivity() {
            setupContext.reload()
            finish()
        }

        val requiredScreens = mutableListOf<SetupScreen>()
        val requirements = intent.getIntExtra("requirements", Requirements.FIRST_RUN)
        fun hasRequirement(requirement: Int) = requirements and requirement == requirement

        if (hasRequirement(Requirements.FIRST_RUN)) {
            requiredScreens.add(
                PickLanguageScreen().apply {
                    route = "language"
                }
            )
        }

        if (hasRequirement(Requirements.GRANT_PERMISSIONS)) {
            requiredScreens.add(
                PermissionsScreen().apply {
                    route = "permissions"
                }
            )
        }

        if (hasRequirement(Requirements.SAVE_FOLDER)) {
            requiredScreens.add(
                SaveFolderScreen().apply {
                    route = "saveFolder"
                }
            )
        }

        if (hasRequirement(Requirements.MAPPINGS)) {
            requiredScreens.add(
                MappingsScreen().apply {
                    route = "mappings"
                }
            )
        }

        if (requiredScreens.isEmpty()) {
            endActivity()
            return
        }

        requiredScreens.forEach { screen ->
            screen.context = setupContext
            screen.init()
        }

        setContent {
            val navController = rememberNavController()
            var canGoNext by remember { mutableStateOf(false) }
            val currentScreen = remember { mutableStateOf(requiredScreens.first()) }

            fun nextScreen() {
                if (!canGoNext) return
                canGoNext = false
                currentScreen.value.onLeave()
                val index = requiredScreens.indexOf(currentScreen.value)
                if (index + 1 < requiredScreens.size) {
                    currentScreen.value = requiredScreens[index + 1]
                    navController.navigate(currentScreen.value.route)
                } else {
                    endActivity()
                }
            }

            LaunchedEffect(Unit) {
                requiredScreens.forEach { screen ->
                    screen.allowNext = { canGoNext = it }
                    screen.goNext = { nextScreen() }
                }
            }

            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = canGoNext,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            FloatingActionButton(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .offset { IntOffset(-10, -10) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                onClick = { nextScreen() }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    modifier = Modifier.size(20.dp),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                ) {
                    NavHost(
                        modifier = Modifier.padding(it),
                        navController = navController,
                        startDestination = requiredScreens.first().route,
                        enterTransition = { slideInHorizontally { width -> width } },
                        exitTransition = { slideOutHorizontally { width -> -width } }
                    ) {
                        requiredScreens.forEach { screen ->
                            composable(screen.route) {
                                BackHandler(true) {}
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 110.dp),
                                    verticalArrangement = Arrangement.SpaceAround,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    screen.apply {
                                        LaunchedEffect(Unit) {
                                            canGoNext = false
                                        }
                                        Content()
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
