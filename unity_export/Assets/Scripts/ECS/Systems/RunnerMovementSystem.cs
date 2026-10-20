using Unity.Burst;
using Unity.Entities;
using Unity.Mathematics;
using Unity.Transforms;

namespace PelmenGame.ECS
{
    [BurstCompile]
    public partial struct RunnerMovementSystem : ISystem
    {
        [BurstCompile]
        public void OnCreate(ref SystemState state)
        {
            state.RequireForUpdate<PelmenRunnerTag>();
            state.RequireForUpdate<PelmenMoveData>();
        }

        [BurstCompile]
        public void OnUpdate(ref SystemState state)
        {
            float dt = SystemAPI.Time.DeltaTime;

            foreach (var (transform, moveData, haptics) in 
                     SystemAPI.Query<RefRW<LocalTransform>, RefRW<PelmenMoveData>, DynamicBuffer<HapticRequestBufferElement>>()
                     .WithAll<PelmenRunnerTag>())
            {
                ref var pos = ref transform.ValueRW.Position;
                ref var move = ref moveData.ValueRW;

                // 1. Forward progression
                pos.z += move.ForwardSpeed * dt;

                // 2. Smooth lane shift interpolation
                float targetX = move.TargetLane * move.LaneWidth;
                pos.x = math.lerp(pos.x, targetX, math.clamp(move.LaneChangeSpeed * dt, 0f, 1f));

                if (math.abs(pos.x - targetX) < 0.05f)
                {
                    move.CurrentLane = move.TargetLane;
                }

                // 3. Vertical jump / gravity / slam physics
                if (!move.IsGrounded)
                {
                    float currentGravity = move.IsSlamming ? move.SlamForce : move.Gravity;
                    move.VerticalVelocity += currentGravity * dt;
                    pos.y += move.VerticalVelocity * dt;

                    // Ground collision floor (y = 0.5f base half-height)
                    if (pos.y <= 0.5f)
                    {
                        pos.y = 0.5f;
                        move.VerticalVelocity = 0f;
                        move.IsGrounded = true;

                        if (move.IsSlamming)
                        {
                            move.IsSlamming = false;
                            // Request heavy vibration on slam landing
                            haptics.Add(new HapticRequestBufferElement 
                            { 
                                Pattern = HapticFeedbackPattern.HeavySlam, 
                                Intensity = 1.0f 
                            });
                        }
                    }
                }
            }
        }
    }
}
