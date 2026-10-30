package me.eternal.purrfect.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.eternal.purrfect.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfect.common.config.DataProcessors
import me.eternal.purrfect.common.config.PropertyPair
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.columnPadding
import me.eternal.purrfect.common.ui.componentPadding
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet

enum class ConfirmBottomSheetType {
    CONFIRM,
    WARNING,
    INFO,
    ACK;
}

class BottomSheets(
    private val translation: LocaleWrapper,
) {
    @Composable
    fun ConfirmBottomSheet(
        title: String,
        message: String? = null,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
        confirmBottomSheetType: ConfirmBottomSheetType = ConfirmBottomSheetType.CONFIRM
    ) {
        val confirmButtonColors = when (confirmBottomSheetType) {
            ConfirmBottomSheetType.CONFIRM,
            ConfirmBottomSheetType.INFO,
            ConfirmBottomSheetType.ACK -> ButtonDefaults.buttonColors()
            ConfirmBottomSheetType.WARNING -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        }

        val confirmButtonText = when (confirmBottomSheetType) {
            ConfirmBottomSheetType.CONFIRM,
            ConfirmBottomSheetType.WARNING -> translation["button.positive"]
            ConfirmBottomSheetType.INFO,
            ConfirmBottomSheetType.ACK -> translation["button.ok"]
        }

        LazyColumnBottomSheet(
            onDismiss = onDismiss,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = columnPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (confirmBottomSheetType == ConfirmBottomSheetType.WARNING) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (message != null) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShapeSingle
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(componentPadding)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = columnPadding),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (confirmBottomSheetType != ConfirmBottomSheetType.ACK) {
                            OutlinedButton(
                                onClick = { onDismiss() }
                            ) {
                                Text(text = translation["button.negative"])
                            }
                        }
                        Button(
                            onClick = { onConfirm() },
                            colors = confirmButtonColors
                        ) {
                            Text(text = confirmButtonText)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun InfoBottomSheet(
        title: String,
        message: String? = null,
        onDismiss: () -> Unit,
    ) {
        ConfirmBottomSheet(
            title = title,
            message = message,
            onConfirm = onDismiss,
            onDismiss = onDismiss,
            confirmBottomSheetType = ConfirmBottomSheetType.ACK
        )
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun UniqueSelectionBottomSheet(property: PropertyPair<*>) {
        val keys = remember {
            (property.value.defaultValues as? List<String> ?: emptyList()).toMutableList().apply {
                add(0, "null")
            }
        }

        var selectedValue by remember {
            mutableStateOf(property.value.getNullable()?.toString() ?: "null")
        }

        LazyColumnBottomSheet(
            onDismiss = { property.value.setAny(selectedValue.takeIf { it != "null" }) }
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    keys.forEachIndexed { index, item ->
                        fun select() {
                            selectedValue = item
                            property.value.setAny(item.takeIf { it != "null" })
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShape(keys.size, index)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { select() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                key(selectedValue) {
                                    RadioButton(
                                        selected = selectedValue == item,
                                        onClick = { select() }
                                    )
                                }
                                Text(
                                    text = property.key.propertyOption(translation, item),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun KeyboardInputBottomSheet(
        property: PropertyPair<*>,
        DismissButton: @Composable (() -> Unit) -> Unit,
        ConfirmButton: @Composable (() -> Unit) -> Unit,
        dismiss: () -> Unit = {}
    ) {
        val focusRequester = remember { FocusRequester() }
        var fieldValue by remember {
            mutableStateOf(property.value.getNullable()?.toString() ?: "")
        }

        LazyColumnBottomSheet(
            onDismiss = dismiss,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 10.dp)
                            .onGloballyPositioned {
                                focusRequester.requestFocus()
                            }
                            .focusRequester(focusRequester),
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        keyboardOptions = when (property.key.dataType.type) {
                            DataProcessors.Type.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                            DataProcessors.Type.FLOAT -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            else -> KeyboardOptions(keyboardType = KeyboardType.Text)
                        },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DismissButton(dismiss)
                        ConfirmButton {
                            when (property.key.dataType.type) {
                                DataProcessors.Type.INTEGER -> runCatching {
                                    property.value.setAny(fieldValue.toInt())
                                }.onFailure {
                                    property.value.setAny(0)
                                }
                                DataProcessors.Type.FLOAT -> runCatching {
                                    property.value.setAny(fieldValue.toFloat())
                                }.onFailure {
                                    property.value.setAny(0f)
                                }
                                else -> property.value.setAny(fieldValue)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun MultipleSelectionBottomSheet(property: PropertyPair<*>) {
        val defaultItems = property.value.defaultValues as? List<String> ?: emptyList()
        val toggledStates = remember {
            (property.value.get() as MutableList<String>)
        }

        LazyColumnBottomSheet(
            onDismiss = { property.value.setAny(toggledStates) }
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    defaultItems.forEachIndexed { index, item ->
                        var state by remember { mutableStateOf(toggledStates.contains(item)) }

                        fun toggle() {
                            if (toggledStates.contains(item)) {
                                toggledStates.remove(item)
                                state = false
                            } else {
                                toggledStates.add(item)
                                state = true
                            }
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShape(defaultItems.size, index)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = property.key.propertyOption(translation, item),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                )
                                Checkbox(
                                    checked = state,
                                    onCheckedChange = { toggle() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChooseColorBottomSheet(
        property: PropertyPair<*>,
        DismissButton: @Composable (() -> Unit) -> Unit,
        ConfirmButton: @Composable (() -> Unit) -> Unit,
        ResetButton: @Composable (() -> Unit) -> Unit,
        dismiss: () -> Unit = {}
    ) {
        val alertDialogs = remember { AlertDialogs(translation) }
        LazyColumnBottomSheet(
            onDismiss = dismiss,
        ) {
            item {
                alertDialogs.ColorPickerPropertyDialog(
                    property = property,
                    dismiss = dismiss
                )
            }
        }
    }
}
