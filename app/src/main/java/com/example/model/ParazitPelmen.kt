package com.example.model

/**
 * State machine stages for the ParazitPelmen prefab entity.
 */
enum class ParazitPelmenState {
    INACTIVE,
    JUMPING_IN, // Leaping in an arc towards the hero's head
    ATTACHED,   // Sitting on top of the hero's head, occasionally chanting or slapping
    JUMPING_OUT,// Leaping away in an arc towards the ground
    EXPLODING   // Squelch splash upon hitting ground, lingers 0.6s before removal
}

/**
 * ParazitPelmen entity:
 * - Scale 0.45 - 0.55 relative to the main hero
 * - Trigger collider representation
 * - Full state machine with timer and physics state
 */
data class ParazitPelmen(
    var state: ParazitPelmenState = ParazitPelmenState.INACTIVE,
    var scale: Float = 0.5f, // 0.45 - 0.55 relative scale
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var targetHeadX: Float = 0f,
    var targetHeadY: Float = 0f,
    var jumpProgress: Float = 0f, // 0.0 -> 1.0 arc progress
    var jumpArcHeight: Float = 140f,
    var attachedTimerSec: Float = 0f,
    var attachedDurationSec: Float = 5.5f, // 4-7 sec
    var chantIntervalTimerSec: Float = 0f,
    var nextChantTargetSec: Float = 1.4f, // 1.2 - 1.8 sec
    var handWavingPhase: Float = 0f,
    var sinWobbleAngle: Float = 0f,
    var speechBubble: String? = null,
    var speechBubbleTimer: Float = 0f,
    var isSlappingHero: Boolean = false,
    var slapDurationTimer: Float = 0f,
    var explodeTimer: Float = 0f
)
