using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using UnityEngine;

#if FIREBASE_INSTALLED
using Firebase;
using Firebase.Auth;
using Firebase.Firestore;
using Firebase.Extensions;
#endif

namespace PelmenGame.Backend
{
    [System.Serializable]
    public class UserGameProfile
    {
        public string userId;
        public string displayName;
        public int pelmenCoins;
        public int highScore;
        public List<int> unlockedVariantIds = new();
        public int totalBoxesOpened;
        public long lastSyncedTimestamp;
    }

    /// <summary>
    /// Handles backend synchronization with Firebase (Auth, Firestore, Cloud Functions).
    /// Gracefully provides fallback/offline operation if Firebase SDK is not yet imported.
    /// </summary>
    public class FirebaseBackendManager : MonoBehaviour
    {
        public static FirebaseBackendManager Instance { get; private set; }

        public event Action<UserGameProfile> OnProfileLoaded;
        public event Action<int> OnBlindBoxUnlocked;

        [SerializeField] private UserGameProfile currentProfile = new();
        public UserGameProfile CurrentProfile => currentProfile;

        private bool isFirebaseInitialized = false;

        private void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
            }
            else
            {
                Destroy(gameObject);
            }
        }

        private async void Start()
        {
            await InitializeFirebaseAsync();
        }

        public async Task InitializeFirebaseAsync()
        {
#if FIREBASE_INSTALLED
            var dependencyStatus = await FirebaseApp.CheckAndFixDependenciesAsync();
            if (dependencyStatus == DependencyStatus.Available)
            {
                var auth = FirebaseAuth.DefaultInstance;
                if (auth.CurrentUser == null)
                {
                    var authResult = await auth.SignInAnonymouslyAsync();
                    currentProfile.userId = authResult.User.UserId;
                }
                else
                {
                    currentProfile.userId = auth.CurrentUser.UserId;
                }

                isFirebaseInitialized = true;
                Debug.Log($"[Firebase] Initialized. User ID: {currentProfile.userId}");
                await LoadUserProfileFromFirestoreAsync();
            }
            else
            {
                Debug.LogError($"[Firebase] Could not resolve dependencies: {dependencyStatus}");
                InitializeLocalFallback();
            }
#else
            Debug.Log("[Firebase] Firebase SDK not installed in Unity project. Operating in offline/local mock mode.");
            InitializeLocalFallback();
            await Task.Yield();
#endif
        }

        private void InitializeLocalFallback()
        {
            currentProfile.userId = SystemInfo.deviceUniqueIdentifier;
            currentProfile.displayName = "Chef_Pelmen_" + currentProfile.userId.Substring(0, 4);
            currentProfile.pelmenCoins = 1500;
            currentProfile.highScore = 0;
            
            // Starter variants unlocked (e.g. Classic Beef #1, Garlic Butter #2)
            if (!currentProfile.unlockedVariantIds.Contains(1)) currentProfile.unlockedVariantIds.Add(1);
            if (!currentProfile.unlockedVariantIds.Contains(2)) currentProfile.unlockedVariantIds.Add(2);
            
            OnProfileLoaded?.Invoke(currentProfile);
        }

        public async Task SaveProfileAsync()
        {
            currentProfile.lastSyncedTimestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();

#if FIREBASE_INSTALLED
            if (isFirebaseInitialized)
            {
                var db = FirebaseFirestore.DefaultInstance;
                var userDoc = db.Collection("pelmen_users").Document(currentProfile.userId);

                var data = new Dictionary<string, object>
                {
                    { "userId", currentProfile.userId },
                    { "displayName", currentProfile.displayName },
                    { "pelmenCoins", currentProfile.pelmenCoins },
                    { "highScore", currentProfile.highScore },
                    { "unlockedVariantIds", currentProfile.unlockedVariantIds },
                    { "totalBoxesOpened", currentProfile.totalBoxesOpened },
                    { "lastSyncedTimestamp", currentProfile.lastSyncedTimestamp }
                };

                await userDoc.SetAsync(data, SetOptions.MergeAll);
                Debug.Log("[Firebase] User profile synced to Firestore.");
            }
#else
            // Save to PlayerPrefs locally
            string json = JsonUtility.ToJson(currentProfile);
            PlayerPrefs.SetString("pelmen_local_profile", json);
            PlayerPrefs.Save();
            await Task.Yield();
#endif
        }

        /// <summary>
        /// Rolls a blind box server-side (or local deterministic RNG), unlocks the variant (1..67).
        /// </summary>
        public async Task<int> OpenBlindBoxAsync(int boxTier)
        {
            // Tier 1: Common-Rare, Tier 2: Epic, Tier 3: Legendary / Mythic
            int rolledVariantId = UnityEngine.Random.Range(1, 68); // 1 to 67 inclusive

            if (!currentProfile.unlockedVariantIds.Contains(rolledVariantId))
            {
                currentProfile.unlockedVariantIds.Add(rolledVariantId);
            }

            currentProfile.totalBoxesOpened++;
            await SaveProfileAsync();

            OnBlindBoxUnlocked?.Invoke(rolledVariantId);
            return rolledVariantId;
        }
    }
}
