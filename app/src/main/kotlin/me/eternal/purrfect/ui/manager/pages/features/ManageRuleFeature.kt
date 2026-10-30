package me.eternal.purrfect.ui.manager.pages.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.data.MessagingRuleType
import me.eternal.purrfect.common.data.RuleState
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.columnPadding
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.storage.*
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.util.ConfirmBottomSheetType
import me.eternal.purrfect.ui.util.BottomSheets

class ManageRuleFeature : Routes.Section() {
    private val bottomSheets by lazy { BottomSheets(context.translation) }

    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val currentRuleType = navBackStackEntry.arguments?.getString("rule_type")?.let {
            MessagingRuleType.getByName(it)
        } ?: MessagingRuleType.STEALTH

        val ruleStateList = listOf(
            null,
            RuleState.WHITELIST,
            RuleState.BLACKLIST
        )

        var currentRuleState by remember {
            mutableStateOf(context.config.root.rules.getRuleState(currentRuleType))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding())
        ) {
            Text(
                text = context.translation["rules.properties.${currentRuleType.key}.name"],
                modifier = Modifier.padding(16.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = context.translation["rules.properties.${currentRuleType.key}.description"],
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(horizontal = columnPadding)
            ) {
                itemsIndexed(ruleStateList) { index, ruleState ->
                    var showConfirmationBottomSheet by remember { mutableStateOf(false) }

                    if (showConfirmationBottomSheet) {
                        bottomSheets.ConfirmBottomSheet(
                            title = context.translation["manager.dialogs.clear_Print_list.title"],
                            message = context.translation["manager.dialogs.clear_Print_list.body"],
                            confirmBottomSheetType = ConfirmBottomSheetType.WARNING,
                            onConfirm = {
                                context.database.clearRuleIds(currentRuleType.key)
                                showConfirmationBottomSheet = false
                            },
                            onDismiss = { showConfirmationBottomSheet = false }
                        )
                    }

                    fun select() {
                        currentRuleState = ruleState
                        context.config.root.rules.getPropertyPair(currentRuleType.key).value.setAny(ruleState?.key)
                        context.config.writeConfig()
                        if (ruleState == null && context.database.getRuleIds(currentRuleType.key).isNotEmpty()) {
                            showConfirmationBottomSheet = true
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(ruleStateList.size, index)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select() }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RadioButton(
                                selected = currentRuleState == ruleState,
                                onClick = { select() }
                            )
                            Text(
                                text = if (ruleState == null) {
                                    context.translation["rules.toasts.disabled"]
                                } else {
                                    context.translation["rules.modes.${ruleState.key}"]
                                }
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = cardShapeSingle
                    ) {
                        Text(
                            text = context.translation["manager.sections.features.Computation_notice"],
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
