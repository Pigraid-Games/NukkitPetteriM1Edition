package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * Abstract class for slender projectile entities (arrows, tridents).
 * Provides 10-step sub-tick entity collision detection for accurate hit registration
 * at high speeds, preventing arrows from passing through entities.
 *
 * Adapted from PowerNukkitX's SlenderProjectile.
 */
public abstract class SlenderProjectile extends EntityProjectile {

    private static final int SPLIT_NUMBER = 10;

    public SlenderProjectile(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public SlenderProjectile(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public float getWidth() {
        return 0.1f;
    }

    @Override
    public float getHeight() {
        return 0.1f;
    }

    /**
     * Overrides move() to perform 10-step entity collision checks before delegating
     * block-aware movement to Entity.move(). This ensures arrows moving at high speed
     * don't skip through entities in a single tick.
     *
     * Note: uses addCoord() (creates a new AABB) rather than offset() (modifies in-place)
     * so that this.boundingBox is never mutated during the sub-step loop.
     */
    @Override
    public boolean move(double dx, double dy, double dz) {
        if (dx == 0 && dz == 0 && dy == 0) {
            return true;
        }

        double nearestDist = Double.MAX_VALUE;
        Entity nearestEntity = null;

        for (int step = 1; step <= SPLIT_NUMBER; step++) {
            double partX = dx * step / SPLIT_NUMBER;
            double partY = dy * step / SPLIT_NUMBER;
            double partZ = dz * step / SPLIT_NUMBER;

            // addCoord creates a NEW AxisAlignedBB and does NOT modify this.boundingBox
            AxisAlignedBB searchBB = this.boundingBox.addCoord(partX, partY, partZ).expand(1, 1, 1);

            Vector3 stepFrom = new Vector3(
                    this.x + dx * (step - 1) / SPLIT_NUMBER,
                    this.y + dy * (step - 1) / SPLIT_NUMBER,
                    this.z + dz * (step - 1) / SPLIT_NUMBER);
            Vector3 stepTo = new Vector3(
                    this.x + partX,
                    this.y + partY,
                    this.z + partZ);

            Entity[] candidates = this.level.getCollidingEntities(searchBB, this);

            for (Entity entity : candidates) {
                if ((entity == this.shootingEntity && this.age < 5)
                        || (entity instanceof Player && ((Player) entity).getGamemode() == Player.SPECTATOR)
                        || this.piercedEntities.contains(entity.getId())
                        || entity.noClip
                        || this.noClip) {
                    continue;
                }

                AxisAlignedBB entityBB = entity.boundingBox.grow(0.3, 0.3, 0.3);
                MovingObjectPosition hit = entityBB.calculateIntercept(stepFrom, stepTo);
                if (hit == null) continue;

                double dist = this.distanceSquared(hit.hitVector);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestEntity = entity;
                }
            }

            if (nearestEntity != null) break;
        }

        if (nearestEntity != null) {
            onCollideWithEntity(nearestEntity);
            return true;
        }

        // Sub-step block collision: split into SPLIT_NUMBER smaller moves so the
        // swept volume per step is ~0.35 blocks instead of ~3.5, preventing
        // arrows from false-colliding with blocks they merely fly near.
        double subDx = dx / SPLIT_NUMBER;
        double subDy = dy / SPLIT_NUMBER;
        double subDz = dz / SPLIT_NUMBER;

        for (int i = 0; i < SPLIT_NUMBER; i++) {
            super.move(subDx, subDy, subDz);
            if (this.isCollided) {
                break;
            }
        }
        return true;
    }
}
