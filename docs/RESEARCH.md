# Camera research notes

No source code from the reviewed projects was copied. Observer Cam's implementation was written against Minecraft 1.21.11 and Fabric APIs after reviewing public descriptions, repository structure, and licenses.

## Minecraft YouTube Follower

- URL: https://github.com/GeiserX/Minecraft-Youtube-Follower
- License: GPL-3.0.
- Useful concepts: adaptive indoor/outdoor distance, aiming at the face rather than feet, an 8-block baseline, and separating slow shot updates from continuous viewing.
- Decision: concepts only. Its Mineflayer/Prismarine/Docker architecture and streaming stack are outside this mod's scope and no code was reused.

## Second Person Camera

- URL: https://modrinth.com/mod/secondpersoncamera
- License: MIT (as listed by Modrinth).
- Useful concepts: retain the real player as the controlled entity, assign only Minecraft's camera entity to the alternate viewpoint, and restore safely when a target disappears.
- Decision: used the same platform-level separation principle through Minecraft's camera entity API; no code was reused.

## Camera Utils

- URL: https://github.com/henkelmax/camera-utils
- License: no explicit license file or GitHub license metadata was visible during review.
- Useful concepts: detached camera control, adjustable third-person distance, and movement smoothing.
- Decision: no source reuse because the repository did not present an explicit license and its implementation targets older APIs.

## RealCamera

- URL: https://github.com/xTracr/RealCamera
- License: MIT.
- Useful concepts: camera position/rotation transforms and the importance of applying a camera transform at the vanilla camera setup boundary.
- Decision: Observer Cam uses its own single `CameraTransform` and a narrow `Camera.setup` injection; no code was reused.

## Freecam

- Prompt URL: https://github.com/Zergatul/freecam
- Current maintained comparison: https://github.com/MinecraftFreecam/Freecam
- License: MIT for both repositories as displayed by GitHub.
- Useful concepts: camera/player separation and robust restoration after leaving a detached camera mode.
- Decision: Observer Cam does not create a controllable free-camera player surrogate; it assigns the real in-world Observer entity as the camera. No code was reused.

## Fabric and Mod Menu

- Fabric development documentation: https://docs.fabricmc.net/develop/
- Fabric 1.21.11 Yarn reference: https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/
- Mod Menu: https://modrinth.com/mod/modmenu
- Versions selected: Minecraft 1.21.11, Java 21, Fabric Loader 0.18.4, Fabric API 0.141.2+1.21.11, Loom 1.14.10, and optional Mod Menu 17.0.0-beta.2.
- Configuration decision: a small Gson-backed config plus vanilla widgets avoids adding Cloth Config/YACL solely for this MVP.

## Real-time recording and FFmpeg

- Back On Track: https://github.com/Zack694/Back-On-Track
- License: MIT. The reviewed release targets Fabric 1.21.11, making it the closest current compatibility reference.
- Useful concepts: capture Minecraft's final framebuffer through the game's asynchronous screenshot readback, hand completed frames to a bounded queue, and stream raw pixels to FFmpeg away from the render thread.
- ReplayMod rendering documentation: https://www.replaymod.com/docs/
- FFmpeg raw-video input documentation: https://ffmpeg.org/ffmpeg-all.html#rawvideo
- FFmpeg segment/stream-segment documentation: https://ffmpeg.org/ffmpeg-formats.html#segment_002c-stream_005fsegment_002c-ssegment
- Decision: Observer Cam independently implements the same platform pattern with a much narrower scope. Minecraft owns the GPU readback, the queue is deliberately tiny so recording cannot consume unbounded memory, and FFmpeg receives an explicit pixel format, dimensions, and frame rate. The clean-world capture point is before Minecraft draws its GUI; an optional second capture point includes the HUD.
- Reliability additions: every session has an identity token so late asynchronous frames cannot enter a later recording, the render thread never waits for encoding, brief missed slots are filled without unlimited catch-up, and output is first written to a clearly named partial file before finalization.
- Instant-replay decision: use the official segment muxer with forced keyframes, fixed two-second segments, reset timestamps, and a concat manifest for stream-copy export. This makes old-footage eviction file-granular and avoids repeatedly rewriting one large recording.

Flashback was also reviewed as a product reference for export controls. Its custom license was not suitable for source reuse, so no Flashback implementation code was copied.

## Audio capture boundary

- OpenAL Soft loopback extension: https://openal-soft.org/openal-extensions/SOFT_loopback.txt
- FFmpeg device documentation: https://ffmpeg.org/ffmpeg-devices.html#dshow
- The 1.21.11 Back On Track project independently confirms the practical limitation: Minecraft audio is outside Java Sound, so game-output recording requires a system loopback/monitor device.
- Decision: do not replace Minecraft's active OpenAL device/context. `ALC_SOFT_loopback` is an application-owned renderer, not a passive tap of the current device; using it would require redirecting the game's sound engine and risks compatibility with audio and voice-chat mods.
- Decision: optional recording audio comes from an explicitly chosen DirectShow loopback source in the same FFmpeg session as video. MP4/MKV use AAC and WebM uses Opus; replay segments keep the same audio stream for lossless concatenation.
- The current Windows system exposes microphones but no built-in loopback source. Observer Cam therefore remains video-only here unless Windows' own Stereo Mix becomes available. It does not install or require an additional virtual-audio package and never guesses a microphone as game audio.
