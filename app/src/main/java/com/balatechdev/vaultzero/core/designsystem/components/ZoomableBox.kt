package com.balatechdev.vaultzero.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * ZoomableBox
 *
 * Professional two-finger pinch-to-zoom and pan container for Jetpack Compose.
 *
 * Key Capabilities:
 * 1. Two-finger pinch-to-zoom from [minScale] (1x) to [maxScale] (5x).
 * 2. Panning in all directions when zoomed in, with boundary clamping.
 * 3. Preserves normal 1-finger vertical scrolling of parent containers (e.g. LazyColumn or verticalScroll)
 *    when unzoomed (scale == 1x).
 * 4. Double-tap to zoom in to [doubleTapScale] (2.5x) or reset to 1x with smooth animation.
 * 5. Independent scale and offset state per instance.
 * 6. Clips content to its boundary to avoid overlapping adjacent items.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    doubleTapScale: Float = 2.5f,
    onScaleChanged: ((Float) -> Unit)? = null,
    content: @Composable BoxScope.(currentScale: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    var lastTapUpTime by remember { mutableLongStateOf(0L) }
    var lastTapUpPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scaleAnim.value) {
        onScaleChanged?.invoke(scaleAnim.value)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize, minScale, maxScale, doubleTapScale) {
                if (containerSize.width == 0 || containerSize.height == 0) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    val downPos = down.position
                    var hasMovedPastSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    val isDoubleTapCandidate = (downTime - lastTapUpTime < 320L) &&
                            (hypot(downPos.x - lastTapUpPos.x, downPos.y - lastTapUpPos.y) < touchSlop * 3)

                    var wasMultiTouch = false

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            wasMultiTouch = true
                            hasMovedPastSlop = true

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            val oldScale = scaleAnim.value
                            val newScale = ZoomMath.clampScale(oldScale * zoom, minScale, maxScale)

                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val centroidFromCenter = centroid - center

                            val newOffsetX = ZoomMath.calculateCentroidOffset(
                                currentOffset = offsetXAnim.value,
                                centroidFromCenter = centroidFromCenter.x,
                                oldScale = oldScale,
                                newScale = newScale,
                                pan = pan.x,
                                size = containerSize.width.toFloat()
                            )

                            val newOffsetY = ZoomMath.calculateCentroidOffset(
                                currentOffset = offsetYAnim.value,
                                centroidFromCenter = centroidFromCenter.y,
                                oldScale = oldScale,
                                newScale = newScale,
                                pan = pan.y,
                                size = containerSize.height.toFloat()
                            )

                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newOffsetX)
                                offsetYAnim.snapTo(newOffsetY)
                            }

                            // Consume multi-touch movements so parent scrollable containers don't intercept pinch
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else if (pointerCount == 1) {
                            val change = event.changes[0]
                            val distance = hypot(change.position.x - downPos.x, change.position.y - downPos.y)
                            if (distance > touchSlop) {
                                hasMovedPastSlop = true
                            }

                            if (ZoomMath.shouldConsumeDrag(scaleAnim.value)) {
                                // When zoomed in, 1-finger drag pans the zoomed content
                                val pan = event.calculatePan()
                                val currentScale = scaleAnim.value

                                val newOffsetX = ZoomMath.clampOffset(
                                    offset = offsetXAnim.value + pan.x,
                                    size = containerSize.width.toFloat(),
                                    scale = currentScale
                                )
                                val newOffsetY = ZoomMath.clampOffset(
                                    offset = offsetYAnim.value + pan.y,
                                    size = containerSize.height.toFloat(),
                                    scale = currentScale
                                )

                                coroutineScope.launch {
                                    offsetXAnim.snapTo(newOffsetX)
                                    offsetYAnim.snapTo(newOffsetY)
                                }

                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            } else {
                                // When at 1x scale, DO NOT consume!
                                // Allows 1-finger vertical scroll to propagate to parent LazyColumn or verticalScroll
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val upTime = System.currentTimeMillis()
                    if (!wasMultiTouch && !hasMovedPastSlop && (upTime - downTime < 300L)) {
                        // Clean single-finger tap
                        if (isDoubleTapCandidate) {
                            // Double-tap triggered!
                            lastTapUpTime = 0L
                            val currentScale = scaleAnim.value
                            coroutineScope.launch {
                                if (currentScale > 1.05f) {
                                    // Reset to 1x
                                    launch { scaleAnim.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                } else {
                                    // Zoom in to doubleTapScale centered around tap position
                                    val targetScale = ZoomMath.clampScale(doubleTapScale, minScale, maxScale)
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val tapFromCenter = downPos - center

                                    val targetOffsetX = ZoomMath.calculateDoubleTapOffset(
                                        tapFromCenter = tapFromCenter.x,
                                        targetScale = targetScale,
                                        size = containerSize.width.toFloat()
                                    )
                                    val targetOffsetY = ZoomMath.calculateDoubleTapOffset(
                                        tapFromCenter = tapFromCenter.y,
                                        targetScale = targetScale,
                                        size = containerSize.height.toFloat()
                                    )

                                    launch { scaleAnim.animateTo(targetScale, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetXAnim.animateTo(targetOffsetX, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetYAnim.animateTo(targetOffsetY, tween(250, easing = FastOutSlowInEasing)) }
                                }
                            }
                        } else {
                            lastTapUpTime = upTime
                            lastTapUpPos = downPos
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = offsetXAnim.value
                translationY = offsetYAnim.value
            },
            contentAlignment = Alignment.Center
        ) {
            content(scaleAnim.value)
        }
    }
}
