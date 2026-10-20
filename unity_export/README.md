# Pelmen Game — Unity Architecture & GitHub Transfer Package

Этот репозиторий содержит полную архитектурную модель и готовые исходные файлы C# / Unity Assets для переноса проекта в **Unity (URP Mobile)** с поддержкой **ECS (DOTS)**, **Addressables**, **Firebase** и нативного **Android Mobile API**.

---

## 🏛 1. Полная схема архитектуры проекта Unity

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   PELMEN UNITY ARCHITECTURE (URP MOBILE)                        │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

 ┌──────────────────────────────────┐               ┌───────────────────────────────────────────┐
 │       INPUT ACTIONS & TOUCH      │               │           ECS PIPELINE (DOTS/BURST)       │
 │   (PelmenInputActions / Manager) │               │   High-performance zero-GC Game Loop      │
 ├──────────────────────────────────┤               ├───────────────────────────────────────────┤
 │ • Runner: Swipe L/R (Lanes)      │ ──[Events]──> │ • PelmenMoveData & RunnerMovementSystem   │
 │ • Runner: Swipe Up (Jump)        │               │   (Lane interpolation, Jump/Gravity/Slam) │
 │ • Runner: Swipe Down (Air Slam)  │               │ • SquishDeformation & DeformationSystem   │
 │ • Squish: 1-Finger Press / Delta │               │   (Spring-Damper mass, volume conserve)   │
 │ • Squish: 2-Finger Pinch / Twist │               │ • HapticRequestBufferElement (IJobEntity) │
 └──────────────────────────────────┘               └─────────────────────┬─────────────────────┘
                                                                          │
                                                                 [Haptic Events]
                                                                          │
                                                                          ▼
 ┌──────────────────────────────────┐               ┌───────────────────────────────────────────┐
 │     ADDRESSABLES ASSET PIPELINE  │               │       NATIVE MOBILE API / JNI BRIDGE      │
 │  (Streaming on Demand & Cache)   │               │            (AndroidMobileBridge)          │
 ├──────────────────────────────────┤               ├───────────────────────────────────────────┤
 │ • SectorSceneLoader:             │               │ • Android JNI: android.os.Vibrator        │
 │   Dynamic track chunk loading &  │               │ • VibrationEffect (EFFECT_CLICK, HEAVY,   │
 │   automatic memory release       │               │   TICK, DOUBLE_CLICK for blind-box crack) │
 │ • PelmenCatalogAddressables:     │               │ • Fallback: Handheld.Vibrate()            │
 │   67 Pelmen 3D Prefabs/Materials │               │ • 120Hz/90Hz Display Refresh Rate unlock  │
 │   (Variant_01 .. Variant_67)     │               │ • Display Cutout & Safe Area handling     │
 └──────────────────────────────────┘               └───────────────────────────────────────────┘
                                                                          │
 ┌────────────────────────────────────────────────────────────────────────┴─────────────────────┐
 │                           FIREBASE BACKEND (FirebaseBackendManager)                          │
 ├──────────────────────────────────────────────────────────────────────────────────────────────┤
 │ • Firebase Auth (Anonymous & Google Sign-In)                                                 │
 │ • Cloud Firestore: Sync 67 unlocked Pelmen IDs, Pelmen Coins, Leaderboards, Blind Box stats  │
 │ • Anti-cheat Cloud Functions: Deterministic Blind Box Drop RNG & reward verification        │
 │ • Offline persistence with local fallback (PlayerPrefs / JSON)                               │
 └──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 2. Содержимое готовых файлов (Assets/)

Все файлы уже созданы в папке `unity_export/Assets/`:

1. **`Assets/Input/PelmenInputActions.inputactions`**: Настроенный ассет New Input System для свайпов, прыжков, мультитача и сквиш-мялки.
2. **`Assets/Scripts/Input/PelmenInputManager.cs`**: Обработчик жестов (1-пальцевые свайпы, 2-пальцевый Pinch & Twist, нажим Touch Pressure).
3. **`Assets/Scripts/ECS/Components/PelmenComponents.cs`**: Компоненты ECS (`PelmenRunnerTag`, `PelmenMoveData`, `SquishDeformation`, `PelmenVariantData`, `HapticRequestBufferElement`).
4. **`Assets/Scripts/ECS/Systems/RunnerMovementSystem.cs`**: Burst-оптимизированная физика бега, смены полос, прыжков и слэма.
5. **`Assets/Scripts/ECS/Systems/SquishDeformationSystem.cs`**: Физика пружины (Spring-Damper) с сохранением объёма мялки пельменя.
6. **`Assets/Scripts/Addressables/SectorSceneLoader.cs`**: Бесшовное стриминг-создание и удаление секторов раннера.
7. **`Assets/Scripts/Addressables/PelmenCatalogAddressables.cs`**: Каталог асинхронной загрузки всех 67 вариантов пельменей.
8. **`Assets/Scripts/Backend/FirebaseBackendManager.cs`**: Синхронизация профиля игрока, валюты и разблокированных пельменей с Firebase Firestore.
9. **`Assets/Scripts/Mobile/AndroidMobileBridge.cs`**: Высокоточный виброотклик через JNI (VibrationEffect) и разблокировка 120 FPS.

---

## 📦 3. Как скачать APK файл?

Готовый скомпилированный APK файл Android приложения находится прямо в проекте:
* Путь в файловой системе: **`/.build-outputs/app-debug.apk`**
* **Как скачать в интерфейсе Google AI Studio:**
  1. В правом верхнем углу интерфейса AI Studio нажмите на иконку настроек / экспорта (**Project Settings / Export**).
  2. Выберите пункт **«Download APK»** или **«Export as ZIP»** (в ZIP архиве содержится весь проект, включая скомпилированный `.apk` и папку `unity_export`).

---

## 🚀 4. Как перенести проект в GitHub?

Вам **не нужно ничего копировать вручную**! Все файлы готовы в репозитории:

1. **Прямой экспорт в GitHub через AI Studio:**
   - Нажмите в верхнем правом меню **«Push to GitHub»** или **«Export to GitHub»**.
   - Авторизуйте ваш GitHub аккаунт, и AI Studio создаст репозиторий со всеми файлами (включая папку `unity_export/`).
2. **Использование в Unity:**
   - Склонируйте репозиторий с GitHub к себе на компьютер (`git clone ...`).
   - Создайте новый проект в **Unity Hub** с шаблоном **3D (URP)** (рекомендуется версия **Unity 2022.3 LTS** или **Unity 6**).
   - Скопируйте папку `unity_export/Assets` в папку `Assets` вашего нового Unity проекта.
   - В Unity Package Manager установите пакеты:
     * `Entities` (com.unity.entities)
     * `Addressables` (com.unity.addressables)
     * `Input System` (com.unity.inputsystem)
     * `Firebase Unity SDK` (по желанию, для включения директивы `FIREBASE_INSTALLED`).
