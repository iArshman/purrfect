package me.eternal.purrfect.ui.setup.screens.impl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.eternal.purrfect.ui.setup.screens.SetupScreen
import me.eternal.purrfect.ui.util.ActivityLauncherHelper
import me.eternal.purrfect.ui.util.chooseFolder

class SaveFolderScreen : SetupScreen() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    override fun Content() {
        DialogText(text = context.translation["setup.dialogs.save_folder"])
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                activityLauncherHelper.chooseFolder {
                    if (it.isBlank()) return@chooseFolder
                    context.config.root.downloader.saveFolder.set(it)
                    context.config.writeConfig()
                    allowNext(true)
                    goNext()
                }
            }
        ) {
            Text(text = context.translation["setup.dialogs.select_save_folder_button"])
        }
    }
}
