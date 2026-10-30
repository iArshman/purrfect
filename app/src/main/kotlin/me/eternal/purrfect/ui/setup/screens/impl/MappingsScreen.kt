package me.eternal.purrfect.ui.setup.screens.impl

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.ui.setup.screens.SetupScreen
import me.eternal.purrfect.ui.util.BottomSheets

class MappingsScreen : SetupScreen() {
    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        var infoText by remember { mutableStateOf(null as String?) }
        var isGenerating by remember { mutableStateOf(false) }

        if (infoText != null) {
            fun dismiss() {
                infoText = null
                goNext()
            }

            remember { BottomSheets(context.translation) }.InfoBottomSheet(
                title = infoText!!,
                onDismiss = { dismiss() }
            )
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                if (isGenerating) return@launch
                isGenerating = true
                runCatching {
                    val warnings = context.mappings.refresh()

                    if (warnings.isNotEmpty()) {
                        isGenerating = false
                        infoText = "${warnings.size} warning(s) occurred while generating mappings:\n\n${warnings.joinToString("\n")}".also {
                            context.log.warn(it)
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        allowNext(true)
                        goNext()
                    }
                }.onFailure {
                    isGenerating = false
                    infoText = context.translation["setup.mappings.generate_failure"] + "\n\n" + it.message
                    context.log.error("Failed to generate mappings", it)
                }
            }
        }

        if (isGenerating) {
            DialogText(text = context.translation["setup.mappings.dialog"])
            CircularProgressIndicator(
                modifier = Modifier
                    .padding()
                    .size(50.dp)
            )
        }
    }
}
