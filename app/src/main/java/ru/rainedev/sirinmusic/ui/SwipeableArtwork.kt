package ru.rainedev.sirinmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Direct finger tracking; release springs back while the next cover slides into view. */
@Composable
internal fun SwipeableArtwork(
    url: String?,
    size: Dp,
    trackKey: Long?,
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { size.toPx() }
    val threshold = with(density) { 48.dp.toPx() }.coerceAtLeast(widthPx * 0.22f)
    val minimumFlingDistance = with(density) { 16.dp.toPx() }
    val flingVelocity = with(density) { 800.dp.toPx() }
    // Loading and track changes must not reset the release animation halfway through.
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var direction by remember { mutableIntStateOf(0) }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val velocity = remember { VelocityTracker() }

    LaunchedEffect(dragging) {
        if (!dragging && drag != 0f) {
            animate(drag, 0f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )) { value, _ -> drag = value }
        }
    }
    LaunchedEffect(direction) {
        if (direction != 0) {
            delay(1_000)
            direction = 0
        }
    }
    val transitionDirection = remember(trackKey) { direction }
    val gestures = Modifier
            .pointerInput(trackKey, enabled, threshold, flingVelocity) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocity.resetTracking()
                        dragging = true
                    },
                    onDragCancel = { dragging = false },
                    onDragEnd = {
                        val speed = velocity.calculateVelocity().x
                        val fling = abs(drag) >= minimumFlingDistance && abs(speed) >= flingVelocity && drag * speed > 0f
                        val switch = abs(drag) >= threshold || fling
                        val travel = if (drag < 0f) -1 else 1
                        dragging = false
                        if (switch) {
                            direction = travel
                            if (travel < 0) next() else previous()
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        drag = (drag + amount).coerceIn(-widthPx * 0.6f, widthPx * 0.6f)
                    },
                )
            }
    Box(Modifier.size(size).then(gestures)) {
    AnimatedContent(
        targetState = trackKey to url,
        contentKey = { it.first },
        transitionSpec = {
            val travel = transitionDirection
            ((slideInHorizontally(tween(260)) { -travel * it / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(200)) { travel * it / 3 } + fadeOut(tween(160))))
                .using(SizeTransform(clip = false))
        },
        modifier = Modifier
            .graphicsLayer {
                translationX = drag
                val progress = (abs(drag) / widthPx).coerceIn(0f, 1f)
                scaleX = 1f - progress * 0.04f
                scaleY = scaleX
                rotationZ = (drag / widthPx).coerceIn(-1f, 1f) * 3f
            },
        label = "trackArtwork",
    ) { (_, artwork) ->
        Artwork(url = artwork, size = size, corner = 20.dp)
    }
    }
}
