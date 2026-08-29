package com.fluffybacon.observercam.entity;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.camera.CameraDirector;
import com.fluffybacon.observercam.camera.CameraState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class ObserverCameraEntity extends Entity {
    public static final int CAKE_DANCE_TICKS = 22 * 20;
    private static final int JUKEBOX_CHECK_INTERVAL_TICKS = 10;
    private static final int DANCE_PARTICLE_INTERVAL_TICKS = 8;
    private static final int JUKEBOX_DANCE_RADIUS = 3;
    private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TARGET_UUID = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FOLLOWING = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CAMERA_STATE = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SHOT_SCORE = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VISIBILITY = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> INDOOR = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CANDIDATE_COUNT = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POWERED_TICKS = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DANCING = SynchedEntityData.defineId(ObserverCameraEntity.class, EntityDataSerializers.BOOLEAN);

    private final CameraDirector director = new CameraDirector();
    private int missingTargetTicks;
    private int cakeDanceTicks;
    private int jukeboxCheckTicks;
    private boolean jukeboxDancing;

    public ObserverCameraEntity(EntityType<? extends ObserverCameraEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, "");
        builder.define(TARGET_UUID, "");
        builder.define(FOLLOWING, false);
        builder.define(CAMERA_STATE, CameraState.HOLD.name());
        builder.define(SHOT_SCORE, 0.0F);
        builder.define(VISIBILITY, 0);
        builder.define(INDOOR, 0.0F);
        builder.define(CANDIDATE_COUNT, 0);
        builder.define(POWERED_TICKS, 0);
        builder.define(DANCING, false);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setInvulnerable(true);
        if (!level().isClientSide()) {
            if (poweredTicks() > 0) {
                entityData.set(POWERED_TICKS, ObserverPulseTiming.advance(poweredTicks()));
            }
            updateDance((ServerLevel) level());
        }
        if (level().isClientSide() || !isFollowing()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Entity target = getTarget();
        if (target == null || !target.isAlive()) {
            Entity owner = getOwner();
            if (owner != null && owner.isAlive() && !owner.getUUID().equals(getTargetUuid())) {
                setTarget(owner);
                missingTargetTicks = 0;
                return;
            }
            if (!getTargetUuidString().isEmpty() && ++missingTargetTicks > 100) {
                discard();
            }
            return;
        }
        missingTargetTicks = 0;

        if (target.level() != level() && target instanceof ServerPlayer player) {
            moveToTargetLevel(player);
            return;
        }
        director.tick(this, target);
    }

    private void moveToTargetLevel(ServerPlayer target) {
        ServerLevel targetLevel = target.level();
        ObserverCameraEntity replacement = new ObserverCameraEntity(ObserverCam.OBSERVER_CAMERA, targetLevel);
        UUID ownerUuid = getOwnerUuid();
        replacement.setOwner(ownerUuid != null ? ownerUuid : target.getUUID());
        replacement.setTarget(target);
        replacement.setFollowing(isFollowing());
        replacement.cakeDanceTicks = cakeDanceTicks;
        replacement.entityData.set(DANCING, cakeDanceTicks > 0);
        if (isPowered()) {
            replacement.pulse();
        }
        Vec3 destination = target.position().add(0.0, 2.0, 0.0).subtract(target.getLookAngle().scale(8.0));
        replacement.snapTo(destination);
        targetLevel.addFreshEntity(replacement);
        discard();
    }

    public void setTarget(Entity target) {
        entityData.set(TARGET_UUID, target.getUUID().toString());
    }

    public void setOwner(UUID ownerUuid) {
        entityData.set(OWNER_UUID, ownerUuid.toString());
    }

    @Nullable
    public UUID getOwnerUuid() {
        return parseUuid(entityData.get(OWNER_UUID));
    }

    public boolean isOwnedBy(UUID playerUuid) {
        return playerUuid.equals(getOwnerUuid());
    }

    public void clearTarget() {
        entityData.set(TARGET_UUID, "");
    }

    @Nullable
    public UUID getTargetUuid() {
        return parseUuid(getTargetUuidString());
    }

    @Nullable
    private static UUID parseUuid(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public Entity getTarget() {
        UUID uuid = getTargetUuid();
        if (uuid == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getPlayerList().getPlayer(uuid);
        }
        return level().getPlayerByUUID(uuid);
    }

    @Nullable
    private Entity getOwner() {
        UUID uuid = getOwnerUuid();
        if (uuid == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getPlayerList().getPlayer(uuid);
        }
        return level().getPlayerByUUID(uuid);
    }

    private String getTargetUuidString() {
        return entityData.get(TARGET_UUID);
    }

    public boolean isFollowing() {
        return entityData.get(FOLLOWING);
    }

    public void setFollowing(boolean following) {
        entityData.set(FOLLOWING, following);
        if (!following) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean isPowered() {
        return ObserverPulseTiming.isPowered(poweredTicks());
    }

    public void pulse() {
        entityData.set(POWERED_TICKS, ObserverPulseTiming.activate(poweredTicks()));
    }

    public boolean startCakeDance() {
        if (level().isClientSide() || cakeDanceTicks > 0) {
            return false;
        }
        cakeDanceTicks = CAKE_DANCE_TICKS;
        entityData.set(DANCING, true);
        return true;
    }

    public boolean isDancing() {
        return entityData.get(DANCING);
    }

    private int poweredTicks() {
        return Math.max(0, entityData.get(POWERED_TICKS));
    }

    private void updateDance(ServerLevel level) {
        if (cakeDanceTicks > 0) {
            cakeDanceTicks--;
        }
        if (jukeboxCheckTicks <= 0) {
            jukeboxDancing = hasPlayingJukeboxNearby(level);
            jukeboxCheckTicks = JUKEBOX_CHECK_INTERVAL_TICKS - 1;
        } else {
            jukeboxCheckTicks--;
        }

        boolean dancing = cakeDanceTicks > 0 || jukeboxDancing;
        if (entityData.get(DANCING) != dancing) {
            entityData.set(DANCING, dancing);
        }
        if (dancing && tickCount % DANCE_PARTICLE_INTERVAL_TICKS == 0) {
            double phase = tickCount * 0.55;
            double particleX = getX() + Math.cos(phase) * 0.55;
            double particleY = getY() + 0.55 + Math.sin(phase * 0.5) * 0.10;
            double particleZ = getZ() + Math.sin(phase) * 0.55;
            float noteColor = level.getRandom().nextInt(24) / 24.0F;
            level.sendParticles(ParticleTypes.NOTE,
                    particleX, particleY, particleZ, 0, noteColor, 0.0, 0.0, 1.0);
        }
    }

    private boolean hasPlayingJukeboxNearby(ServerLevel level) {
        BlockPos center = blockPosition();
        BlockPos minimum = center.offset(-JUKEBOX_DANCE_RADIUS, -JUKEBOX_DANCE_RADIUS, -JUKEBOX_DANCE_RADIUS);
        BlockPos maximum = center.offset(JUKEBOX_DANCE_RADIUS, JUKEBOX_DANCE_RADIUS, JUKEBOX_DANCE_RADIUS);
        for (BlockPos jukeboxPosition : BlockPos.betweenClosed(minimum, maximum)) {
            if (jukeboxPosition.closerToCenterThan(position(), JUKEBOX_DANCE_RADIUS)
                    && level.getBlockEntity(jukeboxPosition) instanceof JukeboxBlockEntity jukebox
                    && jukebox.getSongPlayer().isPlaying()) {
                return true;
            }
        }
        return false;
    }

    public CameraState cameraState() {
        try {
            return CameraState.valueOf(entityData.get(CAMERA_STATE));
        } catch (IllegalArgumentException ignored) {
            return CameraState.HOLD;
        }
    }

    public float shotScore() {
        return entityData.get(SHOT_SCORE);
    }

    public int visibleSamples() {
        return entityData.get(VISIBILITY);
    }

    public float indoorFactor() {
        return entityData.get(INDOOR);
    }

    public int candidateCount() {
        return entityData.get(CANDIDATE_COUNT);
    }

    public CameraDirector director() {
        return director;
    }

    public void updateDebug(CameraState state, double score, int visibility, double indoor, int candidateCount) {
        entityData.set(CAMERA_STATE, state.name());
        entityData.set(SHOT_SCORE, (float) score);
        entityData.set(VISIBILITY, visibility);
        entityData.set(INDOOR, (float) indoor);
        entityData.set(CANDIDATE_COUNT, candidateCount);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(OWNER_UUID, input.getStringOr("ObserverCamOwner", ""));
        entityData.set(TARGET_UUID, input.getStringOr("ObserverCamTarget", ""));
        entityData.set(FOLLOWING, input.getBooleanOr("ObserverCamFollowing", false));
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("ObserverCamOwner", entityData.get(OWNER_UUID));
        output.putString("ObserverCamTarget", getTargetUuidString());
        output.putBoolean("ObserverCamFollowing", isFollowing());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected boolean isAffectedByBlocks() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
