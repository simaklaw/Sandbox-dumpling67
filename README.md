# Детская площадка

Офлайн-приложение для Android с мини-играми для малышей: один универсальный профиль без возрастных режимов (папки с играми: кухня, «Игры» с разделами по жанрам, хозяйство и магазин и др.). Полностью без интернета: разрешения `INTERNET` в манифесте нет, прогресс хранится на устройстве.

- Kotlin + Jetpack Compose (Material 3), Room, JBox2D (физика), Lottie (конфетти)
- «Ракетный робот» (индекс 50, `ui/screens/JetBotScreen.kt` + правила `JetBotRules.kt` с тестами): идея из веб-игры Supersonic-bot, переписана на Compose Canvas — палец держит тягу, три сердечка, без микрофона и интернета, все картинки нарисованы кодом
- «Хваталка» (игра с когтем) — three.js внутри WebView, ассеты в `app/src/main/assets/claw`, всё грузится локально
- `applicationId`: `com.aistudio.kidsplayground.play`, код живёт в пакете `com.example`
- minSdk 24, targetSdk/compileSdk 36, JDK 21, Gradle 9.3.1

## Сборка и проверка

Каждый push запускает GitHub Actions (`.github/workflows/android.yml`): unit-тесты и debug-APK. Готовый APK лежит в артефакте `app-debug` запуска. Если сборка упала, лог ошибок виден в самом запуске.

Локально (нужны JDK 21 и Android SDK 36):

```bash
gradle :app:testDebugUnitTest   # тесты (Robolectric, физика раннера, навигация, аудит звуков)
gradle :app:assembleDebug       # app/build/outputs/apk/debug/
```

Скриптов `gradlew` в репозитории нет: Android Studio берёт версию Gradle из `gradle/wrapper/gradle-wrapper.properties`.

## Структура

| Путь (`app/src/main/java/com/example`) | Что внутри |
|---|---|
| `MainActivity.kt` | точка входа, навигация по номерам игр (`when(pageIndex)`), «Назад/Домой» |
| `screens/` | главный экран, папки игр, игры с отдельными модулями (Archery, Busyboard, Pinball, Rain, Squish…) |
| `ui/screens/` | остальные игровые экраны, общие UI-наборы (`UiKit`, `LottieKit`) |
| `audio/`, `shared/SoundManager.kt` | звуковой движок, каталог звуков (`SoundCatalog`), аудит файлов |
| `physics/runner/` | физика раннера (JBox2D) |
| `data/` | Room (`AppDatabase`), `GameRepository` |
| `shared/i18n/` | переключение RU ⇄ EN (словарь `res/raw/i18n_en.json`) |

Игра №5 «Бегун» (`FunnyRunningBoobaScreen`) использует `PelmenRunnerScreen`, его физику и звуки: это не устаревший код.

## Ресурсы, которые ищутся по имени

Часть ресурсов подгружается строкой, а не через `R.*`, поэтому поиск «нигде не упоминается» по ним обманывает:

- звуки: `SoundResourceResolver` / `getIdentifier(name, "raw", …)`; английская озвучка выбирается добавлением суффикса `_en` к имени файла (`SoundManager`)
- картинки: `getIdentifier(…, "drawable", …)`, например `pet_%02d` в «Луге питомцев»

Поэтому `shrinkResources`/R8 для релиза без keep-правил включать нельзя: удалятся нужные файлы.

## Звуки

`generate_all_sounds.py` создаёт заглушки, `prepare_sounds.sh` приводит исходники из `input/` к формату игры (OGG, моно, 44,1 кГц) в `output/`; готовые файлы копируются в `app/src/main/res/raw`. Список и статус: `sounds_checklist.csv`. Звуки переходов, лопания шариков (`bp_pop1..7`), жевания (`kc_munch*`) и знаки вместо слов делает `generate_polish_sounds.py` (numpy + ffmpeg); `generate_all_sounds.py` исторический, его шумовые «вжухи» и робот `espeak-ng` в игру не попадают. Как заменить заглушки живыми голосами — `VOICES.md`. Звуки «Бизиборда» (`bb2_*`) синтезирует `generate_busyboard_sounds.py` (numpy, scipy, ffmpeg) прямо в `res/raw`. Лицензии: `CREDITS.md`, `SOUND_CREDITS.md`.

## Другое

- `ROADMAP.md` — план и журнал работ, `DEVICE_CHECKLIST.md` — чек-лист проверки на телефоне
- `unity_export/` — набор для возможного переноса на Unity; в сборку Android не входит
