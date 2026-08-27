# Observer Cam hands-on testing

The next useful step is a real gameplay pass with the freshly built jar. Begin with default settings so any camera behavior is reproducible.

## Setup

1. Install Minecraft Java 1.21.11, Fabric Loader 0.18.2 or newer, Fabric API, Observer Cam, and optionally Mod Menu 17.x. Install FFmpeg or select `ffmpeg.exe` in Recording settings before the recording checks.
2. Use a disposable world or back up the test world.
3. Run `/gamemode creative` before movement tests so camera evaluation is not interrupted by player death.
4. In **Mods → Observer Cam → Configure**, choose **Reset Defaults** and confirm.
5. Use **Cameraman enabled** on the configuration landing page. The Observer should spawn and begin following without commands.
6. Use **Enter Observer POV** on that same page, then verify both it and `O` enter and leave Observer POV.

## Configuration checks

- Mod Menu shows the supplied Observer Cam artwork.
- **Configure** shows direct cameraman, POV, recording, and Observer PiP actions plus the eight category pages in a compact two-column layout.
- Every slider and toggle has a helpful hover description.
- Values show useful units, including blocks, degrees, ticks, and percent.
- Changed values survive leaving and reopening the screen.
- Cancelling **Reset Defaults** preserves the current values; confirming it restores defaults.
- Switching **Cameraman enabled** off dismisses the Observer and immediately restores normal POV.
- Rejoining with the setting enabled creates or resumes exactly one Observer for the player.
- Repeatedly enabling the cameraman or using summon commands before re-enabling still converges to exactly one assigned Observer.
- **Video Quality** defaults to MP4/H.264, Current Window, Balanced, 30 FPS, and no HUD. **Recording** defaults to a 3 GB combined output-folder cap.
- Clicking **Video output folder** opens a native folder chooser. Cancelling preserves the old path; selecting a folder updates the button and survives restarting the client.
- Clicking **FFmpeg executable** can select `ffmpeg.exe`; cancelling preserves the previous choice.
- **Record audio** is Off after Reset Defaults. Clicking **Audio source** lists only Windows game-audio loopback inputs; if none exists, it explains that recording stays video-only and never offers a microphone as a substitute.
- **Open video folder** creates and opens the resolved output directory.
- **Instant Replay** is Off after Reset Defaults, offers Time or Size retention, and exposes **Save recent footage** without hiding the `F9` binding.
- **Observer picture-in-picture** is Off after Reset Defaults. Its recording and PiP controls are unbound by default and therefore cannot replace an existing key; both remain usable from Mod Menu.

## Picture-in-picture checks

1. Use the normal player camera and click **Show Observer PiP**. Confirm a live 16:9 Observer feed appears at the upper-left while movement, mining, and placement still control the player.
2. Sprint, jump, and turn quickly. The feed should update smoothly enough to monitor framing without changing the main camera.
3. Enter full Observer POV and confirm the duplicate PiP hides; exit POV and confirm it returns.
4. Hide Minecraft's HUD and confirm the PiP hides with it. Show the HUD and confirm it returns.
5. Disable PiP and confirm its texture disappears immediately. Rejoin the world and confirm the saved preference is respected.
6. Test once with shaders. PiP intentionally renders a second world view at 10 FPS; leave it disabled if the shader cost is too high.

## Recording checks

1. Bind **Start/stop Observer recording** under Observer Cam controls or use the Mod Menu button. Enter Observer POV, record normal movement for 20 seconds, then stop. Wait for the saved message before closing Minecraft.
2. Confirm the Observer eye glows red while recording, the compact REC indicator advances, and the resulting video has the expected duration, view, and smooth playback.
3. Repeat with **Include HUD** enabled and confirm HUD/chat inclusion changes without changing the Observer view.
4. Repeat once with MKV and once with WebM; confirm every clean stop produces the selected extension and no `.partial` file or FFmpeg log remains.
5. Repeat with 720p and 1080p. Confirm the files report exactly 1280×720 and 1920×1080, with no stretched image when the game window is not 16:9.
6. Compare High, Balanced, and Smaller Files on the same 20-second scene. High should retain the most detail; Smaller Files should normally use less disk.
7. Temporarily select a missing executable. Starting should show a clear error and create no broken final video.
8. Resize the window during a short recording. It should stop and finalize rather than feed mixed frame sizes to FFmpeg.
9. Set a small cap near current folder usage. Recording should refuse to start or stop safely before exceeding it, without deleting existing files.
10. Exit Observer POV, dismiss the Observer, disconnect, and close Minecraft during separate short sessions. Each should stop/finalize safely.

