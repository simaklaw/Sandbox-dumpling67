package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R

/** Lottie-эффекты (LottieFiles): конфетти — один раз при каждом увеличении trigger; петля — для «живых» картинок. */
@Composable
internal fun LottieConfetti(trigger: Int, modifier: Modifier = Modifier) {
    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_confetti))
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) { if (trigger > 0) playing = true }
    val progress by animateLottieCompositionAsState(comp, isPlaying = playing, restartOnPlay = true, iterations = 1)
    LaunchedEffect(progress) { if (progress >= 1f) playing = false }
    if (playing && comp != null) LottieAnimation(comp, { progress }, modifier)
}

@Composable
internal fun LottieLoop(res: Int, modifier: Modifier = Modifier) {
    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    LottieAnimation(comp, modifier = modifier, iterations = LottieConstants.IterateForever)
}
