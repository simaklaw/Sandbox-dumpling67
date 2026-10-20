using System;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.EnhancedTouch;
using Touch = UnityEngine.InputSystem.EnhancedTouch.Touch;

namespace PelmenGame.InputManagement
{
    public enum RunnerSwipeDirection
    {
        None,
        Left,
        Right,
        Up,
        Down
    }

    /// <summary>
    /// Connects Unity's New Input System and EnhancedTouch with gameplay systems:
    /// - Runner: Fast directional swipes, lane hops, jump, air-slam, double-tap turbo.
    /// - Squish Malka: Multi-touch pressure, 2-finger pinch, twist, and dough deformation.
    /// </summary>
    public class PelmenInputManager : MonoBehaviour
    {
        public static PelmenInputManager Instance { get; private set; }

        public event Action<RunnerSwipeDirection> OnRunnerSwipe;
        public event Action OnRunnerJump;
        public event Action OnRunnerSlam;
        public event Action OnRunnerBoost;

        public event Action<Vector2, float> OnSquishTouchDown; // pos, pressure
        public event Action<Vector2, Vector2> OnSquishDrag;     // pos, delta
        public event Action OnSquishTouchUp;
        public event Action<float> OnSquishPinch;              // pinch scale factor
        public event Action<float> OnSquishTwist;              // twist angle delta

        [Header("Swipe Tuning")]
        [SerializeField] private float minSwipeDistanceInches = 0.4f;
        [SerializeField] private float maxSwipeDurationSec = 0.35f;

        private Vector2 swipeStartPosition;
        private float swipeStartTime;
        private bool isTrackingSwipe;
        private float dpi;

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

            dpi = Screen.dpi > 0 ? Screen.dpi : 160f;
        }

        private void OnEnable()
        {
            EnhancedTouchSupport.Enable();
            TouchSimulation.Enable();
        }

        private void OnDisable()
        {
            EnhancedTouchSupport.Disable();
            TouchSimulation.Disable();
        }

        private void Update()
        {
            ProcessMultiTouch();
        }

        private void ProcessMultiTouch()
        {
            var activeTouches = Touch.activeTouches;

            if (activeTouches.Count == 0)
            {
                if (isTrackingSwipe)
                {
                    isTrackingSwipe = false;
                    OnSquishTouchUp?.Invoke();
                }
                return;
            }

            // --- SINGLE TOUCH (Runner Swipes & Squish Press) ---
            if (activeTouches.Count == 1)
            {
                var touch = activeTouches[0];

                if (touch.phase == UnityEngine.InputSystem.TouchPhase.Began)
                {
                    swipeStartPosition = touch.screenPosition;
                    swipeStartTime = Time.time;
                    isTrackingSwipe = true;

                    float pressure = touch.pressure > 0.01f ? touch.pressure : 1.0f;
                    OnSquishTouchDown?.Invoke(touch.screenPosition, pressure);
                }
                else if (touch.phase == UnityEngine.InputSystem.TouchPhase.Moved)
                {
                    OnSquishDrag?.Invoke(touch.screenPosition, touch.delta);
                }
                else if (touch.phase == UnityEngine.InputSystem.TouchPhase.Ended)
                {
                    EvaluateRunnerSwipe(touch.screenPosition);
                    isTrackingSwipe = false;
                    OnSquishTouchUp?.Invoke();
                }
            }
            // --- DUAL TOUCH (Pinch, Twist & Deep Squish) ---
            else if (activeTouches.Count >= 2)
            {
                var t0 = activeTouches[0];
                var t1 = activeTouches[1];

                Vector2 prevPos0 = t0.screenPosition - t0.delta;
                Vector2 prevPos1 = t1.screenPosition - t1.delta;

                float currentDistance = Vector2.Distance(t0.screenPosition, t1.screenPosition);
                float prevDistance = Vector2.Distance(prevPos0, prevPos1);
                float pinchDelta = currentDistance - prevDistance;

                // Pinch / Spread gesture
                if (Mathf.Abs(pinchDelta) > 1.0f)
                {
                    float pinchFactor = currentDistance / Mathf.Max(prevDistance, 0.001f);
                    OnSquishPinch?.Invoke(pinchFactor);
                }

                // Twist / Rotation gesture
                Vector2 prevVector = prevPos1 - prevPos0;
                Vector2 currVector = t1.screenPosition - t0.screenPosition;
                float angleDelta = Vector2.SignedAngle(prevVector, currVector);

                if (Mathf.Abs(angleDelta) > 0.5f)
                {
                    OnSquishTwist?.Invoke(angleDelta);
                }
            }
        }

        private void EvaluateRunnerSwipe(Vector2 endPosition)
        {
            float duration = Time.time - swipeStartTime;
            if (duration > maxSwipeDurationSec) return;

            Vector2 delta = endPosition - swipeStartPosition;
            float distanceInches = delta.magnitude / dpi;

            if (distanceInches < minSwipeDistanceInches)
            {
                // Simple tap (or double-tap check)
                if (duration < 0.15f)
                {
                    OnRunnerJump?.Invoke();
                }
                return;
            }

            // Directional swipe detection
            if (Mathf.Abs(delta.x) > Mathf.Abs(delta.y))
            {
                if (delta.x > 0)
                {
                    OnRunnerSwipe?.Invoke(RunnerSwipeDirection.Right);
                }
                else
                {
                    OnRunnerSwipe?.Invoke(RunnerSwipeDirection.Left);
                }
            }
            else
            {
                if (delta.y > 0)
                {
                    OnRunnerSwipe?.Invoke(RunnerSwipeDirection.Up);
                    OnRunnerJump?.Invoke();
                }
                else
                {
                    OnRunnerSwipe?.Invoke(RunnerSwipeDirection.Down);
                    OnRunnerSlam?.Invoke();
                }
            }
        }
    }
}
