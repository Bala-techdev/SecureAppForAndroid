package com.balatechdev.vaultzero.core.designsystem.components

import kotlin.math.max

/**
 * Mathematical calculations and bounding helpers for pinch-to-zoom and pan gestures.
 * Pure Kotlin functions with zero framework dependencies for 100% deterministic unit testing.
 */
object ZoomMath {
    fun clampScale(scale: Float, minScale: Float = 1f, maxScale: Float = 5f): Float {
        return scale.coerceIn(minScale, maxScale)
    }

    fun calculateMaxOffset(size: Float, scale: Float): Float {
        return max(0f, (size * (scale - 1f)) / 2f)
    }

    fun clampOffset(offset: Float, size: Float, scale: Float): Float {
        val maxOffset = calculateMaxOffset(size, scale)
        return offset.coerceIn(-maxOffset, maxOffset)
    }

    fun calculateCentroidOffset(
        currentOffset: Float,
        centroidFromCenter: Float,
        oldScale: Float,
        newScale: Float,
        pan: Float,
        size: Float
    ): Float {
        if (newScale <= 1.01f) return 0f
        val scaledOffset = (currentOffset - centroidFromCenter) * (newScale / oldScale) + centroidFromCenter + pan
        return clampOffset(scaledOffset, size, newScale)
    }

    fun calculateDoubleTapOffset(
        tapFromCenter: Float,
        targetScale: Float,
        size: Float
    ): Float {
        if (targetScale <= 1.01f) return 0f
        val rawOffset = -tapFromCenter * (targetScale - 1f)
        return clampOffset(rawOffset, size, targetScale)
    }

    fun shouldConsumeDrag(scale: Float): Boolean {
        return scale > 1.05f
    }
}
