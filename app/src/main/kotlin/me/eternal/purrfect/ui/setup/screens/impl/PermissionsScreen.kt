package me.eternal.purrfect.ui.setup.screens.impl

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.columnPadding
import me.eternal.purrfect.ui.setup.screens.SetupScreen
import me.eternal.purrfect.ui.util.OnLifecycleEvent

data class PermissionData(
    val translationKey: String,
    val isPermissionGranted: () -> Boolean,
    val requestPermission: (PermissionData) -> Unit,
)

class PermissionsScreen : SetupScreen() {
    @SuppressLint("BatteryLife")
    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val grantedPermissions = remember {
            mutableStateMapOf<String, Boolean>()
        }
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                grantedPermissions["notification_permission"] = isGranted
            }
        }

        val permissions = remember {
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionData(
                        translationKey = "notification_permission",
                        isPermissionGranted = {
                            context.androidContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        },
                        requestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                } else null,
                PermissionData(
                    translationKey = "battery_optimization",
                    isPermissionGranted = {
                        val powerManager = context.androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                        powerManager.isIgnoringBatteryOptimizations(context.androidContext.packageName)
                    },
                    requestPermission = {
                        context.activity!!.startActivity(
                            Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:${context.androidContext.packageName}".toUri()
                            }
                        )
                    }
                ),
                PermissionData(
                    translationKey = "display_over_other_apps",
                    isPermissionGranted = {
                        Settings.canDrawOverlays(context.androidContext)
                    },
                    requestPermission = {
                        context.activity!!.startActivity(
                            Intent().apply {
                                action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                                data = "package:${context.androidContext.packageName}".toUri()
                            }
                        )
                    }
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PermissionData(
                        translationKey = "request_install_unknown_apps",
                        isPermissionGranted = {
                            context.androidContext.packageManager.canRequestPackageInstalls()
                        },
                        requestPermission = {
                            context.activity!!.startActivity(
                                Intent().apply {
                                    action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                    data = "package:${context.androidContext.packageName}".toUri()
                                }
                            )
                        }
                    )
                } else null
            )
        }

        fun updateState() {
            permissions.forEach { permission ->
                grantedPermissions[permission.translationKey] = permission.isPermissionGranted()
            }
            if (permissions.all { perm -> grantedPermissions[perm.translationKey] == true }) {
                goNext()
            }
        }

        OnLifecycleEvent { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    updateState()
                    delay(1000)
                    updateState()
                }
            }
        }

        LaunchedEffect(Unit) {
            updateState()
        }

        DialogText(
            text = context.translation["setup.permissions.dialog"]
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = columnPadding)
        ) {
            permissions.forEachIndexed { index, perm ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(permissions.size, index)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(text = context.translation["setup.permissions.${perm.translationKey}"])
                        },
                        trailingContent = {
                            if (grantedPermissions[perm.translationKey] == true) {
                                Icon(Icons.Rounded.Check, null)
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        if (perm.isPermissionGranted()) {
                                            grantedPermissions[perm.translationKey] = true
                                        } else {
                                            perm.requestPermission(perm)
                                        }
                                    }
                                ) {
                                    Text(text = context.translation["setup.permissions.request_button"])
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
