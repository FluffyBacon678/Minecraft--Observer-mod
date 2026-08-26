package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.camera.CandidateGenerator.CameraPlan;
import com.fluffybacon.observercam.camera.CandidateGenerator.SubjectGroup;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.fluffybacon.observercam.entity.ObserverCameraManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CameraDirector {
    public static final int PLAN_INTERVAL_TICKS = 4;
    private static final int FAILED_PLAN_ATTEMPTS_BEFORE_RECOVERY = 3;
    private static final int STALLED_TICKS_BEFORE_RECOVERY = 10;
    private static final double HORIZONTAL_FOCUS_DEAD_ZONE = 0.75;
    private static final double VERTICAL_FOCUS_DEAD_ZONE = 0.55;
    private static final double FOCUS_RESPONSE = 0.42;
    private static final double MAXIMUM_FOCUS_STEP = 0.9;
    private static final double HEADING_RESPONSE = 0.24;
    private static final double MAXIMUM_HEADING_TURN = Math.toRadians(5.0);

    private Vec3 desiredPosition;
    private Vec3 focus;
    private int selectedAngle = -1;
    private int ticksInShot;
    private double currentScore;
    private CameraState state = CameraState.REPOSITION;
    private CameraPlan latestPlan;
    private boolean emergencyTeleportPending;
    private int failedPlanAttempts;
    private int stalledTicks;
    private boolean forcedRecoveryPending;
    private Vec3 compositionFocus;
    private Vec3 shotForward;

    public void tick(ObserverCameraEntity observer, Entity target) {
        ObserverCamConfig config = ObserverCameraManager.cameraConfigFor(observer);
        ticksInShot++;
        if (desiredPosition == null || forcedRecoveryPending || observer.tickCount % PLAN_INTERVAL_TICKS == 0) {
            chooseShot(observer, target, config);
        }
        if (desiredPosition == null || focus == null) {
            observer.updateDebug(CameraState.EMERGENCY_RECOVERY, 0.0, 0, 1.0, 0);
            return;
        }

        if (emergencyTeleportPending) {
            observer.snapTo(desiredPosition);
            observer.setDeltaMovement(Vec3.ZERO);
            CameraTransform.Rotation rotation = CameraTransform.lookAt(desiredPosition.add(0.0, 0.5, 0.0), focus);
            observer.setYRot(rotation.yaw());
            observer.setXRot(rotation.pitch());
            emergencyTeleportPending = false;
            state = CameraState.EMERGENCY_RECOVERY;
        }

        double separation = observer.distanceTo(target);
        if (separation > config.emergencyTeleportDistance) {
            state = CameraState.EMERGENCY_RECOVERY;
        } else if (separation > config.catchUpDistance) {
            state = CameraState.CATCH_UP;
        }
        Vec3 beforeMove = observer.position();
        double distanceBeforeMove = beforeMove.distanceTo(desiredPosition);
        boolean teleported = MotionController.tick(observer, desiredPosition, focus, config);
        if (teleported) {
            state = CameraState.EMERGENCY_RECOVERY;
        }
        double distanceAfterMove = observer.position().distanceTo(desiredPosition);
        if (!teleported && distanceBeforeMove > 0.75 && distanceBeforeMove - distanceAfterMove < 0.002) {
            stalledTicks++;
            if (stalledTicks >= STALLED_TICKS_BEFORE_RECOVERY) {
                forcedRecoveryPending = true;
                stalledTicks = 0;
            }
        } else {
            stalledTicks = 0;
        }
        CameraCandidate selected = selectedCandidate();
        int visibility = selected == null ? 0 : selected.visibleSamples();
        double indoor = latestPlan == null ? 0.0 : latestPlan.indoorFactor();
        int count = latestPlan == null ? 0 : latestPlan.candidates().size();
        observer.updateDebug(state, currentScore, visibility, indoor, count);
    }

    private void chooseShot(ObserverCameraEntity observer, Entity target, ObserverCamConfig config) {
        boolean forceEmergencyRecovery = forcedRecoveryPending
                || failedPlanAttempts >= FAILED_PLAN_ATTEMPTS_BEFORE_RECOVERY - 1;
        forcedRecoveryPending = false;
        SubjectGroup subjects = CandidateGenerator.documentarySubjects(observer.level(), target, config);
        boolean snapComposition = compositionFocus == null
                || observer.distanceTo(target) > config.emergencyTeleportDistance
                || compositionFocus.distanceTo(subjects.compositionFocus()) > config.catchUpDistance;
        compositionFocus = snapComposition
                ? subjects.compositionFocus()
                : DocumentaryFraming.softFollow(compositionFocus, subjects.compositionFocus(),
                        HORIZONTAL_FOCUS_DEAD_ZONE, VERTICAL_FOCUS_DEAD_ZONE,
                        FOCUS_RESPONSE, MAXIMUM_FOCUS_STEP);

        Vec3 desiredForward = CandidateGenerator.targetForward(target);
        Vec3 horizontalVelocity = new Vec3(target.getDeltaMovement().x, 0.0, target.getDeltaMovement().z);
        if (horizontalVelocity.lengthSqr() < 0.0025 && shotForward != null) {
            desiredForward = shotForward;
        }
        shotForward = snapComposition
                ? desiredForward
                : DocumentaryFraming.smoothHeading(shotForward, desiredForward,
                        HEADING_RESPONSE, MAXIMUM_HEADING_TURN);
        subjects = subjects.withCompositionFocus(compositionFocus);
        latestPlan = CandidateGenerator.plan(observer, target, selectedAngle, config,
                forceEmergencyRecovery, subjects, shotForward);
        CameraCandidate best = latestPlan.best();
        if (best == null) {
            failedPlanAttempts++;
            state = CameraState.RECOVER_LOS;
            emergencyTeleportPending = false;
            desiredPosition = observer.position();
            focus = compositionFocus;
            currentScore = 0.0;
            return;
        }
        failedPlanAttempts = 0;

        CameraCandidate sameAngle = latestPlan.candidates().stream()
                .filter(this::viableCandidate)
                .filter(candidate -> candidate.angleIndex() == selectedAngle)
                .max(java.util.Comparator.comparingDouble(CameraCandidate::score))
                .orElse(null);
        CameraCandidate alternative = latestPlan.candidates().stream()
                .filter(this::viableCandidate)
                .filter(candidate -> candidate.angleIndex() != selectedAngle)
                .max(java.util.Comparator.comparingDouble(CameraCandidate::score))
                .orElse(null);
        double incumbent = sameAngle == null ? -1000.0 : sameAngle.score();
        int minimumShotTicks = 120 + (int) Math.round(config.shotStability * 80.0);
        int maximumShotTicks = 240 + (int) Math.round(config.shotStability * 100.0)
                + Math.floorMod(selectedAngle, 4) * 15;
        double switchThreshold = 6.0 + config.shotStability * 12.0;
        double effectiveSwitchThreshold = incumbent < config.reframeThreshold
                ? Math.max(3.0, switchThreshold * 0.6)
                : switchThreshold;
        double periodicTolerance = 8.0 + config.shotStability * 7.0;
        boolean visibilityFailure = sameAngle != null && sameAngle.visibleSamples() <= 1
                && best.angleIndex() != selectedAngle && best.visibleSamples() >= 3;
        boolean forced = sameAngle == null || visibilityFailure;
        CameraCandidate challenger = best.angleIndex() == selectedAngle ? alternative : best;
        boolean maySwitch = challenger != null && challenger.angleIndex() != selectedAngle
                && ShotSwitchPolicy.shouldSwitch(incumbent, challenger.score(), ticksInShot,
                        minimumShotTicks, maximumShotTicks, effectiveSwitchThreshold, periodicTolerance, forced);

        CameraCandidate chosen;
        if (selectedAngle < 0 || maySwitch) {
            chosen = selectedAngle < 0 ? best : challenger;
            if (chosen.angleIndex() != selectedAngle) {
                selectedAngle = chosen.angleIndex();
                ticksInShot = 0;
                state = CameraState.REPOSITION;
            } else {
                state = CameraState.REFRAME;
            }
        } else {
            chosen = sameAngle;
            state = incumbent >= Math.max(78.0, config.reframeThreshold)
                    ? CameraState.HOLD
                    : CameraState.REFRAME;
        }

        if (chosen != null) {
            desiredPosition = chosen.position();
            focus = chosen.focus();
            currentScore = chosen.score();
            emergencyTeleportPending = latestPlan.emergencyRecovery();
            if (chosen.visibleSamples() <= 1) {
                state = CameraState.RECOVER_LOS;
            }
        }
    }

    private boolean viableCandidate(CameraCandidate candidate) {
        return latestPlan.emergencyRecovery() ? candidate.safeTeleportDestination() : candidate.valid();
    }

    private CameraCandidate selectedCandidate() {
        if (latestPlan == null) {
            return null;
        }
        return latestPlan.candidates().stream()
                .filter(this::viableCandidate)
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
