package com.example.model

enum class AccessoryCategory(val title: String, val emoji: String) {
    HAT("Шляпки", "🎩"),
    GLASSES("Очки", "👓"),
    BODY("Украшения", "🎀")
}

data class PelmenAccessory(
    val id: String,
    val name: String,
    val category: AccessoryCategory,
    val emoji: String,
    val description: String,
    val unlockRequirement: String,
    val coinPrice: Int,
    val isUnlockedByDefault: Boolean = false
)

object PelmenAccessoryCatalog {
    val ALL_ACCESSORIES = listOf(
        // HATS
        PelmenAccessory(
            id = "hat_none",
            name = "Без шляпы",
            category = AccessoryCategory.HAT,
            emoji = "❌",
            description = "Естественный вид Пельмень-Сикса",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "hat_party",
            name = "Колпак 67",
            category = AccessoryCategory.HAT,
            emoji = "🎉",
            description = "Для праздничного настроения и веселья!",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "hat_chef",
            name = "Поварской Колпак",
            category = AccessoryCategory.HAT,
            emoji = "👨‍🍳",
            description = "Шеф-повар лучшей кухни вселенной!",
            unlockRequirement = "Покормите питомцев 3 раза",
            coinPrice = 30
        ),
        PelmenAccessory(
            id = "hat_crown",
            name = "Золотая Корона",
            category = AccessoryCategory.HAT,
            emoji = "👑",
            description = "Корона истинного Владыки 67!",
            unlockRequirement = "Заработайте 150 монет",
            coinPrice = 100
        ),
        PelmenAccessory(
            id = "hat_tophat",
            name = "Цилиндр Джентльмена",
            category = AccessoryCategory.HAT,
            emoji = "🎩",
            description = "Элегантный стиль для богатого пельмешка",
            unlockRequirement = "Откройте 1 Блайнд-Бокс",
            coinPrice = 60
        ),
        PelmenAccessory(
            id = "hat_viking",
            name = "Шлем Викинга",
            category = AccessoryCategory.HAT,
            emoji = "🪖",
            description = "Бесстрашный воин северных бульонов",
            unlockRequirement = "Пожамкайте сквиш 10 раз",
            coinPrice = 50
        ),
        PelmenAccessory(
            id = "hat_santa",
            name = "Новогодний Колпак",
            category = AccessoryCategory.HAT,
            emoji = "🎅",
            description = "Приносит праздничное настроение и подарки",
            unlockRequirement = "Заберите дневную награду",
            coinPrice = 80
        ),

        // GLASSES
        PelmenAccessory(
            id = "glasses_none",
            name = "Без очков",
            category = AccessoryCategory.GLASSES,
            emoji = "❌",
            description = "Ясный взор без очков",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "glasses_cool",
            name = "Крутые Очки 67",
            category = AccessoryCategory.GLASSES,
            emoji = "🕶️",
            description = "Самый крутой Пельмень в Огайо!",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "glasses_star",
            name = "Звёздные Очки",
            category = AccessoryCategory.GLASSES,
            emoji = "🤩",
            description = "Сияют ярче кухонных звезд",
            unlockRequirement = "Пожамкайте сквиш 5 раз",
            coinPrice = 25
        ),
        PelmenAccessory(
            id = "glasses_smart",
            name = "Очки Учёного",
            category = AccessoryCategory.GLASSES,
            emoji = "👓",
            description = "Знает формулу идеального бульона",
            unlockRequirement = "Заработайте 80 монет",
            coinPrice = 40
        ),
        PelmenAccessory(
            id = "glasses_3d",
            name = "3D Очки Киномана",
            category = AccessoryCategory.GLASSES,
            emoji = "🥽",
            description = "Для просмотра мультиков в 3D",
            unlockRequirement = "Откройте 2 Блайнд-Бокса",
            coinPrice = 70
        ),
        PelmenAccessory(
            id = "glasses_heart",
            name = "Очки-Сердечки",
            category = AccessoryCategory.GLASSES,
            emoji = "😍",
            description = "Полон любви и умиления",
            unlockRequirement = "Покормите питомцев",
            coinPrice = 35
        ),

        // BODY
        PelmenAccessory(
            id = "body_none",
            name = "Без украшений",
            category = AccessoryCategory.BODY,
            emoji = "❌",
            description = "Без нагрудных аксессуаров",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "body_bow",
            name = "Праздничный Бантик",
            category = AccessoryCategory.BODY,
            emoji = "🎀",
            description = "Милый розовый бантик на шею",
            unlockRequirement = "Доступно сразу",
            coinPrice = 0,
            isUnlockedByDefault = true
        ),
        PelmenAccessory(
            id = "body_medal",
            name = "Медаль Чемпиона 67",
            category = AccessoryCategory.BODY,
            emoji = "🏅",
            description = "Награда за выдающиеся успехи",
            unlockRequirement = "Заработайте 200 монет",
            coinPrice = 120
        ),
        PelmenAccessory(
            id = "body_scarf",
            name = "Тёплый Шарфик",
            category = AccessoryCategory.BODY,
            emoji = "🧣",
            description = "Согревает в зимние вечера",
            unlockRequirement = "Заберите дневную награду",
            coinPrice = 45
        ),
        PelmenAccessory(
            id = "body_cape",
            name = "Плащ Супергероя",
            category = AccessoryCategory.BODY,
            emoji = "🦸",
            description = "Развевается при супер-прыжках!",
            unlockRequirement = "Откройте 3 Блайнд-Бокса",
            coinPrice = 150
        )
    )

    fun getById(id: String): PelmenAccessory? {
        return ALL_ACCESSORIES.find { it.id == id }
    }

    fun getByCategory(category: AccessoryCategory): List<PelmenAccessory> {
        return ALL_ACCESSORIES.filter { it.category == category }
    }
}
