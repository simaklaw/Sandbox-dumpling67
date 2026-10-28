package com.example.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 📦 PagedGrid (ШАГ 14):
 * Displays items in 2x3 grid (max 6 per page).
 * If items > 6:
 * - Shows glossy 88 dp pagination arrows [◀] and [▶] at bottom.
 * - Shows page dot indicators.
 * - Supports horizontal swipe gestures with smooth 250ms slide transition and whoosh_soft sound.
 * If items <= 6:
 * - Displays single page without bottom navigation controls.
 */
@Composable
fun <T> PagedGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    itemsPerPage: Int = 6,
    soundManager: SoundManager,
    haptics: Haptics = rememberHaptics(),
    itemContent: @Composable (item: T, index: Int) -> Unit
) {
    val totalPages = (items.size + itemsPerPage - 1) / itemsPerPage
    var currentPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(totalPages) {
                if (totalPages > 1) {
                    detectDragGestures { _, dragAmount ->
                        if (abs(dragAmount.x) > 40f && abs(dragAmount.y) < 30f) {
                            if (dragAmount.x < 0 && currentPage < totalPages - 1) {
                                soundManager.play("whoosh_soft")
                                haptics.vibrateShort()
                                currentPage++
                            } else if (dragAmount.x > 0 && currentPage > 0) {
                                soundManager.play("whoosh_soft")
                                haptics.vibrateShort()
                                currentPage--
                            }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Grid Page Content with 250ms slide animation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = tween(250)) { it } + fadeIn(animationSpec = tween(250)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { -it } + fadeOut(animationSpec = tween(150)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(250)) { -it } + fadeIn(animationSpec = tween(250)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { it } + fadeOut(animationSpec = tween(150)))
                        }
                    },
                    label = "pagedGridContent"
                ) { pageIndex ->
                    val startIndex = pageIndex * itemsPerPage
                    val endIndex = (startIndex + itemsPerPage).coerceAtMost(items.size)
                    val pageItems = items.subList(startIndex, endIndex)

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (row in 0 until 3) {
                            val rowStart = row * 2
                            if (rowStart < pageItems.size) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Item 1
                                    Box(modifier = Modifier.weight(1f)) {
                                        val actualIndex = startIndex + rowStart
                                        itemContent(pageItems[rowStart], actualIndex)
                                    }

                                    // Item 2
                                    if (rowStart + 1 < pageItems.size) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            val actualIndex = startIndex + rowStart + 1
                                            itemContent(pageItems[rowStart + 1], actualIndex)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Controls (Only if totalPages > 1)
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Arrow Button [◀] (88 dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(if (currentPage > 0) ColorOrange else ColorBgSoft, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                if (currentPage > 0) {
                                    soundManager.play("whoosh_soft")
                                    haptics.vibrateShort()
                                    currentPage--
                                }
                            }
                            .testTag("paged_grid_prev_btn")
                    ) {
                        Text(
                            text = "◀",
                            fontSize = 32.sp,
                            color = if (currentPage > 0) ColorInk else ColorInk.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Dots indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(ColorWhite, ShapeCard)
                            .border(BorderStroke(2.5.dp, ColorInk), ShapeCard)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        for (i in 0 until totalPages) {
                            val isSelected = currentPage == i
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 24.dp else 16.dp)
                                    .border(
                                        BorderStroke(if (isSelected) 3.dp else 1.5.dp, ColorInk),
                                        CircleShape
                                    )
                                    .background(if (isSelected) ColorYellow else ColorBgSoft, CircleShape)
                            )
                        }
                    }

                    // Right Arrow Button [▶] (88 dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(if (currentPage < totalPages - 1) ColorOrange else ColorBgSoft, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                if (currentPage < totalPages - 1) {
                                    soundManager.play("whoosh_soft")
                                    haptics.vibrateShort()
                                    currentPage++
                                }
                            }
                            .testTag("paged_grid_next_btn")
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 32.sp,
                            color = if (currentPage < totalPages - 1) ColorInk else ColorInk.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