If a recording fails, keep the `.partial` file and matching `.ffmpeg.log`; those are the most useful diagnostics.

## Instant replay checks

1. Leave **Instant replay buffer** Off, enter Observer POV, and confirm no REPLAY indicator appears and `F9` explains that the feature is disabled.
2. Enable it with the Time limit at 0.5 minutes. In Observer POV, confirm the red eye and REPLAY indicator appear automatically.
3. Film for at least 45 seconds, press `F9`, wait for **Instant replay saved**, and confirm the output contains approximately the latest 30 seconds rather than the full 45 seconds.
4. Keep playing after the save and confirm buffering resumes automatically. Save again and verify the second file contains only footage recorded after buffering resumed.
5. Select the Size limit, choose the minimum cap, and confirm old segments disappear while the indicator remains active and the global recording folder cap is never crossed.
6. Start a normal recording from Mod Menu or your assigned key while replay is buffering. The replay indicator should pause, the normal REC indicator should take over, and replay should resume after the normal video is saved.
7. Exit POV without saving and confirm the private buffer disappears. Completed replay videos must remain untouched.
8. Repeat the save once for MP4, MKV, and WebM. Check duration, seeking, first/last frames, and that no unexpected re-encoding delay occurs.

If a replay export fails, keep its FFmpeg log and private marked buffer for diagnosis. The next clean game session may remove stale private buffer data, so copy it before retrying if recovery matters.

If Windows already exposes Stereo Mix or another loopback source, select it, enable **Record audio**, and repeat one live recording plus one replay save. Confirm the file has synchronized game sound. On systems without a built-in loopback source, leave audio Off; video and replay should work normally without an extra driver.

## Core experience checks

In every scenario, verify that the visible Observer face matches its POV, the real player remains controllable, mining/placing uses the real player's reach and highlighted target, and pressing `O` safely returns the camera to the player.

- Open terrain: walk, sprint, jump, turn sharply, mine, and place blocks.
- Ensemble shot: stand near several visible mobs or another player, then separate them around the scene. The camera should include useful nearby subjects when practical without losing or shrinking the tracked player excessively.
- House: move through rooms and doorways.
- Narrow hallway: walk toward and away from walls; the Observer should hold briefly, then recover to a clear visible position rather than repeatedly pushing toward an unreachable shot across a wall.
- Blocked route: wedge the Observer's route against a corner or close a door across its flight path. It should detect the lack of progress and recover after roughly half a second instead of remaining stuck.
- Forest: pass behind trunks and through leaves.
- Cave: test low ceilings, corners, and uneven floors.
- Water: swim at the surface and underwater.
- Teleport: use a short teleport and a long teleport.
- Dimension change: use a Nether portal while followed and while in Observer POV.
- Recovery: dismiss the viewed Observer and confirm the player camera restores immediately.

## Cinematography notes

Watch for the following rather than only checking whether the feature functions:

- Does the player remain fully or mostly visible?
- Can the player drift naturally within the middle region instead of being locked to the exact center during every footstep and jump?
- Do nearby visible players or mobs influence a wider documentary composition without becoming the main subject?
- Is there useful world context behind and around the player?
- Does the Observer hold a decent shot for several seconds and introduce alternate angles only occasionally instead of constantly swapping sides?
- Are ordinary corrections smooth and visibly flown rather than teleported?
- In Observer POV, does the camera remain fluid between game ticks at high frame rates, without a tiny 20 TPS stepping sensation?
- Does it close in naturally indoors and widen the shot outdoors?
- Does it recover promptly from a blocked view without clipping through walls?

Enable **Debug HUD**, **Show candidate positions**, and **Show selected position** only when investigating a specific problem. A short video plus the HUD values is the most useful evidence for camera-quality tuning.

## Recommended continuation

1. Fix any crashes, broken controls, camera restoration failures, or Observer/POV alignment problems first.
2. Tune clipping and line-of-sight recovery using hallway, forest, and cave results.
3. Tune shot stability and background composition using outdoor movement clips.
4. Repeat the dimension, teleport, and disconnect recovery checks.
5. After the core cameraman and the 20-second recording pass are reliable, prepare the first Modrinth release entry and upload the packaged artwork as its thumbnail.

When reporting a problem, include the scenario, commands used, relevant changed settings, expected behavior, actual behavior, and a screenshot or short clip when possible.
