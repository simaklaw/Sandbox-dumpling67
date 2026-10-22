package com.example.audio

/**
 * Sound Category for routing, prioritization and parent settings.
 */
enum class SoundCategory {
    SFX,
    VOICE,
    ANIMAL
}

/**
 * Priority for missing sounds audit and production assets.
 */
enum class SoundPriority {
    P1_CRITICAL,
    P2_IMPORTANT,
    P3_DESIRABLE
}

/**
 * Definition of a single sound entry in the audio engine catalog.
 */
data class SoundEntry(
    val name: String,
    val category: SoundCategory,
    val files: List<String>,
    val loop: Boolean = false,
    val volume: Float = 1.0f,
    val minIntervalMs: Long = 0L,
    val maxConcurrent: Int = 3,
    val pitchJitter: Float = 0f,
    val group: String = "shared",
    val priority: SoundPriority = SoundPriority.P2_IMPORTANT,
    val description: String = ""
)

/**
 * Unified catalog of all sounds in "Детская площадка" according to Step 9 specifications.
 */
object SoundCatalog {

    val entries: List<SoundEntry> = listOf(
        // ==========================================
        // 🐾 4.1. ANIMAL SOUNDS (P1) - Real records, 2 variants each
        // ==========================================
        SoundEntry(
            name = "animal_dog",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_dog_1", "animal_dog_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Собака: гав-гав"
        ),
        SoundEntry(
            name = "animal_cat",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_cat_1", "animal_cat_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Котёнок: мяу"
        ),
        SoundEntry(
            name = "animal_cow",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_cow_1", "animal_cow_2"),
            volume = 1.0f,
            minIntervalMs = 500L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Корова: му-у"
        ),
        SoundEntry(
            name = "animal_horse",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_horse_1", "animal_horse_2"),
            volume = 1.0f,
            minIntervalMs = 500L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Лошадка: иго-го"
        ),
        SoundEntry(
            name = "animal_pig",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_pig_1", "animal_pig_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Свинка: хрю-хрю"
        ),
        SoundEntry(
            name = "animal_sheep",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_sheep_1", "animal_sheep_2"),
            volume = 0.95f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Барашек: бе-е"
        ),
        SoundEntry(
            name = "animal_goose",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_goose_1", "animal_goose_2"),
            volume = 0.95f,
            minIntervalMs = 350L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Гусь: га-га"
        ),
        SoundEntry(
            name = "animal_rooster",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_rooster_1", "animal_rooster_2"),
            volume = 1.0f,
            minIntervalMs = 500L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Петух: ку-ка-ре-ку"
        ),
        SoundEntry(
            name = "animal_frog",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_frog_1", "animal_frog_2"),
            volume = 0.9f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Лягушка: ква-ква"
        ),
        SoundEntry(
            name = "animal_crow",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_crow_1", "animal_crow_2"),
            volume = 0.9f,
            minIntervalMs = 350L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Ворона: кар-кар"
        ),
        SoundEntry(
            name = "animal_duck",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_duck_1", "animal_duck_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Уточка: кря-кря"
        ),
        SoundEntry(
            name = "animal_bunny",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_bunny_1", "animal_bunny_2"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Зайчик: фырканье / писк"
        ),
        SoundEntry(
            name = "animal_chick",
            category = SoundCategory.ANIMAL,
            files = listOf("chick_peep"),
            volume = 0.9f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Цыплёнок: пи-пи"
        ),
        SoundEntry(
            name = "animal_piglet",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_pig_2"),
            volume = 0.9f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Поросёнок: визг"
        ),
        SoundEntry(
            name = "animal_lamb",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_sheep_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Ягнёнок: бэ-э"
        ),
        SoundEntry(
            name = "animal_calf",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_cow_2"),
            volume = 0.95f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Телёнок: му-у"
        ),
        SoundEntry(
            name = "animal_dog_happy",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_dog_happy"),
            volume = 0.95f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Довольный щенок"
        ),
        SoundEntry(
            name = "animal_cat_happy",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_cat_happy"),
            volume = 0.95f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Мурлыканье кошечки"
        ),
        SoundEntry(
            name = "animal_bunny_happy",
            category = SoundCategory.ANIMAL,
            files = listOf("animal_bunny_happy"),
            volume = 0.9f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            group = "animals",
            priority = SoundPriority.P1_CRITICAL,
            description = "Довольный зайка"
        ),

        // ==========================================
        // 🗣️ 3. VOICES (P1)
        // ==========================================
        SoundEntry(
            name = "voice_arrow_normal",
            category = SoundCategory.VOICE,
            files = listOf("voice_arrow_normal"),
            volume = 1.0f,
            minIntervalMs = 400L,
            maxConcurrent = 1,
            group = "archery",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Стрела!"
        ),
        SoundEntry(
            name = "voice_arrow_carrot",
            category = SoundCategory.VOICE,
            files = listOf("voice_arrow_carrot"),
            volume = 1.0f,
            minIntervalMs = 400L,
            maxConcurrent = 1,
            group = "archery",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Морковка!"
        ),
        SoundEntry(
            name = "voice_arrow_star",
            category = SoundCategory.VOICE,
            files = listOf("voice_arrow_star"),
            volume = 1.0f,
            minIntervalMs = 400L,
            maxConcurrent = 1,
            group = "archery",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Звёздочка!"
        ),
        SoundEntry(
            name = "praise",
            category = SoundCategory.VOICE,
            files = listOf("praise_1", "praise_2", "praise_3"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Молодец! / Умничка! / Ура!"
        ),
        SoundEntry(
            name = "peekaboo",
            category = SoundCategory.VOICE,
            files = listOf("peekaboo_1", "peekaboo_2"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "busyboard",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Ку-ку! / А вот и я!"
        ),
        SoundEntry(
            name = "yum_voice",
            category = SoundCategory.VOICE,
            files = listOf("yum_voice"),
            volume = 1.0f,
            minIntervalMs = 500L,
            maxConcurrent = 1,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Голос: Ням-ням!"
        ),
        SoundEntry(
            name = "voice_hint_tap",
            category = SoundCategory.VOICE,
            files = listOf("voice_hint_tap"),
            volume = 1.0f,
            minIntervalMs = 800L,
            maxConcurrent = 1,
            group = "shared",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Нажми!"
        ),
        SoundEntry(
            name = "voice_hint_drag",
            category = SoundCategory.VOICE,
            files = listOf("voice_hint_drag"),
            volume = 1.0f,
            minIntervalMs = 800L,
            maxConcurrent = 1,
            group = "shared",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Потяни сюда!"
        ),
        SoundEntry(
            name = "voice_hint_look",
            category = SoundCategory.VOICE,
            files = listOf("voice_hint_look"),
            volume = 1.0f,
            minIntervalMs = 800L,
            maxConcurrent = 1,
            group = "shared",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Смотри!"
        ),
        SoundEntry(
            name = "voice_name_cow",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_cow"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Корова"
        ),
        SoundEntry(
            name = "voice_name_dog",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_dog"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Собака"
        ),
        SoundEntry(
            name = "voice_name_cat",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_cat"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Кошка"
        ),
        SoundEntry(
            name = "voice_name_duck",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_duck"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Утка"
        ),
        SoundEntry(
            name = "voice_name_pig",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_pig"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Свинья"
        ),
        SoundEntry(
            name = "voice_name_frog",
            category = SoundCategory.VOICE,
            files = listOf("voice_name_frog"),
            volume = 1.0f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            group = "animals",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Голос: Лягушка"
        ),
        SoundEntry(
            name = "lullaby_short",
            category = SoundCategory.SFX,
            files = listOf("lullaby_short"),
            volume = 0.7f,
            minIntervalMs = 2000L,
            maxConcurrent = 1,
            group = "shared",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Короткая тихая колыбельная"
        ),

        // ==========================================
        // 🔔 4.2. GENERAL & INTERFACE (P1)
        // ==========================================
        SoundEntry(
            name = "tap",
            category = SoundCategory.SFX,
            files = listOf("tap"),
            volume = 0.8f,
            minIntervalMs = 100L, // 10 per sec
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Мягкий клик кнопки"
        ),
        SoundEntry(
            name = "pop",
            category = SoundCategory.SFX,
            files = listOf("pop"),
            volume = 0.85f,
            minIntervalMs = 80L,
            maxConcurrent = 4,
            pitchJitter = 0.04f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Поп пузырька"
        ),
        SoundEntry(
            name = "whoosh",
            category = SoundCategory.SFX,
            files = listOf("whoosh"),
            volume = 0.8f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Воздушный вжух"
        ),
        SoundEntry(
            name = "whoosh_soft",
            category = SoundCategory.SFX,
            files = listOf("whoosh_soft"),
            volume = 0.7f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Мягкий вжух переходов"
        ),
        SoundEntry(
            name = "chime",
            category = SoundCategory.SFX,
            files = listOf("chime"),
            volume = 0.9f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.02f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Колокольчик успеха"
        ),
        SoundEntry(
            name = "fanfare_short",
            category = SoundCategory.SFX,
            files = listOf("fanfare_short"),
            volume = 1.0f,
            minIntervalMs = 800L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Радостная короткая мелодия фанфар"
        ),
        SoundEntry(
            name = "soft_no",
            category = SoundCategory.SFX,
            files = listOf("soft_no"),
            volume = 0.75f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.02f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Мягкое отстранение / отказ"
        ),
        SoundEntry(
            name = "spawn",
            category = SoundCategory.SFX,
            files = listOf("spawn"),
            volume = 0.8f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "shared",
            priority = SoundPriority.P1_CRITICAL,
            description = "Пуф появления предмета"
        ),
        SoundEntry(
            name = "open",
            category = SoundCategory.SFX,
            files = listOf("open"),
            volume = 0.8f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Рот открылся"
        ),

        // ==========================================
        // 🥕 4.3. FEEDING (P1)
        // ==========================================
        SoundEntry(
            name = "pickup",
            category = SoundCategory.SFX,
            files = listOf("pickup"),
            volume = 0.85f,
            minIntervalMs = 120L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Чпок - взяли еду"
        ),
        SoundEntry(
            name = "eat_in",
            category = SoundCategory.SFX,
            files = listOf("eat_in"),
            volume = 0.9f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Еда ушла в рот"
        ),
        SoundEntry(
            name = "chew",
            category = SoundCategory.SFX,
            files = listOf("chew_1", "chew_2"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Жевание"
        ),
        SoundEntry(
            name = "yum",
            category = SoundCategory.SFX,
            files = listOf("yum"),
            volume = 0.95f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            pitchJitter = 0.02f,
            group = "feeding",
            priority = SoundPriority.P1_CRITICAL,
            description = "Довольное ммм"
        ),

        // ==========================================
        // 🎛️ 4.4. BUSYBOARD (P2)
        // ==========================================
        SoundEntry(
            name = "lever_clack",
            category = SoundCategory.SFX,
            files = listOf("lever_clack"),
            volume = 0.85f,
            minIntervalMs = 150L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Щелчок рычага"
        ),
        SoundEntry(
            name = "lever_hum",
            category = SoundCategory.SFX,
            files = listOf("lever_hum_loop"),
            loop = true,
            volume = 0.75f,
            minIntervalMs = 0L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Гудение генератора/моторчика"
        ),
        SoundEntry(
            name = "confetti_rain",
            category = SoundCategory.SFX,
            files = listOf("confetti_rain"),
            volume = 0.9f,
            minIntervalMs = 500L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Хлопушка конфетти"
        ),
        SoundEntry(
            name = "train_toot",
            category = SoundCategory.SFX,
            files = listOf("train_toot"),
            volume = 0.9f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            pitchJitter = 0.02f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Гудок паровозика"
        ),
        SoundEntry(
            name = "star_burst",
            category = SoundCategory.SFX,
            files = listOf("star_burst"),
            volume = 0.85f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Магические искры"
        ),
        SoundEntry(
            name = "quack",
            category = SoundCategory.SFX,
            files = listOf("quack_1", "quack_2", "quack_3"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Мультяшное кряканье"
        ),
        SoundEntry(
            name = "frog_boing",
            category = SoundCategory.SFX,
            files = listOf("frog_boing"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Пружинка лягушонка"
        ),
        SoundEntry(
            name = "bubble",
            category = SoundCategory.SFX,
            files = listOf("bubble"),
            volume = 0.8f,
            minIntervalMs = 100L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Бульк пузырька"
        ),
        SoundEntry(
            name = "firework",
            category = SoundCategory.SFX,
            files = listOf("firework"),
            volume = 0.9f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Мягкий салют"
        ),
        SoundEntry(
            name = "switch_on",
            category = SoundCategory.SFX,
            files = listOf("switch_on"),
            volume = 0.8f,
            minIntervalMs = 100L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Включение тумблера"
        ),
        SoundEntry(
            name = "switch_off",
            category = SoundCategory.SFX,
            files = listOf("switch_off"),
            volume = 0.75f,
            minIntervalMs = 100L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Выключение тумблера"
        ),
        SoundEntry(
            name = "jingle_lights",
            category = SoundCategory.SFX,
            files = listOf("jingle_lights"),
            volume = 0.85f,
            minIntervalMs = 500L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Перезвон огоньков гирлянды"
        ),
        SoundEntry(
            name = "knob_tick",
            category = SoundCategory.SFX,
            files = listOf("knob_tick"),
            volume = 0.7f,
            minIntervalMs = 50L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Щелчок крутилки"
        ),
        SoundEntry(
            name = "door_creak",
            category = SoundCategory.SFX,
            files = listOf("door_creak"),
            volume = 0.8f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Скрип дверцы домика"
        ),
        SoundEntry(
            name = "door_close",
            category = SoundCategory.SFX,
            files = listOf("door_close"),
            volume = 0.8f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Закрытие дверцы"
        ),
        SoundEntry(
            name = "bell_ding",
            category = SoundCategory.SFX,
            files = listOf("bell_ding"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "busyboard",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Звоночек колокольчика"
        ),

        // ==========================================
        // 🟣 4.5. SQUISH / МЯЛКА (P2)
        // ==========================================
        SoundEntry(
            name = "squish",
            category = SoundCategory.SFX,
            files = listOf("squish_1", "squish_2", "squish_3"),
            volume = 0.85f,
            minIntervalMs = 160L, // 6 per sec
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Сжатие слайма/мялки"
        ),
        SoundEntry(
            name = "stretch",
            category = SoundCategory.SFX,
            files = listOf("stretch"),
            volume = 0.8f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Растяжение резинки"
        ),
        SoundEntry(
            name = "release_boing",
            category = SoundCategory.SFX,
            files = listOf("release_boing"),
            volume = 0.85f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Отпускание с пружинкой"
        ),
        SoundEntry(
            name = "fly",
            category = SoundCategory.SFX,
            files = listOf("fly"),
            volume = 0.85f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Полёт героя уиии"
        ),
        SoundEntry(
            name = "bounce",
            category = SoundCategory.SFX,
            files = listOf("bounce"),
            volume = 0.8f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Отскок от стены"
        ),
        SoundEntry(
            name = "poke",
            category = SoundCategory.SFX,
            files = listOf("poke"),
            volume = 0.8f,
            minIntervalMs = 120L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Тычок пальчиком"
        ),
        SoundEntry(
            name = "giggle",
            category = SoundCategory.SFX,
            files = listOf("giggle"),
            volume = 0.9f,
            minIntervalMs = 500L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Детский добрый смех"
        ),
        SoundEntry(
            name = "dizzy",
            category = SoundCategory.SFX,
            files = listOf("dizzy"),
            volume = 0.85f,
            minIntervalMs = 500L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Головокружение мультяшное"
        ),
        SoundEntry(
            name = "yawn",
            category = SoundCategory.SFX,
            files = listOf("yawn"),
            volume = 0.85f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Зевание"
        ),
        SoundEntry(
            name = "wear",
            category = SoundCategory.SFX,
            files = listOf("wear"),
            volume = 0.85f,
            minIntervalMs = 250L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Надевание аксессуара"
        ),
        SoundEntry(
            name = "gift",
            category = SoundCategory.SFX,
            files = listOf("gift"),
            volume = 0.95f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "squish",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Открытие подарка"
        ),

        // ==========================================
        // 🏹 4.6. ARCHERY, PINBALL, TEA (P2)
        // ==========================================
        SoundEntry(
            name = "bow_pull",
            category = SoundCategory.SFX,
            files = listOf("bow_pull_loop"),
            loop = true,
            volume = 0.75f,
            minIntervalMs = 0L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Натяжение тетивы"
        ),
        SoundEntry(
            name = "shoot",
            category = SoundCategory.SFX,
            files = listOf("shoot"),
            volume = 0.9f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Выстрел из лука"
        ),
        SoundEntry(
            name = "arrow_hit",
            category = SoundCategory.SFX,
            files = listOf("arrow_hit"),
            volume = 0.85f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Попадание стрелы"
        ),
        SoundEntry(
            name = "carrot_hit",
            category = SoundCategory.SFX,
            files = listOf("carrot_hit"),
            volume = 0.9f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Хруст морковки"
        ),
        SoundEntry(
            name = "star_hit",
            category = SoundCategory.SFX,
            files = listOf("star_hit"),
            volume = 0.9f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.03f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Звёздный звон попадания"
        ),
        SoundEntry(
            name = "scarecrow_giggle",
            category = SoundCategory.SFX,
            files = listOf("scarecrow_giggle"),
            volume = 0.9f,
            minIntervalMs = 400L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Хихиканье пугала"
        ),
        SoundEntry(
            name = "wobble",
            category = SoundCategory.SFX,
            files = listOf("wobble"),
            volume = 0.8f,
            minIntervalMs = 200L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Колебание древка"
        ),
        SoundEntry(
            name = "thud",
            category = SoundCategory.SFX,
            files = listOf("thud"),
            volume = 0.8f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "archery",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Мягкий глухой стук"
        ),
        SoundEntry(
            name = "bumper",
            category = SoundCategory.SFX,
            files = listOf("bumper"),
            volume = 0.9f,
            minIntervalMs = 120L,
            maxConcurrent = 4,
            pitchJitter = 0.04f,
            group = "pinball",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Бампер пинбола"
        ),
        SoundEntry(
            name = "sling",
            category = SoundCategory.SFX,
            files = listOf("sling"),
            volume = 0.85f,
            minIntervalMs = 120L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "pinball",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Слингшот пинбола"
        ),
        SoundEntry(
            name = "flip",
            category = SoundCategory.SFX,
            files = listOf("flip"),
            volume = 0.7f,
            minIntervalMs = 125L, // 8 per sec max
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "pinball",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Флиппер пинбола"
        ),
        SoundEntry(
            name = "wall",
            category = SoundCategory.SFX,
            files = listOf("wall"),
            volume = 0.65f,
            minIntervalMs = 200L, // 5 per sec max
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "pinball",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Стенка пинбола"
        ),
        SoundEntry(
            name = "blub",
            category = SoundCategory.SFX,
            files = listOf("blub"),
            volume = 0.8f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Бульканье чая"
        ),
        SoundEntry(
            name = "splash_small",
            category = SoundCategory.SFX,
            files = listOf("splash_small"),
            volume = 0.8f,
            minIntervalMs = 180L,
            maxConcurrent = 2,
            pitchJitter = 0.04f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Маленький всплеск"
        ),
        SoundEntry(
            name = "stir",
            category = SoundCategory.SFX,
            files = listOf("stir"),
            volume = 0.85f,
            minIntervalMs = 300L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Помешивание ложечкой"
        ),
        SoundEntry(
            name = "steam",
            category = SoundCategory.SFX,
            files = listOf("steam"),
            volume = 0.8f,
            minIntervalMs = 400L,
            maxConcurrent = 2,
            pitchJitter = 0.02f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Шипение пара"
        ),
        SoundEntry(
            name = "glug",
            category = SoundCategory.SFX,
            files = listOf("glug"),
            volume = 0.85f,
            minIntervalMs = 350L,
            maxConcurrent = 2,
            pitchJitter = 0.03f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Глотки чая"
        ),
        SoundEntry(
            name = "clink",
            category = SoundCategory.SFX,
            files = listOf("clink"),
            volume = 0.8f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "tea",
            priority = SoundPriority.P2_IMPORTANT,
            description = "Звяканье чашки"
        ),

        // ==========================================
        // 🎨 4.7. DRAWING, GRAINS, BALLOONS (P3)
        // ==========================================
        SoundEntry(
            name = "draw_loop",
            category = SoundCategory.SFX,
            files = listOf("draw_loop"),
            loop = true,
            volume = 0.75f,
            minIntervalMs = 0L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "drawing",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Шуршание фломастера"
        ),
        SoundEntry(
            name = "grain_pour",
            category = SoundCategory.SFX,
            files = listOf("grain_pour_loop"),
            loop = true,
            volume = 0.75f,
            minIntervalMs = 0L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "grains",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Пересыпание крупы"
        ),
        SoundEntry(
            name = "grain_scoop",
            category = SoundCategory.SFX,
            files = listOf("grain_scoop_loop"),
            loop = true,
            volume = 0.75f,
            minIntervalMs = 0L,
            maxConcurrent = 1,
            pitchJitter = 0f,
            group = "grains",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Черпание крупы совком"
        ),
        SoundEntry(
            name = "stamp",
            category = SoundCategory.SFX,
            files = listOf("stamp"),
            volume = 0.85f,
            minIntervalMs = 150L,
            maxConcurrent = 3,
            pitchJitter = 0.04f,
            group = "drawing",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Штампик"
        ),
        SoundEntry(
            name = "balloon_pop",
            category = SoundCategory.SFX,
            files = listOf("balloon_pop_1", "balloon_pop_2", "balloon_pop_3"),
            volume = 0.9f,
            minIntervalMs = 100L,
            maxConcurrent = 4,
            pitchJitter = 0.04f,
            group = "balloons",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Лопнувший шарик"
        ),
        SoundEntry(
            name = "rainbow_salute",
            category = SoundCategory.SFX,
            files = listOf("rainbow_salute"),
            volume = 0.95f,
            minIntervalMs = 600L,
            maxConcurrent = 1,
            pitchJitter = 0.02f,
            group = "shared",
            priority = SoundPriority.P3_DESIRABLE,
            description = "Радужный салют"
        ),
        // ==========================================
        // 🎨 DECOR SOUNDS & RAINBOW BUTTONS
        // ==========================================
        SoundEntry(name = "decor_ball", category = SoundCategory.SFX, files = listOf("decor_ball"), volume = 0.9f, description = "Мячик прыг"),
        SoundEntry(name = "decor_yarn", category = SoundCategory.SFX, files = listOf("whoosh_soft"), volume = 0.8f, description = "Клубок катится"),
        SoundEntry(name = "decor_kite", category = SoundCategory.SFX, files = listOf("whoosh_soft"), volume = 0.85f, description = "Воздушный змей"),
        SoundEntry(name = "decor_rainbow", category = SoundCategory.SFX, files = listOf("star_burst", "chime"), volume = 0.95f, description = "Радуга сияет"),
        SoundEntry(name = "decor_balloon_fly", category = SoundCategory.SFX, files = listOf("balloon_pop_1", "chime"), volume = 0.85f, description = "Шар взлетает"),
        SoundEntry(name = "decor_plant", category = SoundCategory.SFX, files = listOf("pop", "chime"), volume = 0.9f, description = "Цветок выпрыгивает"),
        SoundEntry(name = "decor_crayon", category = SoundCategory.SFX, files = listOf("star_burst", "chime"), volume = 0.95f, description = "Карандаш рисует"),
        SoundEntry(name = "decor_pumpkin", category = SoundCategory.SFX, files = listOf("decor_pumpkin"), volume = 0.9f, description = "Тыква подпрыгивает"),
        SoundEntry(name = "decor_sunflower", category = SoundCategory.SFX, files = listOf("decor_sunflower"), volume = 0.9f, description = "Подсолнух кивает"),
        SoundEntry(name = "decor_gear", category = SoundCategory.SFX, files = listOf("decor_gear"), volume = 0.85f, description = "Шестерёнка ускоряется"),
        SoundEntry(name = "decor_guest", category = SoundCategory.SFX, files = listOf("decor_guest"), volume = 0.9f, description = "Гость хлопает"),
        SoundEntry(name = "decor_teapot", category = SoundCategory.SFX, files = listOf("decor_teapot"), volume = 0.9f, description = "Чайник пыхтит"),
        SoundEntry(name = "decor_cake", category = SoundCategory.SFX, files = listOf("decor_cake"), volume = 0.9f, description = "Торт мерцает"),
        SoundEntry(name = "decor_mouse", category = SoundCategory.SFX, files = listOf("decor_mouse"), volume = 0.9f, description = "Мышка пищит"),
        SoundEntry(name = "decor_bulb", category = SoundCategory.SFX, files = listOf("decor_bulb"), volume = 0.85f, description = "Лампа вспыхивает"),
        // ===== Шаг 14: звуки, которые раньше играли по имени файла в обход каталога =====
        SoundEntry(name = "drop_plip", category = SoundCategory.SFX, files = listOf("drop_plip_1", "drop_plip_2", "drop_plip_3"), volume = 0.8f, minIntervalMs = 70L, maxConcurrent = 4, pitchJitter = 0.05f, group = "step14", description = "Капля дождя"),
        SoundEntry(name = "splash_soft", category = SoundCategory.SFX, files = listOf("splash_soft"), volume = 0.9f, minIntervalMs = 120L, maxConcurrent = 3, pitchJitter = 0.05f, group = "step14", description = "Всплеск в луже"),
        SoundEntry(name = "box_shake", category = SoundCategory.SFX, files = listOf("box_shake"), volume = 0.9f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Коробка трясётся"),
        SoundEntry(name = "box_pop", category = SoundCategory.SFX, files = listOf("box_pop"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Крышка коробки слетает"),
        SoundEntry(name = "snap_in", category = SoundCategory.SFX, files = listOf("snap_in"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Фигурка встала на место"),
        SoundEntry(name = "voice_shape_circle", category = SoundCategory.VOICE, files = listOf("voice_shape_circle"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры circle"),
        SoundEntry(name = "voice_shape_square", category = SoundCategory.VOICE, files = listOf("voice_shape_square"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры square"),
        SoundEntry(name = "voice_shape_triangle", category = SoundCategory.VOICE, files = listOf("voice_shape_triangle"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры triangle"),
        SoundEntry(name = "voice_shape_star", category = SoundCategory.VOICE, files = listOf("voice_shape_star"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры star"),
        SoundEntry(name = "voice_shape_heart", category = SoundCategory.VOICE, files = listOf("voice_shape_heart"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры heart"),
        SoundEntry(name = "voice_shape_oval", category = SoundCategory.VOICE, files = listOf("voice_shape_oval"), volume = 1.0f, minIntervalMs = 0L, maxConcurrent = 1, pitchJitter = 0.0f, group = "step14", description = "Голос: название фигуры oval"),
        SoundEntry(name = "elephant_toot", category = SoundCategory.ANIMAL, files = listOf("elephant_toot"), volume = 0.95f, minIntervalMs = 300L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Слонёнок трубит"),
        SoundEntry(name = "chick_peep", category = SoundCategory.ANIMAL, files = listOf("chick_peep"), volume = 0.95f, minIntervalMs = 300L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Цыплёнок пищит"),
        SoundEntry(name = "dino_soft", category = SoundCategory.ANIMAL, files = listOf("dino_soft"), volume = 0.95f, minIntervalMs = 300L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Динозаврик"),
        SoundEntry(name = "robot_beep", category = SoundCategory.ANIMAL, files = listOf("robot_beep"), volume = 0.95f, minIntervalMs = 300L, maxConcurrent = 2, pitchJitter = 0.0f, group = "step14", description = "Робот пикает"),
        SoundEntry(name = "decor_sun", category = SoundCategory.SFX, files = listOf("decor_sun"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Солнышко"),
        SoundEntry(name = "decor_butterfly", category = SoundCategory.SFX, files = listOf("decor_butterfly"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Бабочка"),
        SoundEntry(name = "decor_bird", category = SoundCategory.SFX, files = listOf("decor_bird"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Птичка"),
        SoundEntry(name = "decor_star", category = SoundCategory.SFX, files = listOf("decor_star"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Звёздочка"),
        SoundEntry(name = "rainbow_chime", category = SoundCategory.SFX, files = listOf("rainbow_chime"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Радуга"),
        SoundEntry(name = "rain_loop", category = SoundCategory.SFX, files = listOf("rain_loop"), volume = 0.85f, minIntervalMs = 250L, maxConcurrent = 2, pitchJitter = 0.03f, group = "step14", description = "Шум дождя"),
        SoundEntry(name = "rb_red", category = SoundCategory.SFX, files = listOf("rb_red"), volume = 0.95f, description = "Красный цвет"),
        SoundEntry(name = "rb_orange", category = SoundCategory.SFX, files = listOf("rb_orange"), volume = 0.95f, description = "Оранжевый цвет"),
        SoundEntry(name = "rb_yellow", category = SoundCategory.SFX, files = listOf("rb_yellow"), volume = 0.95f, description = "Жёлтый цвет"),
        SoundEntry(name = "rb_green", category = SoundCategory.SFX, files = listOf("rb_green"), volume = 0.95f, description = "Зелёный цвет"),
        SoundEntry(name = "rb_blue", category = SoundCategory.SFX, files = listOf("rb_blue"), volume = 0.95f, description = "Голубой цвет"),
        SoundEntry(name = "rb_purple", category = SoundCategory.SFX, files = listOf("rb_purple"), volume = 0.95f, description = "Фиолетовый цвет"),

        // ==========================================
        // 🚀 4.8. CATAPULT / КАТАПУЛЬТА (ШАГ 13)
        // ==========================================
        SoundEntry(name = "sling_pull_loop", category = SoundCategory.SFX, files = listOf("sling_pull_loop", "stretch"), loop = true, volume = 0.75f, description = "Скрип резинки натяжения"),
        SoundEntry(name = "sling_fire", category = SoundCategory.SFX, files = listOf("sling_fire", "shoot"), volume = 0.95f, minIntervalMs = 200L, description = "Запуск из рогатки"),
        SoundEntry(name = "parachute_open", category = SoundCategory.SFX, files = listOf("parachute_open", "whoosh"), volume = 0.9f, minIntervalMs = 300L, description = "Раскрытие парашюта"),
        SoundEntry(name = "land_bounce", category = SoundCategory.SFX, files = listOf("land_bounce", "bounce"), volume = 0.85f, minIntervalMs = 150L, description = "Приземление и отскок"),
        SoundEntry(name = "star_collect", category = SoundCategory.SFX, files = listOf("star_collect", "chime"), volume = 0.9f, minIntervalMs = 100L, description = "Сбор звёздочки"),
        SoundEntry(name = "cloud_bounce", category = SoundCategory.SFX, files = listOf("cloud_bounce", "frog_boing"), volume = 0.9f, minIntervalMs = 200L, description = "Облако-батут"),
        SoundEntry(name = "balloon_boost", category = SoundCategory.SFX, files = listOf("balloon_boost", "pop"), volume = 0.85f, minIntervalMs = 200L, description = "Связка шариков"),
        SoundEntry(name = "bird_tweet", category = SoundCategory.SFX, files = listOf("bird_tweet", "chick_peep"), volume = 0.85f, minIntervalMs = 250L, description = "Птичка чирикает"),
        SoundEntry(name = "kite_swoosh", category = SoundCategory.SFX, files = listOf("kite_swoosh", "whoosh"), volume = 0.85f, minIntervalMs = 300L, description = "Воздушный змей"),
        SoundEntry(name = "rainbow_slide", category = SoundCategory.SFX, files = listOf("rainbow_slide", "chime"), volume = 0.9f, minIntervalMs = 400L, description = "Горка-радуга"),
        SoundEntry(name = "ufo_hello", category = SoundCategory.SFX, files = listOf("ufo_hello", "robot_beep"), volume = 0.9f, minIntervalMs = 400L, description = "НЛО сигнал"),
        SoundEntry(name = "wind_loop", category = SoundCategory.SFX, files = listOf("wind_loop"), loop = true, volume = 0.7f, description = "Ветер в полёте"),

        // ==========================================
        // 🏃‍♂️ 4.9. RUNNER GAME / РАННЕР ПЕЛЬМЕНЬ-СИКС
        // ==========================================
        SoundEntry(name = "runner_jump", category = SoundCategory.SFX, files = listOf("release_boing", "frog_boing", "bounce"), volume = 0.95f, minIntervalMs = 80L, group = "runner", description = "Прыжок Сикса"),
        SoundEntry(name = "runner_double_jump", category = SoundCategory.SFX, files = listOf("bubble", "whoosh_soft"), volume = 0.95f, minIntervalMs = 100L, group = "runner", description = "Сикс-кульбит"),
        SoundEntry(name = "runner_hit_squish", category = SoundCategory.SFX, files = listOf("squish_1", "squish_2", "squish_3"), volume = 0.95f, minIntervalMs = 120L, group = "runner", description = "Шмяк о препятствие"),
        SoundEntry(name = "runner_hit_thud", category = SoundCategory.SFX, files = listOf("thud", "carrot_hit"), volume = 0.9f, minIntervalMs = 120L, group = "runner", description = "Удар о препятствие"),
        SoundEntry(name = "runner_collect_coin", category = SoundCategory.SFX, files = listOf("star_collect", "pickup", "chime"), volume = 0.95f, minIntervalMs = 60L, group = "runner", description = "Сбор звёздочек и монеток"),
        SoundEntry(name = "runner_collect_dumpling", category = SoundCategory.SFX, files = listOf("star_burst", "rainbow_chime", "yum_voice"), volume = 1.0f, minIntervalMs = 150L, group = "runner", description = "Сбор Золотого Пельменя"),
        SoundEntry(name = "runner_speed_boost", category = SoundCategory.SFX, files = listOf("whoosh", "fanfare_short", "firework"), volume = 1.0f, minIntervalMs = 300L, group = "runner", description = "Турбо Спид 67"),
        SoundEntry(name = "runner_trampoline", category = SoundCategory.SFX, files = listOf("cloud_bounce", "frog_boing", "bumper"), volume = 1.0f, minIntervalMs = 150L, group = "runner", description = "Батут-грибочек"),
        SoundEntry(name = "runner_bumper", category = SoundCategory.SFX, files = listOf("bumper", "wobble"), volume = 0.95f, minIntervalMs = 120L, group = "runner", description = "Бампер отскок")
    )

    private val entryMap: Map<String, SoundEntry> by lazy {
        entries.associateBy { it.name.lowercase() }
    }

    /**
     * Finds sound definition by logical name.
     */
    fun find(name: String): SoundEntry? {
        val key = name.lowercase().trim()
        entryMap[key]?.let { return it }

        // Mappings for older aliases / synonyms from steps 1-8
        return when (key) {
            "click" -> entryMap["tap"]
            "transition" -> entryMap["whoosh_soft"]
            "boing" -> entryMap["release_boing"]
            "squeak" -> entryMap["poke"]
            "clunk" -> entryMap["lever_clack"]
            "bell" -> entryMap["bell_ding"]
            "splash" -> entryMap["splash_small"]
            "success" -> entryMap["chime"]
            "sparkle", "magic" -> entryMap["star_burst"]
            "refuse" -> entryMap["soft_no"]
            "balloon" -> entryMap["balloon_pop"]
            else -> null
        }
    }
}
