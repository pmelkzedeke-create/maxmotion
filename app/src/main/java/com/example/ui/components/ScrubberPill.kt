package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AnimatableProperty
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrubberRow(
    label: String,
    value: Float,
    stepSensitivity: Float = 0.5f,
    minValue: Float = -10000f,
    maxValue: Float = 10000f,
    suffix: String = "",
    decimals: Int = 1,
    property: AnimatableProperty,
    playheadLocalMs: Long,
    testTagPrefix: String,
    onDragStart: () -> Unit,
    onValueChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleKeyframe: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenExpression: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val hasKeyframeHere = property.keyframes.any { abs(it.timeMs - playheadLocalMs) <= 25L }
    val hasExpression = !property.expression.isNullOrBlank()
    var liveValue by remember(value) { mutableFloatStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )

        // Draggable Numeric Scrubber Pill (AE style)
        Box(
            modifier = Modifier
                .width(116.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioElevated)
                .border(
                    width = 1.dp,
                    color = if (hasExpression) ElectricPurple else StudioBorder,
                    shape = RoundedCornerShape(10.dp)
                )
                .pointerInput(minValue, maxValue, stepSensitivity) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            liveValue = value
                            onDragStart()
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onHorizontalDrag = { _, dragAmount ->
                            liveValue = (liveValue + dragAmount * stepSensitivity).coerceIn(minValue, maxValue)
                            onValueChange(liveValue)
                        }
                    )
                }
                .testTag("${testTagPrefix}_scrubber"),
            contentAlignment = Alignment.Center
        ) {
            val formatted = String.format(Locale.US, "%.${decimals}f%s", value, suffix)
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasExpression) ElectricPurple else NeonCyan
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Graph Editor shortcut (when >= 2 keyframes)
        IconButton(
            onClick = onOpenGraph,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .testTag("${testTagPrefix}_graph_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.ShowChart,
                contentDescription = "Abrir gráfico de curva para $label",
                tint = if (property.keyframes.size >= 2) NeonCyan else TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Expression button
        IconButton(
            onClick = onOpenExpression,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .testTag("${testTagPrefix}_expr_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = "Expressão para $label",
                tint = if (hasExpression) ElectricPurple else TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Keyframe Diamond Toggle (Tap = Toggle keyframe, Long press = Expression)
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleKeyframe()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenExpression()
                    }
                )
                .testTag("${testTagPrefix}_kf_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Diamond,
                contentDescription = "Alternar keyframe de $label",
                tint = when {
                    hasKeyframeHere -> NeonCyan
                    property.keyframes.isNotEmpty() -> ElectricPurple
                    else -> TextPrimary.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
