package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

public class EntityBreeze extends EntityFlyingMob {

    public static final int NETWORK_ID = 140;

    public EntityBreeze(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (source.getCause() == EntityDamageEvent.DamageCause.FALL) {
            return false;
        }
        return super.attack(source);
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.distanceSquared(player) > 576) {
            return;
        }
        if (this.attackDelay > 30) {
            this.attackDelay = 0;

            Entity projectile = Entity.createEntity("BreezeWindCharge",
                    this.add(0, this.getEyeHeight(), 0), this);
            if (projectile == null) {
                return;
            }

            Vector3 direction = player.subtract(this).normalize()
                    .multiply(1 + ThreadLocalRandom.current().nextFloat() * 0.2f);
            projectile.setMotion(direction);

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent((EntityProjectile) projectile);
            this.server.getPluginManager().callEvent(launch);
            if (launch.isCancelled()) {
                projectile.close();
            } else {
                projectile.spawnToAll();
                this.level.addSound(this, "mob.breeze.shoot");
            }
        }
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.BREEZE_ROD, 0, Utils.rand(1, 2))};
    }

    @Override
    public float getHeight() {
        return 1.77f;
    }

    @Override
    public int getKillExperience() {
        return 10;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(30);
        super.initEntity();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player) {
            Player player = (Player) creature;
            return !player.closed && player.spawned && player.isAlive()
                    && (player.isSurvival() || player.isAdventure())
                    && distance <= 576; // 24 blocks squared
        }
        return false;
    }
}
