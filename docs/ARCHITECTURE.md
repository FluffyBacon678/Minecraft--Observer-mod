# Observer Cam architecture

## Observer entity

`ObserverCameraEntity` is a 0.96 × 0.96 block-sized, no-gravity entity registered on both logical sides. It is invulnerable, non-pickable, non-pushable, ignores block triggers, emits no movement events, and has no loot table. The server owns its owner, target, movement, and shot state. Owner and target are deliberately separate so retargeting a camera cannot transfer lifecycle control to another player. Both identities, follow state, and compact HUD diagnostics are synchronized and persisted.

The client renderer submits Minecraft's existing `Blocks.OBSERVER` moving-block model. No Observer texture or model is duplicated. The canonical model faces south (+Z); the entity's interpolated yaw and pitch rotate the entire cube so the observing face follows the camera forward vector.

## Authoritative camera transform

`CameraTransform` is the only place that defines Observer camera geometry. It interpolates the physical block center, calculates Minecraft's yaw/pitch forward vector, and moves the origin 0.515 blocks outside the observing face. `CameraMixin` applies that origin after vanilla camera setup. Rendering and aiming use the same entity rotations, so the visible face and POV cannot drift apart.

`ObserverPovController` sets the Observer as Minecraft's camera entity while leaving `Minecraft.player` unchanged. `LocalPlayerMixin` keeps the real player recognized as the controlled camera owner, preserving movement, mining, placement, inventory, and interaction input. `LevelRendererMixin` explicitly extracts the local player's render state while Observer POV is active and suppresses only its floating name tag. The controller remembers the previous perspective and restores it whenever POV is toggled off, the Observer becomes invalid, or the connection closes.

## Planning and scoring

`CameraDirector` runs `CandidateGenerator` every four ticks (five plans per second). `DocumentaryFraming` maintains horizontal and vertical soft zones, damps jump response, and bounds changes to the planning heading. Candidate generation predicts a few horizontal player-velocity ticks ahead, estimates enclosure, blends indoor/outdoor distance, and creates 18 side/front/rear/elevated positions without touching unloaded chunks. It considers at most four nearby visible living subjects, modestly shifts and widens the group composition, and always keeps the tracked player weighted as the primary subject.

Each candidate is evaluated by:

1. `VisibilityProbe` — four collision raycasts at head, torso, waist, and leg heights. Up to two leaf blocks may be crossed before a sample is considered hidden.
2. destination AABB clearance and a direct movement-path ray, which is required during normal flight;
3. distance and projected framing for the selected camera FOV;
4. `EnvironmentProbe` — sky, ceiling, nearby wall, and five background-depth rays;
5. `ShotScorer` — visibility-first weighted scoring with continuity, front-shot policy, and a bounded bonus for including nearby secondary subjects.

The director maintains a selected angle. With default stability it holds an angle for roughly nine seconds before accepting a meaningfully better shot, and after roughly 15–18 seconds it may introduce a comparable alternate view. Immediate side changes are reserved for an invalid path or serious visibility failure. Low-scoring but valid shots first reframe within the held angle. If no reachable candidate exists, the Observer holds instead of continuing toward a stale blocked destination; after three failed planning passes it promotes a clear, visible candidate to a recovery teleport. Motion that makes no useful progress for ten ticks triggers the same recovery. During normal movement, nine body-width path probes prevent the block-sized camera from clipping a corner that only its center ray could clear. The small state set is `HOLD`, `REFRAME`, `REPOSITION`, `CATCH_UP`, `RECOVER_LOS`, and `EMERGENCY_RECOVERY`.

## Motion

`MotionMath` produces a velocity target from position error, then applies acceleration and speed limits. `MotionController` moves through Minecraft collision resolution each tick and damps the actual resulting velocity. Rotation uses wrapped yaw/pitch error with smoothing plus a per-tick speed cap. Separation beyond the emergency threshold permits one teleport to the planned clear destination.

## Client systems

- `ObserverCameraRenderer` renders the vanilla Observer block.
- `ObserverPovController` owns safe POV entry and restoration.
- `GameRendererMixin` applies configured FOV only while the Observer is the camera and performs interaction picking from the real player's eyes and look direction, so mining, placement, attacks, and use remain server-valid in detached POV.
- `LocalPlayerMixin` preserves normal player control while viewing through the Observer.
- `LevelRendererMixin` keeps the local player visible in the detached POV without a name tag.
- `ObserverDebugHud` renders compact synchronized shot diagnostics.
- `ObserverDebugRenderer` recomputes the low-rate client plan and visualizes it with optional particles.
- `ObserverCamConfigScreen` provides lightweight built-in pages; `ObserverCamModMenu` exposes them through optional Mod Menu integration.

The Mod Menu landing page exposes both **Cameraman enabled** and **Enter/Exit Observer POV** as quick actions. A short spawn-sync wait is shown as **Cancel POV Request** rather than looking like a failed click. The enabled preference and a bounded camera-settings snapshot are sent to the server. Runtime settings are isolated by owner UUID and validated against the same limits as the UI, so dedicated-server directors honor live settings without one player changing another player's camera. The server retains exactly one Observer owned by the requesting player across all dimensions and removes that owner's stale duplicates without touching cameras merely targeted at them. Disabling or disconnecting removes the owner's camera wherever it is and restores normal POV.

## Future recording storage boundary

Recording remains outside this MVP. `RecordingStorageBudget` is a deliberately small guard for a future writer: it measures existing files without loading them into memory, converts the configured gigabyte limit to bytes, reports remaining capacity, and rejects allocations that would cross the cap. The default is 3 GB and the Mod Menu range is 0.5–100 GB. No recording directory or recording data is created by the current mod.

## Performance boundaries

Planning is fixed at five times per second; motion and safety checks run once per game tick; entity position and rotation are interpolated by vanilla every frame. Candidate probes only examine loaded chunks around the target. No pathfinder, chunk loader, image analysis, recorder, encoder, or background worker is present.
