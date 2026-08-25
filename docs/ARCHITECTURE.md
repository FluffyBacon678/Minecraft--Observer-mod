# Observer Cam architecture

## Observer entity

`ObserverCameraEntity` is a 0.96 × 0.96 block-sized, no-gravity entity registered on both logical sides. It is invulnerable, non-pickable, non-pushable, ignores block triggers, emits no movement events, and has no loot table. The server owns its target, movement, and shot state. Target identity and compact HUD diagnostics are synchronized through entity data and target/follow state is persisted.

The client renderer submits Minecraft's existing `Blocks.OBSERVER` moving-block model. No Observer texture or model is duplicated. The canonical model faces south (+Z); the entity's interpolated yaw and pitch rotate the entire cube so the observing face follows the camera forward vector.

## Authoritative camera transform

`CameraTransform` is the only place that defines Observer camera geometry. It interpolates the physical block center, calculates Minecraft's yaw/pitch forward vector, and moves the origin 0.515 blocks outside the observing face. `CameraMixin` applies that origin after vanilla camera setup. Rendering and aiming use the same entity rotations, so the visible face and POV cannot drift apart.

`ObserverPovController` sets the Observer as Minecraft's camera entity while leaving `Minecraft.player` unchanged. This preserves movement, mining, placement, inventory, and interaction control. It remembers the previous perspective and restores it whenever POV is toggled off or the Observer becomes invalid.

## Planning and scoring

`CameraDirector` runs `CandidateGenerator` every four ticks (five plans per second). Candidate generation predicts a few player-velocity ticks ahead, estimates enclosure, blends indoor/outdoor distance, and creates 18 side/front/rear/elevated positions without touching unloaded chunks.

Each candidate is evaluated by:

1. `VisibilityProbe` — four collision raycasts at head, torso, waist, and leg heights. Up to two leaf blocks may be crossed before a sample is considered hidden.
2. destination AABB clearance and a direct movement-path ray;
3. distance and projected framing for the selected camera FOV;
4. `EnvironmentProbe` — sky, ceiling, nearby wall, and five background-depth rays;
5. `ShotScorer` — visibility-first weighted scoring with continuity and front-shot policy.

The director maintains a selected angle. It updates the destination within that shot, but only changes angles after the minimum shot duration and a configurable score improvement. A failing incumbent can force recovery. The small state set is `HOLD`, `REFRAME`, `REPOSITION`, `CATCH_UP`, `RECOVER_LOS`, and `EMERGENCY_RECOVERY`.

## Motion

`MotionMath` produces a velocity target from position error, then applies acceleration and speed limits. `MotionController` moves through Minecraft collision resolution each tick and damps the actual resulting velocity. Rotation uses wrapped yaw/pitch error with smoothing plus a per-tick speed cap. Separation beyond the emergency threshold permits one teleport to the planned clear destination.

## Client systems

- `ObserverCameraRenderer` renders the vanilla Observer block.
- `ObserverPovController` owns safe POV entry and restoration.
- `GameRendererMixin` applies configured FOV only while the Observer is the camera.
- `ObserverDebugHud` renders compact synchronized shot diagnostics.
- `ObserverDebugRenderer` recomputes the low-rate client plan and visualizes it with optional particles.
- `ObserverCamConfigScreen` provides lightweight built-in pages; `ObserverCamModMenu` exposes them through optional Mod Menu integration.

The Mod Menu **Cameraman enabled** preference is sent as a small client-to-server payload. The server only creates, resumes, or dismisses Observer entities assigned to the requesting player. Disabling also sends an explicit camera-restore payload, so it cannot leave the client attached to a removed Observer. The preference is resent on world join and remains useful in multiplayer without granting command permissions.

## Future recording storage boundary

Recording remains outside this MVP. `RecordingStorageBudget` is a deliberately small guard for a future writer: it measures existing files without loading them into memory, converts the configured gigabyte limit to bytes, reports remaining capacity, and rejects allocations that would cross the cap. The default is 3 GB and the Mod Menu range is 0.5–100 GB. No recording directory or recording data is created by the current mod.

## Performance boundaries

Planning is fixed at five times per second; motion and safety checks run once per game tick; entity position and rotation are interpolated by vanilla every frame. Candidate probes only examine loaded chunks around the target. No pathfinder, chunk loader, image analysis, recorder, encoder, or background worker is present.
