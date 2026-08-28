package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.camera.CameraCandidate;
import com.fluffybacon.observercam.camera.CameraDirector;
import com.fluffybacon.observercam.camera.CandidateGenerator.CameraPlan;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ObserverDebugRenderer {
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 0.55F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF3030, 0.65F);
    private static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF9A24, 0.65F);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x35FF55, 0.85F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x30D8FF, 0.40F);

    private ObserverDebugRenderer() {
    }

    public static void tick(Minecraft client) {
        ObserverCamConfig config = ObserverCamConfig.get();
        if (client.level == null || client.player == null || client.player.tickCount % CameraDirector.PLAN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!config.showCandidatePositions && !config.showSelectedCameraPosition && !config.showRaycasts && !config.showCollisionChecks) {
            return;
        }
        ObserverCameraEntity observer = ObserverPovController.findOwnedObserver(client);
        if (observer == null) {
            return;
        }
        Entity target = observer.getTarget();
        if (target == null) {
            return;
        }
        CameraPlan plan = CameraDirector.preview(observer, target);
        ClientLevel level = client.level;
        if (config.showCandidatePositions || config.showCollisionChecks) {
            for (CameraCandidate candidate : plan.candidates()) {
                DustParticleOptions color = !candidate.clear() ? RED : candidate.visibleSamples() < 3 ? ORANGE : WHITE;
                if (candidate.clear() && !config.showCandidatePositions) {
                    continue;
                }
                particle(level, color, candidate.position().add(0.0, 0.5, 0.0));
            }
        }
        if (plan.best() != null && config.showSelectedCameraPosition) {
            particle(level, GREEN, plan.best().position().add(0.0, 0.5, 0.0));
        }
        if (plan.best() != null && config.showRaycasts) {
            Vec3 start = plan.best().position().add(0.0, 0.5, 0.0);
            Vec3 delta = plan.best().focus().subtract(start);
            for (int i = 0; i <= 12; i++) {
                particle(level, CYAN, start.add(delta.scale(i / 12.0)));
            }
        }
    }

    private static void particle(ClientLevel level, DustParticleOptions options, Vec3 position) {
        level.addAlwaysVisibleParticle(options, position.x, position.y, position.z, 0.0, 0.0, 0.0);
    }
}
