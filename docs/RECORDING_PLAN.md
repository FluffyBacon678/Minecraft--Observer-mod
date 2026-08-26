# Observer Cam recording plan

## Product decision

Observer Cam should own a small real-time recorder for the view it already directs. It should not require ReplayMod or Flashback, and it should not attempt to become a replay editor. Optional replay-mod compatibility can come later through a narrow integration layer.

The proven export pattern is to capture rendered frames and stream raw pixels to an FFmpeg process. ReplayMod documents this model and its MP4/WebM/PNG presets, while Flashback exposes the same practical user choices: output path, resolution, framerate, container, codec, and optional audio. We can use those ideas without copying their GPL-licensed implementation.

Primary references:

- [ReplayMod documentation: rendering and FFmpeg](https://www.replaymod.com/docs/)
- [ReplayMod source and GPL-3.0 license](https://github.com/ReplayMod/ReplayMod)
- [Flashback export configuration](https://github.com/Moulberry/Flashback/blob/master/src/main/java/com/moulberry/flashback/configuration/FlashbackConfigV0.java)
- [FFmpeg raw-video codec documentation](https://www.ffmpeg.org/ffmpeg-codecs.html#rawvideo)

## Phase 0 — settings boundary (current)

- Mod Menu chooses and persists the video output directory.
- **Open video folder** creates that directory if needed and opens it in the operating system's file manager.
- An empty setting resolves to `<game directory>/observercam/recordings`.
- The existing disk budget defaults to 3 GB and remains configurable from 0.5–100 GB.
- Video format is selectable now: MP4/H.264 (default and widest compatibility), MKV/H.264 (safer recovery), or WebM/VP9 (open web format).
- Instant replay is explicitly **off by default**. Its settings support a time-bounded or size-bounded rolling history; the recorder will activate them in Phase 2.
- A synchronized recording-state signal and redstone-red Observer eye light are ready for the recorder to switch on and off.
- No recorder thread, FFmpeg process, or output file exists yet.

## Phase 1 — narrow video-only MVP

1. Add **Start recording** / **Stop recording** to the Mod Menu landing page and a rebindable key.
2. Record only while Observer POV is active. If the Observer disappears, the world closes, or POV exits, finalize the recording safely.
3. Capture the final Observer framebuffer at the current window resolution. Default to 30 FPS and exclude Minecraft HUD/chat unless the user enables an **Include HUD** setting.
4. Use a double-buffered OpenGL pixel readback so rendering is not stalled for every frame.
5. Pass BGRA frames through a small bounded queue to a background writer. The render thread must never wait for disk or encoder I/O.
6. Pipe fixed-rate raw frames to a user-installed or auto-detected FFmpeg executable. Map MP4 and MKV to H.264 with `yuv420p`; map WebM to VP9. Reject unsupported combinations before capture starts.
7. Write to a unique container-matched partial name, finalize atomically on clean stop, and retain a readable error log if FFmpeg fails.
8. Check free disk space and the Observer Cam storage budget before starting and while writing. Stop cleanly before the configured cap; never delete unrelated files automatically.
9. Show a compact recording indicator, elapsed time, estimated bytes used, and dropped-frame count.

Phase 1 deliberately excludes audio, replay timelines, camera editing, 4K supersampling, 360-degree rendering, and bundled FFmpeg downloads.

## Phase 2 — quality and reliability

- 30/60 FPS and 720p/1080p/current-window presets.
- Quality presets backed by CRF rather than asking users for raw encoder arguments.
- Frame pacing that duplicates the last good frame when capture misses a deadline, preserving real-time duration and A/V compatibility.
- Optional game-audio capture and AAC muxing after video-only stability is proven.
- Encoder discovery/status screen, cancel/failure handling, crash-recovery cleanup, and an **Open output folder** action.
- Explicit GPU encoder choices only after capability probing; software x264 remains the predictable fallback.

### Instant replay implementation

1. Keep the feature disabled until the player opts in. Enabling it starts the same bounded capture pipeline as normal recording, but writes short, independently finalized segments into an Observer Cam-owned `.replay-buffer` directory.
2. Close segments frequently enough that a crash loses only the active segment and older segments remain usable. Start each segment on a keyframe so saved footage can be concatenated without a full re-encode when the selected container permits it.
3. Apply exactly one selected eviction rule:
   - **Time:** remove the oldest complete segments once their combined timeline exceeds the chosen 0.5–30 minute history.
   - **Size:** remove the oldest complete segments once their total exceeds the chosen replay-buffer cap.
4. Treat the global recording storage cap (3 GB by default) as an absolute ceiling regardless of replay mode. If the remaining safe space cannot hold another segment, evict the oldest replay segment or pause buffering cleanly.
5. A future **Save replay** key/button snapshots the current ordered segment list, remuxes it to a normal output file, and leaves buffering active. Completed recordings are never part of replay eviction.
6. Purge only files bearing Observer Cam's private buffer manifest and session identifier. Never delete unrelated files, completed exports, or another session's data.
7. Stop and clean the temporary buffer on disconnect or shutdown. Preserve a valid saved replay even if recording resumes immediately.

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
- Instant replay starts disabled, evicts only its own oldest complete segments, obeys both limit modes, and cannot exceed the global cap.
- Disconnect, crash simulation, and forced FFmpeg termination leave at most a clearly named partial file plus an error log.
