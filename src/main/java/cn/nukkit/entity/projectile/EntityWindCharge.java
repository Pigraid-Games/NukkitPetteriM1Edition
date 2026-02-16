package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.BellRingEvent;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.*;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntityWindCharge extends EntityProjectile implements EntityExplosive {

    public static final int NETWORK_ID = 143;

    public static final int SOUND_WIND_CHARGE_BURST = 509;

    // Burst radius — matches Nukkit-MOT and AllayMC
    private static final double BURST_RADIUS = 2.0;

    // Knockback constants (Nukkit-MOT style: halve existing motion + impulse)
    private static final double HORIZONTAL_STRENGTH = 0.2;
    private static final double VERTICAL_BOOST = 1.0;

    public Entity directionChanged;

    public EntityWindCharge(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityWindCharge(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.3125f;
    }

    @Override
    public float getLength() {
        return 0.3125f;
    }

    @Override
    public float getHeight() {
        return 0.3125f;
    }

    @Override
    protected float getGravity() {
        return 0.0f;
    }

    @Override
    protected float getDrag() {
        return 0.0f;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        // Deflectable by melee attacks and projectiles (reflect_on_hurt: true)
        if (source instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
            if (this.directionChanged == null) {
                this.directionChanged = damager;
                this.shootingEntity = damager;
                this.setMotion(damager.getDirectionVector());
            }
        }
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        // ignored_entities: ender_crystal, wind_charge_projectile, breeze_wind_charge_projectile
        if (entity instanceof EntityWindCharge) {
            return false;
        }
        return super.canCollideWith(entity);
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        if (directionChanged != null && directionChanged == entity) {
            return;
        }

        // Direct hit: 1HP damage (impact_damage.damage: 1)
        EntityDamageEvent dmg;
        if (this.shootingEntity != null) {
            dmg = new EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, DamageCause.PROJECTILE, 1f);
        } else {
            dmg = new EntityDamageByEntityEvent(this, entity, DamageCause.PROJECTILE, 1f);
        }
        entity.attack(dmg);

        // Wind burst (wind_burst_on_hit)
        windBurst();
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        boolean hasUpdate = super.onUpdate(currentTick);

        if (this.age > 1200 || this.isCollided) {
            this.explode();
            hasUpdate = true;
        }

        return hasUpdate;
    }

    @Override
    public void explode() {
        if (this.closed) {
            return;
        }
        windBurst();
    }

    /**
     * Wind burst: standard knockback formula (Nukkit-MOT style).
     * Halves existing motion, then adds horizontal push + fixed vertical boost.
     * No distance attenuation — all entities within burst radius get full force.
     * Standing: motionY = 0/2 + 1.0 = 1.0 → ~6 blocks.
     * Jumping:  motionY = 0.42/2 + 1.0 = 1.21 → ~7.5 blocks.
     */
    private void windBurst() {
        if (this.closed) {
            return;
        }

        double radius = BURST_RADIUS;
        double radiusSquared = radius * radius;
        double minX = NukkitMath.floorDouble(this.x - radius - 1);
        double maxX = NukkitMath.ceilDouble(this.x + radius + 1);
        double minY = NukkitMath.floorDouble(this.y - radius - 1);
        double maxY = NukkitMath.ceilDouble(this.y + radius + 1);
        double minZ = NukkitMath.floorDouble(this.z - radius - 1);
        double maxZ = NukkitMath.ceilDouble(this.z + radius + 1);

        AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        Entity[] list = this.level.getNearbyEntities(explosionBB, this);

        for (Entity entity : list) {
            if (!(entity instanceof EntityLiving)) {
                continue;
            }
            if (entity instanceof EntityItem || entity instanceof EntityXPOrb) {
                continue;
            }

            double dist = entity.distance(this);
            if (dist > radius) {
                continue;
            }

            // Horizontal: halve existing motion + push away from center
            double kbX = entity.motionX / 2.0;
            double kbZ = entity.motionZ / 2.0;
            kbX -= (this.x - entity.x) * HORIZONTAL_STRENGTH;
            kbZ -= (this.z - entity.z) * HORIZONTAL_STRENGTH;

            // Vertical: use player's actual speed for jump stacking
            // Player.speed is (from - to), so actual velocity Y = -speed.y
            double currentVelY = 0;
            if (entity instanceof Player && ((Player) entity).speed != null) {
                currentVelY = -((Player) entity).speed.y;
            } else {
                currentVelY = entity.motionY;
            }
            double kbY = currentVelY + VERTICAL_BOOST;

            // Netherite armor reduces knockback by 10% per piece
            if (entity instanceof Player) {
                int netheritePieces = 0;
                for (Item armor : ((Player) entity).getInventory().getArmorContents()) {
                    if (armor.getTier() == ItemArmor.TIER_NETHERITE) {
                        netheritePieces++;
                    }
                }
                if (netheritePieces > 0) {
                    double reduction = 1.0 - 0.1 * netheritePieces;
                    kbX *= reduction;
                    kbY *= reduction;
                    kbZ *= reduction;
                }
            }

            entity.setMotion(new Vector3(kbX, kbY, kbZ));

            // Negate fall damage for affected players
            if (entity instanceof Player) {
                entity.resetFallDistance();
            }
        }

        // Toggle nearby interactable blocks
        toggleNearbyBlocks(radius);

        // Particle and sound
        this.level.addParticle(new GenericParticle(this, Particle.TYPE_WIND_EXPLOSION));
        this.level.addLevelSoundEvent(this.add(0, 1), SOUND_WIND_CHARGE_BURST);

        this.close();
    }

    private void toggleNearbyBlocks(double radius) {
        int minX = NukkitMath.floorDouble(this.x - radius);
        int maxX = NukkitMath.ceilDouble(this.x + radius);
        int minY = NukkitMath.floorDouble(this.y - radius);
        int maxY = NukkitMath.ceilDouble(this.y + radius);
        int minZ = NukkitMath.floorDouble(this.z - radius);
        int maxZ = NukkitMath.ceilDouble(this.z + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (this.distanceSquared(new Vector3(x + 0.5, y + 0.5, z + 0.5)) > radius * radius) {
                        continue;
                    }

                    Block block = this.level.getBlock(x, y, z);

                    if (block instanceof BlockButton) {
                        block.onActivate(Item.get(Item.AIR), null);
                    } else if (block instanceof BlockLever) {
                        block.onActivate(Item.get(Item.AIR), null);
                    } else if (block instanceof BlockDoor && !(block instanceof BlockDoorIron)) {
                        ((BlockDoor) block).toggle(null);
                    } else if (block instanceof BlockTrapdoor && !(block instanceof BlockTrapdoorIron)) {
                        ((BlockTrapdoor) block).toggle(null);
                    } else if (block instanceof BlockFenceGate) {
                        ((BlockFenceGate) block).toggle(null);
                    } else if (block instanceof BlockBell) {
                        ((BlockBell) block).ring(null, BellRingEvent.RingCause.UNKNOWN);
                    } else if (block instanceof BlockCandle && ((BlockCandle) block).isLit()) {
                        ((BlockCandle) block).setLit(false);
                        this.level.setBlock(block, block, true, true);
                    }
                }
            }
        }
    }
}
