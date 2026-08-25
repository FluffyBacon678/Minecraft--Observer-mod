# Observer Cam

<img src="src/client/resources/assets/observercam/icon.png" alt="Observer Cam project artwork" width="320">

Observer Cam is a Fabric mod that adds an autonomous, floating Observer-block cameraman. The visible Observer is the camera: its observing face supplies the exact position, yaw, and pitch used by Observer POV.

## Requirements

- Minecraft Java Edition **1.21.11**
- Fabric Loader **0.18.2 or newer**
- Fabric API
- Java **21**
- Mod Menu 17.x (optional, for the settings button)

The movement authority lives on the server, so multiplayer servers must install Observer Cam and Fabric API. Clients need the mod to render the entity and use Observer POV.

## Commands and controls

- `/observercam summon` — creates an Observer, targeting the executing player.
- `/observercam target <player>` — assigns the nearest Observer to a player.
- `/observercam follow` — targets the executing player and starts following; summons an Observer if needed.
- `/observercam dismiss` — removes the nearest Observer.
- `/observercam view` — toggles the executing player's Observer POV.
- `O` — toggles Observer POV by default and can be rebound under Observer Cam controls.

With Mod Menu installed, **Mods → Observer Cam → Configure → Behavior → Cameraman enabled** is the simplest lifecycle control. Switching it on creates or resumes one Observer assigned to you; switching it off dismisses your Observers and restores normal POV. The preference is applied again when you join a world. Commands remain available as testing and administration alternatives.

If the viewed Observer is removed, unloaded, or changes dimension, the camera immediately returns to the real player. Player input is never transferred to the Observer.

## Cameraman behavior

The director plans a shot every four game ticks and moves every tick. It generates 18 positions around the predicted player location and considers:

- four-point player visibility (head through legs), with limited leaf tolerance;
- one-block camera clearance and direct path obstruction;
- adaptive indoor/outdoor distance and height;
- projected player screen size for the configured FOV;
- five inexpensive background-depth rays;
- continuity, minimum shot duration, and a side-switch threshold.

Normal movement is acceleration- and speed-limited. Emergency repositioning is reserved for large teleports or unrecoverable separation.

## Configuration

Open **Mods → Observer Cam → Configure** when Mod Menu is installed. Camera, Movement, Cinematography, Behavior, Recording (Planned), and Debug settings are grouped into separate pages, saved to `config/observercam.json`, and applied live when safe. Hover any control for a plain-language explanation; numeric settings display their units, and **Reset Defaults** asks for confirmation before replacing the configuration.

Observer Cam does not record video in this MVP. A storage-budget guard is nevertheless present for that future subsystem and defaults to **3 GB**. Any future recording writer must check this guard before allocating data, so the configured cap cannot be silently exceeded.

The debug HUD reports the current director state, distance, target, visibility, score, indoor estimate, speed, and candidate count. Debug candidate/raycast visualization uses colored particles:

- green — selected position;
- white — valid candidate;
- red — blocked candidate;
- orange — weak visibility;
- cyan — selected camera-to-focus ray.

## Build and run

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The distributable jar is written to `build/libs/observercam-0.1.0.jar`.

The supplied project artwork is packaged as the Mod Menu icon at `src/client/resources/assets/observercam/icon.png`; the same original-resolution PNG can be uploaded as the Modrinth project thumbnail.

See [Testing](docs/TESTING.md) for the recommended hands-on pass, [Architecture](docs/ARCHITECTURE.md) for implementation details, and [Research](docs/RESEARCH.md) for source-review notes.
