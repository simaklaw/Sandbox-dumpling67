using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.AddressableAssets;
using UnityEngine.ResourceManagement.AsyncOperations;

namespace PelmenGame.AddressablesManagement
{
    [System.Serializable]
    public class PelmenAssetEntry
    {
        public int variantId;
        public string codename;
        public string displayName;
        public string addressableKey;
    }

    /// <summary>
    /// Loads and manages the 67 Pelmen-Six variants from Addressables storage.
    /// Supports asset memory caching, lazy loading, and release.
    /// </summary>
    public class PelmenCatalogAddressables : MonoBehaviour
    {
        public static PelmenCatalogAddressables Instance { get; private set; }

        private readonly Dictionary<int, GameObject> loadedPrefabs = new();
        private readonly Dictionary<int, AsyncOperationHandle<GameObject>> activeHandles = new();

        public const int TOTAL_VARIANTS = 67;

        private void Awake()
        {
            if (Instance == null) Instance = this;
            else Destroy(gameObject);
        }

        public string GetAddressableKeyForVariant(int variantId)
        {
            return $"Pelmen_Variant_{variantId:D2}";
        }

        /// <summary>
        /// Asynchronously loads the 3D Prefab for any of the 67 Pelmen models.
        /// </summary>
        public async Task<GameObject> GetPelmenPrefabAsync(int variantId)
        {
            if (loadedPrefabs.TryGetValue(variantId, out GameObject cached))
            {
                return cached;
            }

            string key = GetAddressableKeyForVariant(variantId);
            AsyncOperationHandle<GameObject> handle = Addressables.LoadAssetAsync<GameObject>(key);
            activeHandles[variantId] = handle;

            await handle.Task;

            if (handle.Status == AsyncOperationStatus.Succeeded)
            {
                loadedPrefabs[variantId] = handle.Result;
                return handle.Result;
            }
            else
            {
                Debug.LogWarning($"[Addressables] Variant {variantId} not found with key {key}. Falling back to default.");
                return null;
            }
        }

        /// <summary>
        /// Unload unneeded 3D assets to free mobile RAM
        /// </summary>
        public void ReleaseVariant(int variantId)
        {
            if (activeHandles.TryGetValue(variantId, out var handle))
            {
                Addressables.Release(handle);
                activeHandles.Remove(variantId);
                loadedPrefabs.Remove(variantId);
            }
        }

        private void OnDestroy()
        {
            foreach (var handle in activeHandles.Values)
            {
                Addressables.Release(handle);
            }
            activeHandles.Clear();
            loadedPrefabs.Clear();
        }
    }
}
