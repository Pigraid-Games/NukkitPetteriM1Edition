package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityBlaze;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EntitySnowball extends EntityProjectile {

    public static final int NETWORK_ID = 81;

    private static final byte[] particleCounts = new byte[24];
    private static int particleIndex = 0;

    static {
        for (int i = 0; i < particleCounts.length; i++) {
            particleCounts[i] = (byte) (ThreadLocalRandom.current().nextInt(10) + 5);
        }
    }

    private static int nextParticleCount() {
        int index = particleIndex++;
        if (index >= particleCounts.length) {
            particleIndex = index = 0;
        }
        return particleCounts[index];
    }

    public EntitySnowball(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntitySnowball(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    protected float getDrag() {
        return 0.01f;
    }

    @Override
    protected float getGravity() {
        return 0.03f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }

    @Override
    public float getLength() {
        return 0.25f;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public int getResultDamage(Entity entity) {
        return entity instanceof EntityBlaze ? 3 : super.getResultDamage();
    }

    @Override
    public void onHit() {
        level.addParticle(new GenericParticle(this, Particle.TYPE_SNOWBALL_POOF), null, nextParticleCount());
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.age > 1200 || this.isCollided) {
            this.close();
            return false;
        }

        super.onUpdate(currentTick);
        return !this.closed;
    }
}
