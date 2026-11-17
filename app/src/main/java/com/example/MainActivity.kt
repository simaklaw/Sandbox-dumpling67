package com.example

import com.example.ads.findActivity
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.audio.BackgroundMusicManager
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monetization.GameAccess
import com.example.monetization.MonetizationServices
import com.example.screens.FOLDER_ITEMS
import com.example.screens.FolderScreen
import com.example.screens.FOLDER_INFO
import com.example.screens.FolderType
import com.example.screens.MineScreen
import com.example.screens.RecentGames
import com.example.screens.folderTypeForPage
import com.example.screens.HomeScreen
import com.example.shared.*
import com.example.ui.components.RestTimerOverlay
import com.example.ui.components.ToddlerExitProtectionDialog
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PlaygroundViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed class AppScreenState {
    object Home : AppScreenState()
    /** «Моё»: коллекции и награды ребёнка. */
    object Mine : AppScreenState()
    data class Folder(val title: String, val folderType: FolderType) : AppScreenState()
    data class Game(val pageIndex: Int, val title: String) : AppScreenState()
}

/** Сохранение экрана в Bundle: "H" | "F|<FolderType>|<title>" | "G|<pageIndex>|<title>". */
val AppScreenStateSaver: Saver<AppScreenState, String> = Saver(
    save = { st ->
        when (st) {
            is AppScreenState.Home -> "H"
            is AppScreenState.Mine -> "M"
            is AppScreenState.Folder -> "F|${st.folderType.name}|${st.title}"
            is AppScreenState.Game -> "G|${st.pageIndex}|${st.title}"
        }
    },
    restore = { raw ->
        val parts = raw.split("|", limit = 3)
        try {
            when (parts[0]) {
                "F" -> AppScreenState.Folder(parts[2], FolderType.valueOf(parts[1]))
                "G" -> AppScreenState.Game(parts[1].toInt(), parts[2])
                "M" -> AppScreenState.Mine
                else -> AppScreenState.Home
            }
        } catch (e: Exception) {
            AppScreenState.Home
        }
    }
)

/**
 * Куда вернуться из игры по «Назад» или кнопке «Домой».
 * Папка определяется по единому списку [FOLDER_ITEMS]; игра с главного экрана возвращает на главный.
 */
