package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntityBreezeWindCharge extends EntityWindCharge {

    public static final int NETWORK_ID = 141;

    public EntityBreezeWindCharge(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityBreezeWindCharge(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    protected double getBurstRadius() {
        return 3.0;
    }

    @Override
    protected double getHorizontalStrength() {
        return 0.18;
    }
}
