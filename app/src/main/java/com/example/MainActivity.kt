package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.screens.FolderScreen
import com.example.screens.FolderType
import com.example.screens.folderTypeForPage
import com.example.screens.HomeScreen
import com.example.sensor.TiltSensorManager
import com.example.shared.*
import com.example.ui.components.FirstLaunchAgeDialog
import com.example.ui.components.RestTimerOverlay
import com.example.ui.components.ToddlerExitProtectionDialog
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PlaygroundViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed class AppScreenState {
    object Home : AppScreenState()
    data class Folder(val title: String, val folderType: FolderType) : AppScreenState()
    data class Game(val pageIndex: Int, val title: String) : AppScreenState()
}

/** Сохранение экрана в Bundle: "H" | "F|<FolderType>|<title>" | "G|<pageIndex>|<title>". */
val AppScreenStateSaver: Saver<AppScreenState, String> = Saver(
    save = { st ->
        when (st) {
            is AppScreenState.Home -> "H"
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
                else -> AppScreenState.Home
            }
        } catch (e: Exception) {
            AppScreenState.Home
        }
    }
)

/**
 * Куда вернуться из игры по «Назад» или кнопке «Домой».
 * Папка определяется по единому списку [FOLDER_ITEMS]; для режима «2 года» папок нет — сразу на главный экран.
 */
