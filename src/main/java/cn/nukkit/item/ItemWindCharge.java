package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ItemWindCharge extends ProjectileItem {

    private static final int COOLDOWN_TICKS = 10;
    private static final Map<Long, Integer> cooldowns = new ConcurrentHashMap<>();

    public ItemWindCharge() {
        this(0, 1);
    }

    public ItemWindCharge(Integer meta) {
        this(meta, 1);
    }

    public ItemWindCharge(Integer meta, int count) {
        super(WIND_CHARGE, meta, count, "Wind Charge");
    }

    @Override
    public String getProjectileEntityType() {
        return "WindCharge";
    }

    @Override
    public float getThrowForce() {
        return 1.5f;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        int currentTick = player.getServer().getTick();
        Integer lastThrow = cooldowns.get(player.getId());
        if (lastThrow != null && currentTick - lastThrow < COOLDOWN_TICKS) {
            return false;
        }

        Vector3 motion = directionVector.multiply(this.getThrowForce());
        // Random trajectory offset (cone-shaped spread, Bedrock Edition)
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        motion.x += rand.nextGaussian() * 0.0075 * 6;
        motion.y += rand.nextGaussian() * 0.0075 * 6;
        motion.z += rand.nextGaussian() * 0.0075 * 6;

        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", player.x))
                        .add(new DoubleTag("", player.y + player.getEyeHeight() - 0.3))
                        .add(new DoubleTag("", player.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", motion.x))
                        .add(new DoubleTag("", motion.y))
                        .add(new DoubleTag("", motion.z)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (float) player.yaw))
                        .add(new FloatTag("", (float) player.pitch)));

        Entity projectile = Entity.createEntity(this.getProjectileEntityType(), player.getLevel().getChunk(player.getChunkX(), player.getChunkZ()), nbt, player);
        if (projectile instanceof EntityProjectile) {
            ProjectileLaunchEvent ev = new ProjectileLaunchEvent((EntityProjectile) projectile);
            player.getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                projectile.close();
            } else {
                if (!player.isCreative()) {
                    this.count--;
                }
                cooldowns.put(player.getId(), currentTick);
                projectile.spawnToAll();
                player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_ITEM_THROWN);
            }
        }

        return true;
    }

    @Override
    public boolean isSupportedOn(int protocol) {
        return protocol >= ProtocolInfo.v1_21_0;
    }
}
