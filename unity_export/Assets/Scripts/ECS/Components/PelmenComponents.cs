using Unity.Entities;
using Unity.Mathematics;

namespace PelmenGame.ECS
{
    public enum PelmenRarity : byte
    {
        Common = 0,
        Uncommon = 1,
        Rare = 2,
        Epic = 3,
        Legendary = 4,
        Mythic = 5
    }

    public enum HapticFeedbackPattern : byte
    {
        None = 0,
        LightTick = 1,
        MediumImpact = 2,
        HeavySlam = 3,
        BlindBoxCrack = 4,
        BlindBoxPop = 5,
        CoinCollect = 6
    }

    // Tag for player runner entity
    public struct PelmenRunnerTag : IComponentData { }

    // Runner movement state & configuration
    public struct PelmenMoveData : IComponentData
    {
        public int CurrentLane;        // -1 (Left), 0 (Center), 1 (Right)
        public int TargetLane;         // -1, 0, 1
        public float LaneWidth;        // Default 2.5m
        public float LaneChangeSpeed;  // E.g. 15.0f
        public float ForwardSpeed;      // Current forward velocity (m/s)
        public float BaseSpeed;         // Baseline speed (e.g. 12.0f)
        public float MaxSpeed;          // Max cap with boosts (e.g. 28.0f)
        public float VerticalVelocity;  // Velocity for jump/gravity (m/s)
        public float JumpForce;         // Default 14.0f
        public float Gravity;           // Default -35.0f
        public bool IsGrounded;
        public bool IsSlamming;         // Air down-slam state
        public float SlamForce;         // Fast-fall force e.g. -45.0f
    }

    // Squish-malka 3D deformation & physical spring damper
    public struct SquishDeformation : IComponentData
    {
        public float3 DeformationScale; // Current squish scale (X, Y, Z)
        public float3 TargetScale;      // Target rest scale (1, 1, 1)
        public float3 Velocity;         // Spring oscillation velocity
        public float SpringStiffness;   // Elasticity factor (e.g. 180.0f)
        public float Damping;           // Damping factor (e.g. 14.0f)
        public float MaxSquishCompress; // Max squish compression limit (e.g. 0.35f)
        public float TouchPressure;     // Current touch pressure (0.0 to 2.5f)
        public float2 TouchDelta;       // Movement vector from touch swipe
        public bool IsTouching;         // Currently touched/grabbed
    }

    // Metadata for the 67 unlocked / active Pelmen variants
    public struct PelmenVariantData : IComponentData
    {
        public int VariantId;           // 1 to 67
        public PelmenRarity Rarity;
        public float DoughElasticity;   // Affects squash restitution
        public float FlavorBonusRate;   // Score multiplier
        public float MagnetRadius;      // Passive item pull radius
    }

    // Buffer element for pending haptic feedback requests sent to Android Mobile Bridge
    [InternalBufferCapacity(4)]
    public struct HapticRequestBufferElement : IBufferElementData
    {
        public HapticFeedbackPattern Pattern;
        public float Intensity;         // 0.0f to 1.0f
    }

    // Sector track chunk for endless runner loading/despawning
    public struct SectorChunkData : IComponentData
    {
        public int SectorIndex;
        public float LengthZ;
        public float StartZ;
        public float EndZ;
        public bool IsActive;
    }
}
