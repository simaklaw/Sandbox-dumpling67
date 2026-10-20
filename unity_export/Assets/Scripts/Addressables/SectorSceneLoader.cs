using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.AddressableAssets;
using UnityEngine.ResourceManagement.AsyncOperations;
using UnityEngine.ResourceManagement.ResourceProviders;
using UnityEngine.SceneManagement;

namespace PelmenGame.AddressablesManagement
{
    /// <summary>
    /// Manages dynamic loading and unloading of Runner sector environments via Addressables.
    /// Eliminates memory bloat on mobile devices by unloading passed sectors.
    /// </summary>
    public class SectorSceneLoader : MonoBehaviour
    {
        public static SectorSceneLoader Instance { get; private set; }

        [Header("Configuration")]
        [SerializeField] private float chunkLength = 80.0f;
        [SerializeField] private int activeChunksCount = 4;
        [SerializeField] private string[] sectorAddressableKeys = new string[]
        {
            "Sector_Kitchen_Start",
            "Sector_Boiling_Broth",
            "Sector_Freezer_Slalom",
            "Sector_Grandma_Table",
            "Sector_Spice_Ramp",
            "Sector_Steam_Turbine"
        };

        private readonly Queue<AsyncOperationHandle<GameObject>> loadedSectorHandles = new();
        private readonly List<GameObject> activeSectorInstances = new();
        private float nextSpawnZ = 0.0f;
        private int currentSectorIndex = 0;

        private void Awake()
        {
            if (Instance == null) Instance = this;
            else Destroy(gameObject);
        }

        private async void Start()
        {
            // Initial preload of leading chunks
            for (int i = 0; i < activeChunksCount; i++)
            {
                await SpawnNextSectorChunkAsync();
            }
        }

        public async void UpdatePlayerPosition(float playerZ)
        {
            // Check if player has advanced far enough to spawn new sector chunk
            if (playerZ > nextSpawnZ - (activeChunksCount * chunkLength * 0.7f))
            {
                await SpawnNextSectorChunkAsync();
                RecycleOldestSectorChunk();
            }
        }

        public async Task<GameObject> SpawnNextSectorChunkAsync()
        {
            string key = sectorAddressableKeys[currentSectorIndex % sectorAddressableKeys.Length];
            currentSectorIndex++;

            AsyncOperationHandle<GameObject> handle = Addressables.InstantiateAsync(
                key,
                new Vector3(0, 0, nextSpawnZ),
                Quaternion.identity
            );

            await handle.Task;

            if (handle.Status == AsyncOperationStatus.Succeeded)
            {
                GameObject sectorGO = handle.Result;
                loadedSectorHandles.Enqueue(handle);
                activeSectorInstances.Add(sectorGO);
                nextSpawnZ += chunkLength;
                return sectorGO;
            }
            else
            {
                Debug.LogError($"[Addressables] Failed to load sector with key: {key}");
                return null;
            }
        }

        private void RecycleOldestSectorChunk()
        {
            if (activeSectorInstances.Count > activeChunksCount)
            {
                GameObject oldGo = activeSectorInstances[0];
                activeSectorInstances.RemoveAt(0);

                if (loadedSectorHandles.Count > 0)
                {
                    var handle = loadedSectorHandles.Dequeue();
                    Addressables.ReleaseInstance(oldGo);
                    Addressables.Release(handle);
                }
            }
        }

        /// <summary>
        /// Asynchronously loads an entire game mode scene (e.g. Squish Lounge, Blind Box Parlor)
        /// </summary>
        public async Task LoadSceneAsync(string sceneAddressableKey, Action<float> onProgress = null)
        {
            AsyncOperationHandle<SceneInstance> sceneHandle = Addressables.LoadSceneAsync(
                sceneAddressableKey, 
                LoadSceneMode.Single
            );

            while (!sceneHandle.IsDone)
            {
                onProgress?.Invoke(sceneHandle.PercentComplete);
                await Task.Yield();
            }

            onProgress?.Invoke(1.0f);
        }
    }
}
