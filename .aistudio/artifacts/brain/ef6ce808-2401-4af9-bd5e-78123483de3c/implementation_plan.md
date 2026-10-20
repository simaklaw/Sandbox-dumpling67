# Implementation Plan - Fixing Entry Crash in "Детская площадка"

## Problem Analysis
The user reports that the app crashes immediately upon entering the main gameplay screen. Potential causes include:
1. Room database synchronous queries in `GameRepository` init block (`pelmenVariantDao.getVariantSync(...)`) throwing SQLite exceptions or migration issues on physical devices.
2. `SoundManager` preloading raw assets synchronously or SoundPool initialization failing.
3. `FirstLaunchAgeDialog` or SharedPreferences state evaluation crashing on first render.

## Proposed Changes

### 1. Hardening `GameRepository` Initialization (`GameRepository.kt`)
- Move database startup seeding (`pelmenVariantDao.getVariantSync`, starter skin checks) into a safe `try-catch` block inside `ioScope.launch` so that database operations never block main thread or cause unhandled exceptions during repository creation.

### 2. Hardening `SoundManager` & `SoundEffectPlayer` (`SoundManager.kt` & `SoundEffectPlayer.kt`)
- Ensure `SoundPool` load failures and missing raw resources are silently caught and ignored without throwing runtime exceptions.
- Make preloading completely asynchronous and non-blocking.

### 3. Hardening Main UI Entry (`MainActivity.kt` & `HomeScreen.kt`)
- Ensure `FirstLaunchAgeDialog` and SharedPreferences reads have safe fallback defaults (`"3-5"`) and robust exception handling.

## Verification Plan
- Compile applet.
- Run `gradle :app:testDebugUnitTest` to verify Robolectric tests.
- Rebuild debug APK.
