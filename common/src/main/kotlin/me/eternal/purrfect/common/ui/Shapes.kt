package me.eternal.purrfect.common.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val cardShapeSingle = RoundedCornerShape(16.dp)
val cardShapeGroupedTop = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 4.dp,
    bottomEnd = 4.dp
)
val cardShapeGroupedMiddle = RoundedCornerShape(4.dp)
val cardShapeGroupedBottom = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomStart = 16.dp,
    bottomEnd = 16.dp
)
val bottomSheetShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)
val bottomSheetClipShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

fun cardShape(groupSize: Int, index: Int): RoundedCornerShape {
    return when {
        groupSize <= 1 -> cardShapeSingle
        index == 0 -> cardShapeGroupedTop
        index == groupSize - 1 -> cardShapeGroupedBottom
        else -> cardShapeGroupedMiddle
    }
}
