package me.eternal.purrfect.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.eternal.purrfect.common.ui.bottomSheetClipShape
import me.eternal.purrfect.common.ui.bottomSheetShape
import me.eternal.purrfect.common.ui.columnPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultBottomSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    disableDragging: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newState ->
            if (disableDragging) {
                newState != SheetValue.Hidden
            } else {
                true
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = bottomSheetShape,
        contentWindowInsets = { WindowInsets.ime },
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun LazyColumnBottomSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    disableDragging: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    DefaultBottomSheet(
        modifier = modifier,
        onDismiss = onDismiss,
        disableDragging = disableDragging
    ) {
        LazyColumn(
            modifier = Modifier.clip(bottomSheetClipShape),
            contentPadding = PaddingValues(
                start = columnPadding,
                end = columnPadding,
                bottom = columnPadding + WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            )
        ) {
            content()
        }
    }
}
