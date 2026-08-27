# Observer Cam recording plan

## Product decision

Observer Cam should own a small real-time recorder for the view it already directs. It should not require ReplayMod or Flashback, and it should not attempt to become a replay editor. Optional replay-mod compatibility can come later through a narrow integration layer.

The proven export pattern is to capture rendered frames and stream raw pixels to an FFmpeg process. ReplayMod documents this model and its MP4/WebM/PNG presets, while Flashback exposes the same practical user choices: output path, resolution, framerate, container, codec, and optional audio. We can use those ideas without copying their GPL-licensed implementation.

Primary references:

- [ReplayMod documentation: rendering and FFmpeg](https://www.replaymod.com/docs/)
- [ReplayMod source and GPL-3.0 license](https://github.com/ReplayMod/ReplayMod)
- [Flashback export configuration](https://github.com/Moulberry/Flashback/blob/master/src/main/java/com/moulberry/flashback/configuration/FlashbackConfigV0.java)
- [FFmpeg raw-video codec documentation](https://www.ffmpeg.org/ffmpeg-codecs.html#rawvideo)

## Phase 0 — settings boundary (complete)

- Mod Menu chooses and persists the video output directory.
- **Open video folder** creates that directory if needed and opens it in the operating system's file manager.
- An empty setting resolves to `<game directory>/observercam/recordings`.
- The existing disk budget defaults to 3 GB and remains configurable from 0.5–100 GB.
- Video format is selectable now: MP4/H.264 (default and widest compatibility), MKV/H.264 (safer recovery), or WebM/VP9 (open web format).
- Instant replay is explicitly **off by default**. Its time-bounded or size-bounded rolling history is implemented in Phase 2A below.
- A synchronized recording-state signal drives the redstone-red Observer eye while recording.

## Phase 1 — narrow video-only MVP (implemented)

1. **Start recording** / **Stop recording** is available on the Mod Menu landing page and through a rebindable key which is deliberately unassigned by default.
2. Recording is allowed only while Observer POV is active. Losing the Observer, leaving the world, exiting POV, resizing the framebuffer, or shutting down finalizes the session safely.
3. The final Observer framebuffer is captured at the current window resolution. The default is 30 FPS with Minecraft's HUD/chat excluded; **Include HUD** enables the post-GUI capture point.
4. Minecraft's asynchronous screenshot readback performs the GPU transfer. This reuses the game's maintained render path and is less fragile around Sodium/Iris than owning a second OpenGL readback implementation.
5. RGBA frames pass through a three-frame bounded queue to a background FFmpeg writer. A slow encoder drops frames instead of blocking the render thread, and only a few missing timeline slots may be duplicated.
6. Fixed-rate raw frames are piped to a user-selected or PATH-resolved FFmpeg executable. MP4 and MKV use H.264 with `yuv420p`; WebM uses VP9. The command is assembled from fixed safe options rather than arbitrary user arguments.
7. Output uses unique, container-matched partial names, is moved to its final name on clean stop, and retains a readable FFmpeg log only on failure.
8. Free disk space and the configured Observer Cam storage budget are checked before and during recording. Recording stops before the cap and never deletes unrelated files.
9. A compact indicator shows recording/finalization state, elapsed time, estimated bytes, and dropped frames. The Observer's eye glows redstone red while capture is active.

Phase 1 deliberately excludes replay timelines, camera editing, 4K supersampling, 360-degree rendering, and bundled FFmpeg downloads.

## Phase 2A — instant replay (implemented)

- The feature remains disabled until the player opts in. Enabling it starts the same bounded capture pipeline whenever Observer POV is active.
- FFmpeg writes two-second independently finalized, keyframe-aligned segments into an Observer Cam-owned `.observercam-replay-buffer` directory.
- Exactly one selected retention rule applies: elapsed time or combined buffer size. The global 3 GB recording cap and free-disk reserve always remain absolute ceilings.
- Eviction selects only the oldest complete segments. The active segment, completed exports, unrelated files, unmarked directories, and another location's data are never deletion targets.
- `F9` and **Save recent footage** close the active segment, concatenate the retained sequence without re-encoding, save it in the selected MP4/MKV/WebM format, remove the successful private buffer, and resume automatically.
- A normal recording temporarily replaces the replay encoder rather than running two encoders at once. Replay buffering resumes after the normal video finalizes.
- Disconnect, POV exit, disabling the setting, and shutdown discard unsaved private buffer data. A failed export retains its marked session and diagnostics until the next clean buffer startup.

## Phase 2B — output quality (implemented)

- Current-window, 720p, and 1080p output presets. Fixed sizes preserve aspect ratio and letterbox only when necessary.
- High, Balanced, and Smaller Files presets backed by fixed codec-appropriate CRF values rather than arbitrary encoder arguments.
- The same resolution and quality snapshot applies to normal recording and every segment in an instant-replay session.
- Friendlier FFmpeg discovery/installation guidance, crash-recovery cleanup, and deeper encoder diagnostics.
- Explicit GPU encoder choices only after capability probing; software x264 remains the predictable fallback.

## Optional game audio (implemented; source supplied by Windows)

- Minecraft outputs through OpenAL. The safe cross-mod approach is to capture an operating-system loopback input and feed it to the same FFmpeg process as the existing video pipe.
- **Record audio** is off by default. The explicit picker lists only source names that look like game-output loopbacks; microphones are never auto-selected or shown as substitutes.
- Live recording maps video and audio into one process, resamples minor timestamp drift, and ends audio with the video. MP4/MKV use AAC at 192 kbps and WebM uses Opus at 160 kbps.
- Instant replay includes the same audio stream in every keyframed segment and stream-copies optional audio during export.
- Observer Cam does not install a virtual driver or replace Minecraft's OpenAL context. If Windows does not already expose Stereo Mix or another loopback input, recording remains video-only.

## Phase 3 — replay ecosystem compatibility

- Detect Flashback or ReplayMod without making either mandatory.
- Offer a camera-pose track/export bridge so their offline replay renderer can follow Observer Cam's documentary decisions.
- Keep live MP4 recording and replay-data recording as separate modes. A replay mod is better for re-editing and deterministic offline rendering; Observer Cam's recorder is better for immediately usable footage.
- Integrations must use public APIs or independently written adapters. Do not copy GPL implementation code into Observer Cam.

## Acceptance tests for the first recorder

- A 60-second 1080p/30 recording has correct duration and plays in Windows, VLC, and a browser upload flow.
- Walking, sprinting, jumping, menus, dimension changes, and stopping via key all finalize a playable file.
- The render thread stays responsive when the encoder is slower than capture.
- Cancelling the folder dialog changes nothing; selecting a folder survives restart.
- Starting without FFmpeg gives a clear setup message and creates no broken video.
- Reaching the storage cap stops cleanly and never deletes files.
- Disconnect, crash simulation, and forced FFmpeg termination leave at most a clearly named partial file plus an error log.

Instant-replay retention, ownership, command construction, real segment creation, and lossless MP4/WebM joining are now covered. The remaining manual acceptance check is an in-world retained-history test under the player's actual shaders and modpack.
