using Unity.Burst;
using Unity.Entities;
using Unity.Mathematics;
using Unity.Transforms;

namespace PelmenGame.ECS
{
    [BurstCompile]
    public partial struct SquishDeformationSystem : ISystem
    {
        [BurstCompile]
        public void OnCreate(ref SystemState state)
        {
            state.RequireForUpdate<SquishDeformation>();
        }

        [BurstCompile]
        public void OnUpdate(ref SystemState state)
        {
            float dt = SystemAPI.Time.DeltaTime;
            // Cap delta time to prevent spring explosions during frame spikes
            dt = math.min(dt, 0.033f);

            foreach (var (transform, squish, haptics) in 
                     SystemAPI.Query<RefRW<LocalTransform>, RefRW<SquishDeformation>, DynamicBuffer<HapticRequestBufferElement>>())
            {
                ref var sq = ref squish.ValueRW;
                ref var tr = ref transform.ValueRW;

                float3 currentScale = sq.DeformationScale;
                float3 targetScale = sq.TargetScale;

                if (sq.IsTouching)
                {
                    // Calculate compression based on touch pressure & delta
                    float pressAmount = math.clamp(sq.TouchPressure * 0.15f, 0f, sq.MaxSquishCompress);
                    
                    // Squash along Y axis, volume-preserving bulge along X and Z
                    float squishY = math.clamp(1.0f - pressAmount, 0.4f, 1.0f);
                    float volumePreserveXZ = math.sqrt(1.0f / squishY);

                    // Add lateral finger drag shearing
                    float dragX = sq.TouchDelta.x * 0.005f;
                    float dragZ = sq.TouchDelta.y * 0.005f;

                    targetScale = new float3(volumePreserveXZ + dragX, squishY, volumePreserveXZ + dragZ);

                    // If newly compressed past 20%, trigger a subtle squish haptic tick
                    if (pressAmount > 0.15f && sq.Velocity.y < -0.1f)
                    {
                        haptics.Add(new HapticRequestBufferElement
                        {
                            Pattern = HapticFeedbackPattern.LightTick,
                            Intensity = math.clamp(pressAmount * 2f, 0.2f, 0.8f)
                        });
                    }
                }

                // Spring-Damper simulation equation: F = -k * x - c * v
                float3 displacement = currentScale - targetScale;
                float3 springForce = -sq.SpringStiffness * displacement;
                float3 dampingForce = -sq.Damping * sq.Velocity;
                float3 acceleration = springForce + dampingForce;

                sq.Velocity += acceleration * dt;
                sq.DeformationScale += sq.Velocity * dt;

                // Apply deformation to entity local transform scale
                tr.Scale = sq.DeformationScale.y; // Uniform baseline
                // Custom shader matrix or vertex morphing consumes DeformationScale (x, y, z)
            }
        }
    }
}
