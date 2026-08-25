package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.camera.CandidateGenerator.CameraPlan;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CameraDirector {
    public static final int PLAN_INTERVAL_TICKS = 4;

    private Vec3 desiredPosition;
    private Vec3 focus;
    private int selectedAngle = -1;
    private int ticksInShot;
    private double currentScore;
    private CameraState state = CameraState.REPOSITION;
    private CameraPlan latestPlan;

    public void tick(ObserverCameraEntity observer, Entity target) {
        ObserverCamConfig config = ObserverCamConfig.get();
        ticksInShot++;
        if (desiredPosition == null || observer.tickCount % PLAN_INTERVAL_TICKS == 0) {
            chooseShot(observer, target, config);
        }
        if (desiredPosition == null || focus == null) {
            observer.updateDebug(CameraState.EMERGENCY_RECOVERY, 0.0, 0, 1.0, 0);
            return;
        }

        double separation = observer.distanceTo(target);
        if (separation > config.emergencyTeleportDistance) {
            state = CameraState.EMERGENCY_RECOVERY;
        } else if (separation > config.catchUpDistance) {
            state = CameraState.CATCH_UP;
        }
        boolean teleported = MotionController.tick(observer, desiredPosition, focus, config);
        if (teleported) {
            state = CameraState.EMERGENCY_RECOVERY;
        }
        CameraCandidate selected = selectedCandidate();
        int visibility = selected == null ? 0 : selected.visibleSamples();
        double indoor = latestPlan == null ? 0.0 : latestPlan.indoorFactor();
        int count = latestPlan == null ? 0 : latestPlan.candidates().size();
        observer.updateDebug(state, currentScore, visibility, indoor, count);
    }

    private void chooseShot(ObserverCameraEntity observer, Entity target, ObserverCamConfig config) {
        latestPlan = CandidateGenerator.plan(observer, target, selectedAngle, config);
        CameraCandidate best = latestPlan.best();
        if (best == null) {
            state = CameraState.RECOVER_LOS;
            return;
        }

        CameraCandidate sameAngle = latestPlan.candidates().stream()
                .filter(CameraCandidate::valid)
                .filter(candidate -> candidate.angleIndex() == selectedAngle)
                .max(java.util.Comparator.comparingDouble(CameraCandidate::score))
                .orElse(null);
        double incumbent = sameAngle == null ? -1000.0 : sameAngle.score();
        int minimumShotTicks = 20 + (int) Math.round(config.shotStability * 80.0);
        double switchThreshold = 4.0 + config.shotStability * 14.0;
        boolean forced = incumbent < config.reframeThreshold || sameAngle == null;
        boolean maySwitch = ShotSwitchPolicy.shouldSwitch(incumbent, best.score(), ticksInShot,
                minimumShotTicks, switchThreshold, false);

        CameraCandidate chosen;
        if (selectedAngle < 0 || forced || maySwitch) {
            chosen = best;
            if (chosen.angleIndex() != selectedAngle) {
                selectedAngle = chosen.angleIndex();
                ticksInShot = 0;
                state = CameraState.REPOSITION;
            } else {
                state = CameraState.REFRAME;
            }
        } else {
            chosen = sameAngle;
            state = incumbent >= 78.0 ? CameraState.HOLD : CameraState.REFRAME;
        }

        if (chosen != null) {
            desiredPosition = chosen.position();
            focus = chosen.focus();
            currentScore = chosen.score();
            if (chosen.visibleSamples() <= 1) {
                state = CameraState.RECOVER_LOS;
            }
        }
    }

    private CameraCandidate selectedCandidate() {
        if (latestPlan == null) {
            return null;
        }
        return latestPlan.candidates().stream()
                .filter(candidate -> candidate.angleIndex() == selectedAngle)
                .max(java.util.Comparator.comparingDouble(CameraCandidate::score))
                .orElse(latestPlan.best());
    }

    public CameraPlan latestPlan() {
        return latestPlan;
    }

    public static CameraPlan preview(ObserverCameraEntity observer, Entity target) {
        return CandidateGenerator.plan(observer, target, -1, ObserverCamConfig.get());
    }
}
