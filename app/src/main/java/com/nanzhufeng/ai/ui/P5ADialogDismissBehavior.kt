package com.nanzhufeng.ai.ui

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.abs

/** A centered in-app dialog should separate the task without visually blacking out the app. */
private const val P5ACenteredDialogScrimAlpha = 0.12f

@Composable
private fun P5ALightDialogScrimEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(P5ACenteredDialogScrimAlpha)
        }
        onDispose { }
    }
}

/** Use on a full-screen in-app overlay/backdrop that is not backed by a Dialog window. */
internal fun Modifier.p5aDismissOnInwardEdgeSwipe(onDismissRequest: () -> Unit): Modifier = pointerInput(onDismissRequest) {
    val activationEdge = 56.dp.toPx()
    val completionDistance = 72.dp.toPx()
    var direction = 0
    var travel = 0f
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            direction = when {
                offset.x <= activationEdge -> 1
                offset.x >= size.width - activationEdge -> -1
                else -> 0
            }
            travel = 0f
        },
        onHorizontalDrag = { _, dragAmount -> if (direction != 0) travel += dragAmount },
        onDragCancel = { direction = 0 },
        onDragEnd = {
            if ((direction == 1 && travel >= completionDistance) ||
                (direction == -1 && travel <= -completionDistance)
            ) onDismissRequest()
            direction = 0
        },
    )
}

/**
 * Shared modal dismissal contract. The platform continues to own the physical edge-back zone;
 * this only accepts an app-delivered, predominantly horizontal drag that starts near either
 * visible edge and travels inward. It observes without consuming, so a dialog's own content,
 * scroll containers and media gestures retain priority.
 */
@Composable
private fun P5ADialogEdgeDismissEffect(onDismissRequest: () -> Unit) {
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val density = LocalDensity.current
    val activationEdge = with(density) { 56.dp.toPx() }
    val completionDistance = with(density) { 72.dp.toPx() }
    val view = LocalView.current

    DisposableEffect(view, activationEdge, completionDistance) {
        val host = view.rootView
        var direction = 0
        var startX = 0f
        var startY = 0f
        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    direction = when {
                        event.x <= activationEdge -> 1
                        event.x >= host.width - activationEdge -> -1
                        else -> 0
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val horizontalTravel = event.rawX - startX
                    val verticalTravel = event.rawY - startY
                    val isPredominantlyHorizontal = abs(horizontalTravel) >= abs(verticalTravel) * 1.3f
                    if (isPredominantlyHorizontal && (
                        (direction == 1 && horizontalTravel >= completionDistance) ||
                            (direction == -1 && horizontalTravel <= -completionDistance)
                        )
                    ) latestDismiss()
                    direction = 0
                }

                MotionEvent.ACTION_CANCEL -> direction = 0
            }
            false
        }
        host.setOnTouchListener(listener)
        onDispose { host.setOnTouchListener(null) }
    }
}

/** Standard Material dialog with the shared edge-inward dismissal behavior. */
@Composable
internal fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            P5ALightDialogScrimEffect()
            P5ADialogEdgeDismissEffect(onDismissRequest)
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** Custom/full-screen dialogs share the exact same dismissal behavior as Material dialogs. */
@Composable
internal fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    ComposeDialog(onDismissRequest = onDismissRequest, properties = properties) {
        P5ALightDialogScrimEffect()
        P5ADialogEdgeDismissEffect(onDismissRequest)
        content()
    }
}