fun parentStateForGame(pageIndex: Int): AppScreenState {
    val type = folderTypeForPage(pageIndex) ?: return AppScreenState.Home
    return AppScreenState.Folder(FOLDER_INFO.getValue(type).title, type)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: GameRepository
    private lateinit var soundPlayer: SoundEffectPlayer
    private lateinit var soundManager: SoundManager
    private lateinit var musicManager: BackgroundMusicManager
    private lateinit var viewModel: PlaygroundViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.shared.i18n.I18n.init(applicationContext)

        // Set default uncaught exception handler to prevent hard crashes and log issues cleanly
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        repository = GameRepository(this)
        // Считаем запуск один раз за процесс (не при повороте): первые запуски без рекламы.
        if (savedInstanceState == null) com.example.ads.AdServices.manager(this).onAppLaunch()
        soundPlayer = SoundEffectPlayer(this)
        soundManager = SoundManager.getInstance(this, soundPlayer)
        musicManager = BackgroundMusicManager()
        try {
            val on = getSharedPreferences("playground_prefs", MODE_PRIVATE).getBoolean("pref_music_enabled", true)
            musicManager.setMusicEnabled(on)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "music pref", e)
        }
        viewModel = PlaygroundViewModel(repository, soundPlayer, musicManager)

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    repository = repository,
                    soundPlayer = soundPlayer
                )
            }
        }
    }

    private fun hideSystemBars() {
        try {
            val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "hideSystemBars", e)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        com.example.shared.IdleTracker.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        try {
            if (::musicManager.isInitialized) musicManager.resumeMusic()
            if (::soundManager.isInitialized) soundManager.onAppResume()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (::musicManager.isInitialized) musicManager.pauseMusic()
            if (::soundManager.isInitialized) soundManager.onAppPause()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Error in onPause", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::musicManager.isInitialized) musicManager.release()
            if (::soundPlayer.isInitialized) soundPlayer.release()
            if (::soundManager.isInitialized) soundManager.releaseAll()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Error in onDestroy", e)
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: PlaygroundViewModel,
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }

    // Текущий экран переживает пересоздание Activity и смерть процесса (например, ребёнок свернул игру)
    var activeScreenState by rememberSaveable(stateSaver = AppScreenStateSaver) {
        mutableStateOf<AppScreenState>(AppScreenState.Home)
    }
    LaunchedEffect(Unit) { soundManager.preloadSpeech() }
    // Прокрутка папок живёт здесь, выше смены экранов: вышел из игры — вернулся на то же место списка
    val worldGridStates = FolderType.values().associateWith { androidx.compose.foundation.lazy.grid.rememberLazyGridState() }
    var musicOn by remember { mutableStateOf(prefs.getBoolean("pref_music_enabled", true)) }
    var showParentSettings by remember { mutableStateOf(false) }
    var showParentLock by remember { mutableStateOf(false) }
    var showStore by remember { mutableStateOf(false) }

    // Реклама: только на безопасных точках (вход на главный и выход из игры), решает AdManager.
    val adContext = androidx.compose.ui.platform.LocalContext.current
    val adManager = remember { com.example.ads.AdServices.manager(adContext.applicationContext) }
    var launchAdTried by rememberSaveable { mutableStateOf(false) }

    // Доступ к играм: при выключенной монетизации открыто всё. null = права ещё читаются из локальной БД.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val unlockedState: Set<String>? by remember { MonetizationServices.unlockedProducts(appContext) }
        .collectAsStateWithLifecycle(initialValue = if (BuildConfig.MONETIZATION_ENABLED) null else emptySet())
    val unlockedSet = unlockedState ?: emptySet()
    // Пока права читаются из БД или куплен remove_ads, рекламы нет.
    androidx.compose.runtime.SideEffect {
        com.example.ads.AdServices.adsRemoved =
            unlockedState == null || com.example.monetization.ProductIds.REMOVE_ADS in unlockedSet
    }
    val canPlay: (Int) -> Boolean = remember(unlockedSet) {
        { index -> GameAccess.canPlay(index, BuildConfig.MONETIZATION_ENABLED, unlockedSet) }
    }
    val toggleMusic: () -> Unit = {
        val on = !musicOn
        musicOn = on
        prefs.edit().putBoolean("pref_music_enabled", on).apply()
        viewModel.musicManager.setMusicEnabled(on)
        if (on) soundManager.play("pop")
    }
    // Заставка показывается один раз за запуск (rememberSaveable: не повторяется при повороте/пересоздании)
    var showIntro by rememberSaveable { mutableStateOf(true) }
    // музыка стартует только после заставки: иначе она звучит поверх нот
    LaunchedEffect(showIntro) {
        if (!showIntro) {
            viewModel.musicManager.held = false
            viewModel.musicManager.resumeMusic()
        }
    }
    var showExitProtectionDialog by remember { mutableStateOf(false) }

    // Таймер отдыха: считаем только пока приложение на экране (в кармане/свёрнутым время не идёт)
    // и не теряем накопленное при пересоздании Activity (смена темы/языка).
    var activePlaySeconds by rememberSaveable { mutableLongStateOf(0L) }
    var showRestTimerOverlay by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(showRestTimerOverlay, lifecycleOwner) {
        if (!showRestTimerOverlay) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(1000L)
                    activePlaySeconds++

                    val restSetting = prefs.getString("pref_rest_timer", "off") ?: "off"
                    val limitMinutes = when (restSetting) {
                        "10" -> 10
                        "20" -> 20
                        "30" -> 30
                        else -> 0
                    }

                    if (limitMinutes > 0 && activePlaySeconds >= limitMinutes * 60L) {
                        showRestTimerOverlay = true
                    }
                }
            }
        }
    }

    // Общий выход из игры (кнопка «Назад» внутри экранов со своей верхней панелью)
    // Единственное место выхода из игры: реклама (если AdManager разрешит) показывается ПОСЛЕ выхода, на экране папки/главном.
    // Игра, открытая из «Моё», возвращает туда же, а не в её мир
    var gameReturnsToMine by remember { mutableStateOf(false) }
    val openGame: (Int, String) -> Unit = { index, title ->
        RecentGames.push(adContext.applicationContext, index)
        gameReturnsToMine = activeScreenState is AppScreenState.Mine
        activeScreenState = AppScreenState.Game(index, title)
    }
    val exitGame: (Int) -> Unit = { pageIndex ->
        activeScreenState = if (gameReturnsToMine) AppScreenState.Mine else parentStateForGame(pageIndex)
        gameReturnsToMine = false
        adContext.findActivity()?.let { adManager.tryShow(it, com.example.ads.AdTrigger.EXIT) }
    }
    // Подгрузка рекламы заранее (не в onCreate): пока идёт заставка. AdManager сам не пустит при remove_ads/офлайне/первых запусках.
    LaunchedEffect(unlockedState) {
        if (unlockedState != null) adContext.findActivity()?.let { adManager.preload(it) }
    }
    LaunchedEffect(showIntro, activeScreenState) {
        if (!launchAdTried && !showIntro && activeScreenState is AppScreenState.Home) {
            launchAdTried = true
            adContext.findActivity()?.let { adManager.tryShow(it, com.example.ads.AdTrigger.LAUNCH) }
        }
    }
    val leaveGame: (Int) -> Unit = { pageIndex ->
        soundManager.play("transition")
        exitGame(pageIndex)
    }

    CompositionLocalProvider(LocalAgeProfile provides UNIVERSAL_PROFILE) {
        // Multi-touch & Back Button Toddler Protection
        BackHandler {
            soundManager.play("tap")
            when (activeScreenState) {
                is AppScreenState.Home -> {
                    showExitProtectionDialog = true
                }
                is AppScreenState.Folder, is AppScreenState.Mine -> {
                    activeScreenState = AppScreenState.Home
                }
                is AppScreenState.Game -> {
                    exitGame((activeScreenState as AppScreenState.Game).pageIndex)
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = activeScreenState,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.96f, animationSpec = tween(250)))
                            .togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "app_screens"
                ) { screenState ->
                    when (screenState) {
                        is AppScreenState.Home -> {
                            HomeScreen(
                                onNavigateToGame = openGame,
                                onOpenMine = { activeScreenState = AppScreenState.Mine },
                                canPlay = canPlay,
                                onNavigateToFolder = { title, type ->
                                    activeScreenState = AppScreenState.Folder(title, type)
                                },
                                onOpenSettings = { showParentLock = true },
                                musicOn = musicOn,
                                onToggleMusic = toggleMusic,
                                soundManager = soundManager,
                                haptics = haptics
                            )
                        }
                        is AppScreenState.Mine -> {
                            MineScreen(
                                repository = repository,
                                soundManager = soundManager,
                                haptics = haptics,
                                onGoBack = { activeScreenState = AppScreenState.Home },
                                onOpenGame = openGame
                            )
                        }
                        is AppScreenState.Folder -> {
                            FolderScreen(
                                title = screenState.title,
                                folderType = screenState.folderType,
                                onNavigateToGame = openGame,
                                onGoBack = {
                                    activeScreenState = AppScreenState.Home
                                },
                                soundManager = soundManager,
                                haptics = haptics,
                                canPlay = canPlay,
                                gridState = worldGridStates.getValue(screenState.folderType)
                            )
                        }
                        is AppScreenState.Game -> {
                            if (unlockedState == null) {
                                Box(modifier = Modifier.fillMaxSize())
                            } else if (!canPlay(screenState.pageIndex)) {
                                // Платная игра без права: не рисуем, возвращаем на главный (защита от обхода).
                                LaunchedEffect(screenState) { activeScreenState = AppScreenState.Home }
                                Box(modifier = Modifier.fillMaxSize())
                            } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                GameTopBar(
                                    title = screenState.title,
                                    haptics = haptics,
                                    onHome = {
                                        soundManager.play("transition")
                                        exitGame(screenState.pageIndex)
                                    }
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (screenState.pageIndex) {
                                    0 -> AnimalRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    1 -> AnimalFeedingScreen(repository = repository, soundPlayer = soundPlayer)
                                    3 -> FaceAntistressScreen(repository = repository, soundPlayer = soundPlayer)
                                    4 -> ScarecrowArcheryScreen(repository = repository, soundPlayer = soundPlayer)
                                    5 -> FunnyRunningBoobaScreen(repository = repository, soundPlayer = soundPlayer)
                                    6 -> GrainPouringScreen(repository = repository, soundPlayer = soundPlayer)
                                    7 -> BalloonPopScreen(repository = repository, soundPlayer = soundPlayer)
                                    9 -> FingerPaintingScreen(repository = repository, soundPlayer = soundPlayer)
                                    10 -> BusyboardScreen(repository = repository, soundPlayer = soundPlayer)
                                    11 -> BlindBoxScreen(repository = repository, soundPlayer = soundPlayer)
                                     12 -> PinballMarbleRunScreen(repository = repository, soundPlayer = soundPlayer)
                                     16 -> PopItScreen(repository = repository, soundPlayer = soundPlayer)
                                     17 -> PinataScreen(repository = repository, soundPlayer = soundPlayer)
                                    20 -> BubbleWrapScreen(repository = repository, soundPlayer = soundPlayer)
                                    21 -> SmeshinkiScreen(repository = repository, soundPlayer = soundPlayer)
                                    22 -> KineticSandScreen(repository = repository, soundPlayer = soundPlayer)
                                    24 -> RainScreen(repository = repository, soundPlayer = soundPlayer)
                                    26 -> ShapeSorterScreen(repository = repository, soundPlayer = soundPlayer)
                                    27 -> AlienRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    28 -> ZelikAdventureScreen(repository = repository, soundPlayer = soundPlayer)
                                    30 -> AquariumScreen(repository = repository, soundPlayer = soundPlayer)
                                    31 -> ShootingGalleryScreen(repository = repository, soundPlayer = soundPlayer)
                                    32 -> MonsterWorldScreen(repository = repository, soundPlayer = soundPlayer)
                                    33 -> MonsterWorldScreen(repository = repository, soundPlayer = soundPlayer, startInRun = true) // индекс 33 выведен: ведёт в единую комнату
                                    34 -> ButtonJoyScreen(repository = repository, soundPlayer = soundPlayer)
                                    35 -> SharkChaseScreen(repository = repository, soundPlayer = soundPlayer)
                                    36 -> VeggieCuttingScreen(repository = repository, soundPlayer = soundPlayer)
                                    37 -> KastrulyaScreen(repository = repository, soundPlayer = soundPlayer)
                                    38 -> LaundryScreen(repository = repository, soundPlayer = soundPlayer)
                                    39 -> CradleRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    40 -> SandboxRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    41 -> CarWashRoomScreen(
                                        repository = repository,
                                        soundPlayer = soundPlayer,
                                        // на время танца игрушки фоновая музыка приложения ставится на паузу
                                        bgMusicPlaying = { viewModel.musicManager.isEnabled && viewModel.musicManager.isPlaying.value },
                                        setBgMusic = { on -> if (on) viewModel.musicManager.resumeMusic() else viewModel.musicManager.pauseMusic() }
                                    )
                                    42 -> ShopRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    43 -> WeatherRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    44 -> BirdFlightScreen(repository = repository, soundPlayer = soundPlayer)
                                    46 -> ChestRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    47 -> PetYardScreen(repository = repository, soundPlayer = soundPlayer)
                                    48 -> BlockPuzzleScreen(repository = repository, soundPlayer = soundPlayer)
                                    49 -> ClawGameScreen(repository = repository, soundPlayer = soundPlayer)
                                    50 -> JetBotScreen(
                                        repository = repository, soundPlayer = soundPlayer,
                                        onExit = { soundManager.play("transition"); exitGame(50) }
                                    )
                                    51 -> CookScreen(repository = repository, soundPlayer = soundPlayer)
                                    52 -> HyperTrackScreen(repository = repository, soundPlayer = soundPlayer)
                                    23 -> BabyPhoneScreen(repository = repository, soundPlayer = soundPlayer)
                                }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }

        // Settings for parents dialog
        if (showParentLock) {
            com.example.shared.ParentLockDialog(
                onUnlocked = {
                    showParentLock = false
                    showParentSettings = true
                },
                onDismiss = { showParentLock = false }
            )
        }

        if (showStore) {
            com.example.shared.ParentStoreDialog(onDismiss = { showStore = false })
        }

        if (showParentSettings) {
            ParentSettingsDialog(
                soundManager = soundManager,
                musicOn = musicOn,
                onToggleMusic = toggleMusic,
                onResetProgress = {
                    viewModel.resetProgress()
                    soundManager.play("chime")
                },
                onNavigateToGame = { targetIndex ->
                    activeScreenState = AppScreenState.Game(targetIndex, if (targetIndex == 11) "Сюрпризы" else "Бегун")
                    showParentSettings = false
                },
                onOpenStore = if (BuildConfig.MONETIZATION_ENABLED && BuildConfig.ADS_ENABLED) ({ showStore = true }) else null,
                onDismiss = {
                    showParentSettings = false
                }
            )
        }

        // Toddler exit protection dialog
        if (showExitProtectionDialog) {
            ToddlerExitProtectionDialog(
                onContinuePlaying = {
                    showExitProtectionDialog = false
                    soundManager.play("click")
                },
                onParentUnlock = {
                    showExitProtectionDialog = false
                    showParentLock = true
                }
            )
        }

        if (showIntro) {
            com.example.screens.IntroScreen(soundManager = soundManager, onFinished = { showIntro = false })
        }

        // Rest Timer Night Overlay
        RestTimerOverlay(
            visible = showRestTimerOverlay,
            soundManager = soundManager,
            onParentUnlock = {
                showRestTimerOverlay = false
                activePlaySeconds = 0L
            }
        )
    }
}
