# Observer Cam release readiness

Target: **0.1.0-beta.1+mc1.21.11** for Fabric on Minecraft 1.21.11.

This is a beta gate, not a promise that every future 1.21.x build is compatible. Publish the remapped jar as the primary file; the sources jar is optional developer material.

## Completed in the release-candidate pass

- Versioned and dependency metadata is pinned to the tested Minecraft, Loader, Fabric API, and Java baseline.
- Project ownership, source, issue, license, and homepage metadata is present in the jar.
- Automated builds run the test suite on Java 21 under Linux and Windows.
- The packaged icon is reduced to a sensible in-game size instead of contributing most of the jar.
- Camera, config, recording, replay, storage-policy, FFmpeg-command, and PiP support code has automated coverage where it can be tested outside Minecraft.
- Config writes are crash-resistant and invalid numeric settings recover to defaults.

## Required before public upload

Run these against the exact jar intended for release and record the result below:

- [ ] Core camera/POV pass in open terrain, interiors, forest, cave, water, teleports, portals, and deliberately blocked routes.
- [ ] PiP enable/disable, HUD hiding, full-POV hiding, reconnect, and one shader-heavy scene.
- [ ] One 60-second 1080p/30 recording with correct duration, seeking, frame pacing, and playback outside Minecraft.
- [ ] Clean MP4, MKV, and WebM recordings plus a retained-history instant-replay save in each format.
- [ ] Recording failure paths: missing FFmpeg, resize, disconnect, forced encoder exit, nearly full configured cap, and low free disk.
- [ ] Instant replay time eviction, size eviction, save/resume, POV exit, and normal-recording handoff.
- [ ] Dedicated server with two clients: ownership, separate per-player settings, targeting another player, POV restoration, disconnect, and dimension change.
- [ ] Final compatibility pass with the actual release modpack and shaders; check the log for mixin conflicts or renderer errors.
- [ ] Confirm the bundled artwork's origin and distribution rights. If any bundled asset or substantial project material was AI-assisted, disclose that honestly where the host requires it.
- [ ] Prepare non-AI-generated, policy-compliant storefront icon/gallery screenshots with useful alt text. Do not assume the bundled thumbnail is acceptable for a public listing.

Modrinth's current policies require disclosure of significant AI-assisted content and prohibit primarily AI-generated project-page images. Review the current rules before submission: [content disclosures](https://support.modrinth.com/en/articles/16567675-content-disclosures) and [AI usage](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai).

## Publication steps

1. Verify the version in `gradle.properties`, run `clean build`, and install only the resulting remapped jar for the final pass.
2. Generate and save a SHA-256 checksum for that exact jar.
3. Commit the completed acceptance log and release notes; confirm the GitHub build passes on Linux and Windows.
4. Tag the accepted commit as `v0.1.0-beta.1` and create a GitHub prerelease containing the primary jar and checksum.
5. On Modrinth, select Fabric, Minecraft 1.21.11, and client-and-server. Mark Fabric API required and Mod Menu optional; describe FFmpeg as required only for video export.
6. Upload the same checksum-matched jar, include the known limitations, complete all required disclosures, and keep the version marked beta.

## Acceptance log

| Date | Build / SHA-256 | Environment | Result | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-27 | `ed27daa` / `62567B1E…` | Local Minecraft 1.21.11 modpack | Partial pass | Core Observer movement and PiP were user-tested; this predates the beta metadata/hardening pass. |
| 2026-08-28 | `0.1.0-beta.1+mc1.21.11` / `A484C5E5…` | Java 21 clean safety build | Automated pass | 59 tests passed with no compile warnings; finalizer, process, replay-ownership, PiP, and disk-cap error paths were hardened. Manual matrix remains open. |
|  |  |  |  |  |

## Post-beta work, not a first-release blocker

- ReplayMod or Flashback camera-pose integration.
- Capability-probed hardware encoders and higher-than-1080p export.
- Cross-platform game-audio loopback discovery without third-party drivers.
- Configurable PiP placement/scale and background recording from PiP.
- Broader Minecraft/Fabric API compatibility claims after separate testing.
