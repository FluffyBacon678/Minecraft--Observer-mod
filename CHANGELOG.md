# Changelog

All notable user-facing changes to Observer Cam are recorded here.

## 0.1.0-beta.1 — 2026-08-28

### Added

- Autonomous documentary-style Observer cameraman with indoor/outdoor shot planning, group-aware framing, collision recovery, and smoothly interpolated POV.
- Mod Menu controls for cameraman state, POV, camera behavior, movement, cinematography, recording, video quality, instant replay, PiP, and debugging.
- MP4/H.264, MKV/H.264, and WebM/VP9 recording through a bounded FFmpeg pipeline, with current-window, 720p, 1080p, 1440p, and 4K output presets up to 120 FPS.
- Optional, off-by-default rolling instant replay with time or size retention and an `F9` save action.
- Optional, off-by-default Observer picture-in-picture monitor with configurable resolution and 2–60 FPS refresh.
- Optional, off-by-default talkative Observer assistant with local facts and a configurable one-to-five-minute interval.
- Configurable output directory, **Open video folder**, FFmpeg selector, 3 GB default output-folder cap, and optional Windows loopback audio.
- Redstone-red Observer eye and compact HUD state while recording or buffering replay.
- Background Observer recording and replay while the player continues using their normal camera.

### Safety and polish

- Compatible Fabric API updates are accepted instead of requiring exactly 0.141.2, including 0.141.4 for Minecraft 1.21.11.
- Recording uses bounded queues, safe partial-file finalization, free-space checks, and private marked replay buffers that never evict completed or unrelated files.
- Recording and PiP hotkeys are deliberately unbound by default to avoid conflicts.
- Cameraman and recording hotkeys are visible in Mod Menu; both are deliberately unbound on a clean install.
- Invalid client snapshots and corrupted local numeric settings are sanitized before use.
- Config files are replaced atomically when the filesystem supports it.
- PiP capture and texture cleanup are bounded and resilient across reset/disconnect paths.
- FFmpeg/replay finalizers are time-bounded, terminate interrupted child processes, preserve diagnostics, and recover their UI state after unexpected errors.
- Replay cleanup requires an exact private ownership marker and UUID session path, rejects symbolic-link substitutions, and never treats unrelated directories as deletion targets.
- Disk-budget checks run twice per second with 128 MB of stop headroom so the configured recording cap remains conservative under encoder bursts.
- Metadata now identifies the project owner, support links, and exact first-beta compatibility.

### Known limitations

- Full Observer POV is required for recording and instant replay.
- FFmpeg remains an external requirement for video export.
- Automatic loopback-audio selection is Windows-only and does not install a driver.
- PiP costs a second world render and may be expensive with shaders.
- ReplayMod/Flashback camera-track integration is deferred.