fun parentStateForGame(pageIndex: Int, isToddler2: Boolean): AppScreenState {
    if (isToddler2) return AppScreenState.Home
    return when (folderTypeForPage(pageIndex)) {
        FolderType.GAMES -> AppScreenState.Folder("Игры", FolderType.GAMES)
        FolderType.KITCHEN -> AppScreenState.Folder("Кухня", FolderType.KITCHEN)
        null -> AppScreenState.Home
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: GameRepository
    private lateinit var soundPlayer: SoundEffectPlayer
    private lateinit var soundManager: SoundManager
    private lateinit var musicManager: BackgroundMusicManager
    private lateinit var tiltSensorManager: TiltSensorManager
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

        try {
            repository = GameRepository(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init GameRepository, using fallback", e)
            repository = GameRepository(applicationContext)
        }

        try {
            soundPlayer = SoundEffectPlayer(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init SoundEffectPlayer", e)
            soundPlayer = SoundEffectPlayer(applicationContext)
        }

        try {
            soundManager = SoundManager.getInstance(this, soundPlayer)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init SoundManager", e)
            soundManager = SoundManager.getInstance(applicationContext, soundPlayer)
        }

        try {
            musicManager = BackgroundMusicManager()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init BackgroundMusicManager", e)
            musicManager = BackgroundMusicManager()
        }

        try {
            val on = getSharedPreferences("playground_prefs", MODE_PRIVATE).getBoolean("pref_music_enabled", true)
            musicManager.setMusicEnabled(on)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "music pref", e)
        }

        try {
            tiltSensorManager = TiltSensorManager(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init TiltSensorManager", e)
            tiltSensorManager = TiltSensorManager(applicationContext)
        }

        try {
            viewModel = PlaygroundViewModel(repository, soundPlayer, musicManager, tiltSensorManager)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to init PlaygroundViewModel", e)
            viewModel = PlaygroundViewModel(repository, soundPlayer, musicManager, tiltSensorManager)
        }

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        try {
            if (::musicManager.isInitialized) musicManager.resumeMusic()
            if (::tiltSensorManager.isInitialized) tiltSensorManager.startListening()
            if (::soundManager.isInitialized) soundManager.onAppResume()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (::musicManager.isInitialized) musicManager.pauseMusic()
            if (::tiltSensorManager.isInitialized) tiltSensorManager.stopListening()
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
            if (::tiltSensorManager.isInitialized) tiltSensorManager.stopListening()
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

    // Age profile state
    var currentAgeGroup by remember {
        mutableStateOf(prefs.getString("pref_age_group", "3-5") ?: "3-5")
    }
    val activeAgeProfile = remember(currentAgeGroup) { getAgeProfileById(currentAgeGroup) }

    // First launch age dialog
    var showFirstLaunchAgeDialog by remember {
        mutableStateOf(!prefs.getBoolean("pref_first_launch_age_selected", false))
    }

    // Текущий экран переживает пересоздание Activity и смерть процесса (например, ребёнок свернул игру)
    var activeScreenState by rememberSaveable(stateSaver = AppScreenStateSaver) {
        mutableStateOf<AppScreenState>(AppScreenState.Home)
    }
    LaunchedEffect(Unit) { soundManager.preloadSpeech() }
    var musicOn by remember { mutableStateOf(prefs.getBoolean("pref_music_enabled", true)) }
    var showParentSettings by remember { mutableStateOf(false) }
    var showExitProtectionDialog by remember { mutableStateOf(false) }

    // Rest timer state
    var activePlaySeconds by remember { mutableLongStateOf(0L) }
    var showRestTimerOverlay by remember { mutableStateOf(false) }

    // Rest timer ticker loop
    LaunchedEffect(showRestTimerOverlay) {
        if (!showRestTimerOverlay) {
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

    // Общий выход из игры (кнопка «Назад» внутри экранов со своей верхней панелью)
    val leaveGame: (Int) -> Unit = { pageIndex ->
        soundManager.play("transition")
        activeScreenState = parentStateForGame(pageIndex, activeAgeProfile.isToddler2)
    }

    CompositionLocalProvider(LocalAgeProfile provides activeAgeProfile) {
        // Multi-touch & Back Button Toddler Protection
        BackHandler {
            soundManager.play("tap")
            when (activeScreenState) {
                is AppScreenState.Home -> {
                    showExitProtectionDialog = true
                }
                is AppScreenState.Folder -> {
                    activeScreenState = AppScreenState.Home
                }
                is AppScreenState.Game -> {
                    activeScreenState = parentStateForGame(
                        (activeScreenState as AppScreenState.Game).pageIndex,
                        activeAgeProfile.isToddler2
                    )
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
                                onNavigateToGame = { index, title ->
                                    activeScreenState = AppScreenState.Game(index, title)
                                },
                                onNavigateToFolder = { title, type ->
                                    activeScreenState = AppScreenState.Folder(title, type)
                                },
                                onOpenSettings = {
                                    showParentSettings = true
                                },
                                musicOn = musicOn,
                                onToggleMusic = {
                                    val on = !musicOn
                                    musicOn = on
                                    prefs.edit().putBoolean("pref_music_enabled", on).apply()
                                    viewModel.musicManager.setMusicEnabled(on)
                                    if (on) soundManager.play("pop")
                                },
                                soundManager = soundManager,
                                haptics = haptics
                            )
                        }
                        is AppScreenState.Folder -> {
                            FolderScreen(
                                title = screenState.title,
                                folderType = screenState.folderType,
                                onNavigateToGame = { index, title ->
                                    activeScreenState = AppScreenState.Game(index, title)
                                },
                                onGoBack = {
                                    activeScreenState = AppScreenState.Home
                                },
                                soundManager = soundManager,
                                haptics = haptics
                            )
                        }
                        is AppScreenState.Game -> {
                            Box(modifier = Modifier.fillMaxSize()) {
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
                                    20 -> BubbleWrapScreen(repository = repository, soundPlayer = soundPlayer, onBackClick = { leaveGame(20) })
                                    21 -> SmeshinkiScreen(repository = repository, soundPlayer = soundPlayer, onBackClick = { leaveGame(21) })
                                    22 -> KineticSandScreen(repository = repository, soundPlayer = soundPlayer, onBackClick = { leaveGame(22) })
                                    24 -> RainScreen(repository = repository, soundPlayer = soundPlayer)
                                    25 -> BoxSurpriseScreen(repository = repository, soundPlayer = soundPlayer)
                                    26 -> ShapeSorterScreen(repository = repository, soundPlayer = soundPlayer)
                                    27 -> AlienRoomScreen(repository = repository, soundPlayer = soundPlayer)
                                    28 -> ZelikAdventureScreen(repository = repository, soundPlayer = soundPlayer)
                                    30 -> AquariumScreen(repository = repository, soundPlayer = soundPlayer)
                                    31 -> ShootingGalleryScreen(repository = repository, soundPlayer = soundPlayer)
                                    32 -> MonsterFactoryScreen(repository = repository, soundPlayer = soundPlayer)
                                    33 -> MonsterRunnerScreen(repository = repository, soundPlayer = soundPlayer)
                                    34 -> ButtonJoyScreen(repository = repository, soundPlayer = soundPlayer)
                                    35 -> SharkChaseScreen(repository = repository, soundPlayer = soundPlayer)
                                    23 -> BabyPhoneScreen(
                                        repository = repository,
                                        soundPlayer = soundPlayer,
                                        onBackClick = { leaveGame(23) }
                                    )
                                }

                                // Home button at top left to exit the game cleanly
                                HomeButton(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp),
                                    haptics = haptics,
                                    onClick = {
                                        soundManager.play("transition")
                                        activeScreenState = parentStateForGame(screenState.pageIndex, activeAgeProfile.isToddler2)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings for parents dialog
        if (showParentSettings) {
            ParentSettingsDialog(
                soundManager = soundManager,
                onResetProgress = {
                    viewModel.resetProgress()
                    soundManager.play("chime")
                },
                onNavigateToGame = { targetIndex ->
                    activeScreenState = AppScreenState.Game(targetIndex, if (targetIndex == 11) "Сюрпризы" else "Бегун")
                    showParentSettings = false
                },
                onDismiss = {
                    showParentSettings = false
                    // Update active age profile if changed
                    currentAgeGroup = prefs.getString("pref_age_group", "3-5") ?: "3-5"
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
                    showParentSettings = true
                }
            )
        }

        // First Launch Age Selector Dialog
        if (showFirstLaunchAgeDialog) {
            FirstLaunchAgeDialog(
                onSelectAge = { selectedAge ->
                    currentAgeGroup = selectedAge
                    prefs.edit()
                        .putString("pref_age_group", selectedAge)
                        .putBoolean("pref_first_launch_age_selected", true)
                        .apply()
                    showFirstLaunchAgeDialog = false
                    soundManager.play("chime")
                }
            )
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
