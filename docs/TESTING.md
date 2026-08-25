# Observer Cam hands-on testing

The next useful step is a real gameplay pass with the freshly built jar. Begin with default settings so any camera behavior is reproducible.

## Setup

1. Install Minecraft Java 1.21.11, Fabric Loader 0.18.2 or newer, Fabric API, Observer Cam, and optionally Mod Menu 17.x.
2. Use a disposable world or back up the test world.
3. In **Mods → Observer Cam → Configure**, choose **Reset Defaults** and confirm.
4. Open **Behavior** and switch **Cameraman enabled** on. The Observer should spawn and begin following without commands.
5. Press `O` to enter and leave Observer POV.

## Configuration checks

- Mod Menu shows the supplied Observer Cam artwork.
- **Configure** opens the six category pages.
- Every slider and toggle has a helpful hover description.
- Values show useful units, including blocks, degrees, ticks, and percent.
- Changed values survive leaving and reopening the screen.
- Cancelling **Reset Defaults** preserves the current values; confirming it restores defaults.
- Switching **Cameraman enabled** off dismisses the Observer and immediately restores normal POV.
- Rejoining with the setting enabled creates or resumes exactly one Observer for the player.
- **Recording (Planned)** defaults to a 3 GB storage cap and clearly states that recording is not implemented.

## Core experience checks

In every scenario, verify that the visible Observer face matches its POV, the real player remains controllable, and pressing `O` safely returns the camera to the player.

- Open terrain: walk, sprint, jump, turn sharply, mine, and place blocks.
- House: move through rooms and doorways.
- Narrow hallway: walk toward and away from walls.
- Forest: pass behind trunks and through leaves.
- Cave: test low ceilings, corners, and uneven floors.
- Water: swim at the surface and underwater.
- Teleport: use a short teleport and a long teleport.
- Dimension change: use a Nether portal while followed and while in Observer POV.
- Recovery: dismiss the viewed Observer and confirm the player camera restores immediately.

## Cinematography notes

Watch for the following rather than only checking whether the feature functions:

- Does the player remain fully or mostly visible?
- Is there useful world context behind and around the player?
- Does the Observer hold a decent shot instead of constantly swapping sides?
- Are ordinary corrections smooth and visibly flown rather than teleported?
- Does it close in naturally indoors and widen the shot outdoors?
- Does it recover promptly from a blocked view without clipping through walls?

Enable **Debug HUD**, **Show candidate positions**, and **Show selected position** only when investigating a specific problem. A short video plus the HUD values is the most useful evidence for camera-quality tuning.

## Recommended continuation

1. Fix any crashes, broken controls, camera restoration failures, or Observer/POV alignment problems first.
2. Tune clipping and line-of-sight recovery using hallway, forest, and cave results.
3. Tune shot stability and background composition using outdoor movement clips.
4. Repeat the dimension, teleport, and disconnect recovery checks.
5. Only after the core cameraman feels reliable, prepare the first Modrinth release entry and upload the packaged artwork as its thumbnail.

When reporting a problem, include the scenario, commands used, relevant changed settings, expected behavior, actual behavior, and a screenshot or short clip when possible.
