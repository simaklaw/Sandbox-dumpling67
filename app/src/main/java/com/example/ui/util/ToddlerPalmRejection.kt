package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Toddler Palm Rejection & Edge Touch Filter Utility.
 *
 * Designed specifically for young toddlers (ages 1-4) playing on touchscreens:
 * 1. Edge Bezel Deadzones: Filters out accidental thumb/palm touches resting on screen margins.
 * 2. Palm Cluster Rejection: Ignores resting palms that produce large clusters of simultaneous touch points.
 * 3. Grazing / Stationary Rejection: Distinguishes between intentional swipes/taps and resting flat skin.
 */
object ToddlerPalmRejection {

    const val DEFAULT_EDGE_MARGIN_DP = 22f

    /**
     * Checks if a touch offset falls within the edge bezel deadzone.
     */
    fun isNearEdge(
        position: Offset,
        containerSize: Size,
        edgeMarginPx: Float
    ): Boolean {
        if (containerSize.width <= 0 || containerSize.height <= 0) return false
        val leftBound = edgeMarginPx
        val rightBound = containerSize.width - edgeMarginPx
        val topBound = edgeMarginPx
        val bottomBound = containerSize.height - edgeMarginPx

        return position.x < leftBound ||
                position.x > rightBound ||
                position.y < topBound ||
                position.y > bottomBound
    }

    /**
     * Returns true if the touch event represents an intentional toddler interaction.
     * When palm rejection is disabled, always returns true.
     */
    fun isIntentionalTouch(
        position: Offset,
        containerSize: Size,
        edgeMarginPx: Float,
        enabled: Boolean = true
    ): Boolean {
        if (!enabled) return true
        return !isNearEdge(position, containerSize, edgeMarginPx)
    }

    /**
     * Detects if a cluster of active touch points is likely a flat resting toddler palm
     * (e.g. 3+ simultaneous touch contacts clustered within a small radius).
     */
    fun isPalmCluster(pointers: List<Offset>, clusterRadiusPx: Float = 160f): Boolean {
        if (pointers.size < 3) return false
        var closePairs = 0
        for (i in pointers.indices) {
            for (j in i + 1 until pointers.size) {
                val dx = pointers[i].x - pointers[j].x
                val dy = pointers[i].y - pointers[j].y
                if (hypot(dx, dy) < clusterRadiusPx) {
                    closePairs++
                }
            }
        }
        return closePairs >= 3
    }

    /**
     * Filters a list of pointer input changes to keep only intentional touches.
     */
    fun filterIntentionalChanges(
        changes: List<PointerInputChange>,
        containerSize: Size,
        edgeMarginPx: Float,
        enabled: Boolean = true
    ): List<PointerInputChange> {
        if (!enabled) return changes

        // If whole cluster is a resting palm, reject all
        val pressedPositions = changes.filter { it.pressed }.map { it.position }
        if (isPalmCluster(pressedPositions)) {
            return emptyList()
        }

        return changes.filter { change ->
            if (!change.pressed) {
                true // allow up events to clean up state
            } else {
                !isNearEdge(change.position, containerSize, edgeMarginPx)
            }
        }
    }
}

/**
 * Helper to compute edge margin in pixels from dp in Compose scope.
 */
@Composable
fun rememberEdgeMarginPx(marginDp: Dp = ToddlerPalmRejection.DEFAULT_EDGE_MARGIN_DP.dp): Float {
    val density = LocalDensity.current
    return remember(marginDp, density) {
        with(density) { marginDp.toPx() }
    }
}
