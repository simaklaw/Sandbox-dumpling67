using System;
using UnityEngine;
using PelmenGame.ECS;

namespace PelmenGame.Mobile
{
    /// <summary>
    /// Bridges Unity C# with Android native OS APIs:
    /// - Rich Haptic Feedback (Vibrator & VibrationEffect via Android JNI + Handheld.Vibrate fallback)
    /// - Screen Refresh Rate (90Hz / 120Hz display modes)
    /// - Battery Thermal State & Safe Area Screen Insets
    /// </summary>
    public class AndroidMobileBridge : MonoBehaviour
    {
        public static AndroidMobileBridge Instance { get; private set; }

#if UNITY_ANDROID && !UNITY_EDITOR
        private AndroidJavaObject vibratorService;
        private AndroidJavaClass vibrationEffectClass;
        private int apiLevel = 0;
#endif

        private void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
                InitializeAndroidServices();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        private void InitializeAndroidServices()
        {
            // Maximize frame rate for smooth 120Hz / 90Hz gaming on modern Android phones
            QualitySettings.vSyncCount = 0;
            Application.targetFrameRate = 120;

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var versionClass = new AndroidJavaClass("android.os.Build$VERSION"))
                {
                    apiLevel = versionClass.GetStatic<int>("SDK_INT");
                }

                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var currentActivity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                {
                    vibratorService = currentActivity.Call<AndroidJavaObject>("getSystemService", "vibrator");
                }

                if (apiLevel >= 26) // Android 8.0 Oreo+
                {
                    vibrationEffectClass = new AndroidJavaClass("android.os.VibrationEffect");
                }
                Debug.Log($"[AndroidMobileBridge] Initialized native Vibrator. API Level: {apiLevel}");
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[AndroidMobileBridge] Could not initialize Android native vibrator: {ex.Message}");
            }
#endif
        }

        /// <summary>
        /// Triggers high-fidelity haptic feedback based on the game event
        /// </summary>
        public void TriggerHaptic(HapticFeedbackPattern pattern, float intensity = 1.0f)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (vibratorService == null)
            {
                Handheld.Vibrate();
                return;
            }

            try
            {
                if (apiLevel >= 29) // Android 10+ (Predefined effects)
                {
                    int effectId = -1;
                    switch (pattern)
                    {
                        case HapticFeedbackPattern.LightTick:
                            effectId = 2; // VibrationEffect.EFFECT_TICK
                            break;
                        case HapticFeedbackPattern.MediumImpact:
                            effectId = 0; // VibrationEffect.EFFECT_CLICK
                            break;
                        case HapticFeedbackPattern.HeavySlam:
                        case HapticFeedbackPattern.BlindBoxPop:
                            effectId = 1; // VibrationEffect.EFFECT_HEAVY_CLICK
                            break;
                        case HapticFeedbackPattern.BlindBoxCrack:
                            effectId = 5; // VibrationEffect.EFFECT_DOUBLE_CLICK
                            break;
                    }

                    if (effectId >= 0 && vibrationEffectClass != null)
                    {
                        using (var effect = vibrationEffectClass.CallStatic<AndroidJavaObject>("createPredefined", effectId))
                        {
                            vibratorService.Call("vibrate", effect);
                            return;
                        }
                    }
                }

                if (apiLevel >= 26 && vibrationEffectClass != null) // Android 8+
                {
                    long durationMs = 25;
                    int amplitude = Mathf.Clamp((int)(intensity * 255), 1, 255);

                    switch (pattern)
                    {
                        case HapticFeedbackPattern.LightTick:
                            durationMs = 15;
                            amplitude = 60;
                            break;
                        case HapticFeedbackPattern.MediumImpact:
                            durationMs = 35;
                            amplitude = 140;
                            break;
                        case HapticFeedbackPattern.HeavySlam:
                            durationMs = 80;
                            amplitude = 255;
                            break;
                        case HapticFeedbackPattern.BlindBoxCrack:
                            durationMs = 45;
                            amplitude = 180;
                            break;
                        case HapticFeedbackPattern.BlindBoxPop:
                            durationMs = 120;
                            amplitude = 255;
                            break;
                    }

                    using (var effect = vibrationEffectClass.CallStatic<AndroidJavaObject>("createOneShot", durationMs, amplitude))
                    {
                        vibratorService.Call("vibrate", effect);
                        return;
                    }
                }

                // Legacy fallback for old Android devices
                vibratorService.Call("vibrate", (long)40);
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[AndroidMobileBridge] Vibration failed: {ex.Message}. Falling back to Handheld.Vibrate()");
                Handheld.Vibrate();
            }
#else
            // Unity Editor or non-Android fallback
            Debug.Log($"[Haptic Simulation] Pattern: {pattern}, Intensity: {intensity}");
#endif
        }

        public Rect GetSafeArea()
        {
            return Screen.safeArea;
        }
    }
}
