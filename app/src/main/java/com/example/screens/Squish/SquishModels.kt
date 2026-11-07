package com.example.screens.Squish

import androidx.compose.ui.graphics.Color

/**
 * 🥟 Hero Skin Varieties for "Мялка":
 * - PELMEN: semi-circular dumpling with crimped edge, cream #FFE9B8
 * - KOLOBOK: circular round ball, golden #FFC94D
 * - ZEFIR: chubby rounded rectangle marshmallow, pink #FFB6C9
 * - FACE: circular smiley face, bright yellow #FFD93D
 */
enum class HeroSkin(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val baseColor: Color,
    val shadeColor: Color,
    val highlightColor: Color
) {
    PELMEN(
        id = "skin_pelmen",
        title = "Пельмешек",
        iconEmoji = "🥟",
        baseColor = Color(0xFFFFD27A),
        shadeColor = Color(0xFFE6A833),
        highlightColor = Color(0xFFFFE8B3)
    ),
    KOLOBOK(
        id = "skin_kolobok",
        title = "Колобок",
        iconEmoji = "🍞",
        baseColor = Color(0xFFFFB92E),
        shadeColor = Color(0xFFD99300),
        highlightColor = Color(0xFFFFE082)
    ),
    ZEFIR(
        id = "skin_zefir",
        title = "Зефир",
        iconEmoji = "🍬",
        baseColor = Color(0xFFFF9EBB),
        shadeColor = Color(0xFFD96B8C),
        highlightColor = Color(0xFFFFCCE0)
    ),
    FACE(
        id = "skin_face",
        title = "Рожица",
        iconEmoji = "🙂",
        baseColor = Color(0xFFFFE14D),
        shadeColor = Color(0xFFD9BB00),
        highlightColor = Color(0xFFFFF299)
    )
}

/**
 * Facial Expressions with hierarchical priorities.
 */
enum class HeroExpression(val priority: Int, val durationMs: Long = 0L) {
    IMPACT(priority = 10, durationMs = 500L),
    DIZZY(priority = 10, durationMs = 2000L),
    FLYING(priority = 8),
    SQUISHED(priority = 7),
    STRETCHED(priority = 7),
    GIGGLE(priority = 5, durationMs = 1500L),
    PROUD(priority = 4, durationMs = 1500L),
    HAPPY(priority = 3, durationMs = 1600L),
    SLEEPY(priority = 2),
    IDLE(priority = 1)
}

/**
 * Wardrobe Slots: Head, Face, Neck.
 */
enum class WardrobeSlot(val title: String, val emoji: String) {
    HEAD("Шапки", "🎩"),
    FACE("Лицо", "👓"),
    NECK("Шея", "🎀")
}

/**
 * Wardrobe Accessory Item.
 */
data class WardrobeItem(
    val id: String,
    val slot: WardrobeSlot,
    val name: String,
    val iconEmoji: String,
    val isDefaultUnlocked: Boolean = false
)

/**
 * Catalog of all 14 accessories and 3 background themes.
 */
object SquishCatalog {
    val ALL_ITEMS = listOf(
        // HEAD (Hats & Flowers)
        WardrobeItem("hat_party", WardrobeSlot.HEAD, "Колпачок", "🎉", isDefaultUnlocked = true),
        WardrobeItem("hat_crown", WardrobeSlot.HEAD, "Корона", "👑"),
        WardrobeItem("hat_chef", WardrobeSlot.HEAD, "Повар", "👨‍🍳"),
        WardrobeItem("hat_flower", WardrobeSlot.HEAD, "Цветочек", "🌸"),
        WardrobeItem("hat_cap", WardrobeSlot.HEAD, "Кепка", "🧢"),

        // FACE (Glasses & Mustache)
        WardrobeItem("face_sunglasses", WardrobeSlot.FACE, "Очки", "🕶️", isDefaultUnlocked = true),
        WardrobeItem("face_nerd_glasses", WardrobeSlot.FACE, "Умные очки", "👓"),
        WardrobeItem("face_mustache", WardrobeSlot.FACE, "Усы", "🥸"),
        WardrobeItem("face_star_mask", WardrobeSlot.FACE, "Звёздные очки", "⭐"),
        WardrobeItem("face_blush", WardrobeSlot.FACE, "Румянец", "💖"),

        // NECK (Bowties, Medals, Ribbons)
        WardrobeItem("neck_bowtie", WardrobeSlot.NECK, "Бабочка", "🎀", isDefaultUnlocked = true),
        WardrobeItem("neck_medal", WardrobeSlot.NECK, "Медаль", "🥇"),
        WardrobeItem("neck_bell", WardrobeSlot.NECK, "Бубенчик", "🔔"),
        WardrobeItem("neck_scarf", WardrobeSlot.NECK, "Шарфик", "🧣")
    )
}

/**
 * Particle for bounces and tap sparkles.
 */
data class SquishParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val isStar: Boolean = true,
    var size: Float = 12f,
    var alpha: Float = 1.0f,
    var lifeSec: Float = 0.5f,
    var ageSec: Float = 0f
)
