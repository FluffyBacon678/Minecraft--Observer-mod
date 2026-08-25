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
