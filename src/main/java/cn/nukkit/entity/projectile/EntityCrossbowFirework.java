package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.NBTEntityData;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

public class EntityCrossbowFirework extends EntityProjectile {

    public static final int NETWORK_ID = 72;

    private int lifetime;
    private Item firework;

    public EntityCrossbowFirework(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityCrossbowFirework(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return (source.getCause() == DamageCause.VOID ||
                source.getCause() == DamageCause.FIRE_TICK ||
                source.getCause() == DamageCause.ENTITY_EXPLOSION ||
                source.getCause() == DamageCause.BLOCK_EXPLOSION)
                && super.attack(source);
    }

    @Override
    protected double getBaseDamage() {
        if (this.firework == null) return 0;
        Tag nbt = this.firework.getNamedTag();
        if (nbt == null) return 0;
        nbt = ((CompoundTag) nbt).get("Fireworks");
        if (!(nbt instanceof CompoundTag)) return 0;
        nbt = ((CompoundTag) nbt).get("Explosions");
        if (!(nbt instanceof ListTag)) return 0;
        int starCount = ((ListTag<?>) nbt).size();
        if (starCount == 0) return 0;
        // Vanilla: base 5-6 for first star, +1-2 per additional star
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        return 5 + rand.nextInt(2) + (starCount - 1) * (1 + rand.nextInt(2));
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        // Spawn a temporary EntityFirework at the hit entity's position for the explosion visual
        if (this.firework != null) {
            CompoundTag fwNbt = new CompoundTag()
                    .putList(new ListTag<DoubleTag>("Pos")
                            .add(new DoubleTag("", entity.x))
                            .add(new DoubleTag("", entity.y + entity.getHeight() / 2))
                            .add(new DoubleTag("", entity.z)))
                    .putList(new ListTag<DoubleTag>("Motion")
                            .add(new DoubleTag("", 0))
                            .add(new DoubleTag("", 0))
                            .add(new DoubleTag("", 0)))
                    .putList(new ListTag<FloatTag>("Rotation")
                            .add(new FloatTag("", 0))
                            .add(new FloatTag("", 0)))
                    .putCompound("FireworkItem", NBTIO.putItemHelper(this.firework));
            EntityFirework visualFirework = new EntityFirework(entity.chunk, fwNbt);
            visualFirework.spawnToAll();

            // Immediately trigger explosion on the visual firework
            EntityEventPacket pk = new EntityEventPacket();
            pk.event = EntityEventPacket.FIREWORK_EXPLOSION;
            pk.eid = visualFirework.getId();
            Server.broadcastPacket(visualFirework.getViewers().values(), pk);

            level.addLevelSoundEvent(entity, LevelSoundEventPacket.SOUND_LARGE_BLAST, -1, NETWORK_ID);

            // AoE explosion damage centered on the hit entity
            Tag nbt = this.firework.getNamedTag();
            if (nbt != null) {
                nbt = ((CompoundTag) nbt).get("Fireworks");
                if (nbt instanceof CompoundTag) {
                    nbt = ((CompoundTag) nbt).get("Explosions");
                    if (nbt instanceof ListTag && ((ListTag<?>) nbt).size() != 0) {
                        EntityExplosionPrimeEvent explosionEv = new EntityExplosionPrimeEvent(this, 2.5);
                        explosionEv.setBlockBreaking(false);
                        server.getPluginManager().callEvent(explosionEv);
                        if (!explosionEv.isCancelled()) {
                            Explosion explosion = new Explosion(entity, explosionEv.getForce(), this);
                            explosion.explodeEntity();
                        }
                    }
                }
            }

            visualFirework.kill();
        }

        // Apply direct hit damage
        float damage = this.getResultDamage();
        EntityDamageEvent ev;
        if (this.shootingEntity == null) {
            ev = new EntityDamageEvent(this, DamageCause.PROJECTILE, damage);
        } else {
            ev = new cn.nukkit.event.entity.EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, DamageCause.PROJECTILE, damage, this.knockBack);
        }
        entity.attack(ev);

        this.close();
    }

    private void triggerExplosion() {
        EntityEventPacket pk = new EntityEventPacket();
        pk.event = EntityEventPacket.FIREWORK_EXPLOSION;
        pk.eid = this.getId();
        this.level.addChunkPacket(this.getChunkX(), this.getChunkZ(), pk);
        level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LARGE_BLAST, -1, NETWORK_ID);

        if (this.firework != null) {
            Tag nbt = this.firework.getNamedTag();
            if (nbt != null) {
                nbt = ((CompoundTag) nbt).get("Fireworks");
                if (nbt instanceof CompoundTag) {
                    nbt = ((CompoundTag) nbt).get("Explosions");
                    if (nbt instanceof ListTag && ((ListTag<?>) nbt).size() != 0) {
                        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, 2.5);
                        ev.setBlockBreaking(false);
                        server.getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            Explosion explosion = new Explosion(this, ev.getForce(), this);
                            explosion.explodeEntity();
                        }
                    }
                }
            }
        }
    }

    @Override
    public float getGravity() {
        return (float) Utils.rand(-0.01, 0.01);
    }

    @Override
    public float getHeight() {
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
    public void initEntity() {
        super.initEntity();

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        this.lifetime = 30 + rand.nextInt(6) + rand.nextInt(7);

        if (namedTag.contains("FireworkItem")) {
            this.setFirework(NBTIO.getItemHelper(namedTag.getCompound("FireworkItem")));
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        } else if (this.age > this.lifetime) {
            this.close();
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }

        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            if (!this.isCollided) {
                this.motionY -= this.getGravity();
            }

            this.move(this.motionX, this.motionY, this.motionZ);

            // Check for entity collision
            if (!this.closed) {
                Vector3 moveVector = new Vector3(this.x + this.motionX, this.y + this.motionY, this.z + this.motionZ);
                Entity[] list = this.getLevel().getCollidingEntities(this.boundingBox.addCoord(this.motionX, this.motionY, this.motionZ).expand(1, 1, 1), this);
                double nearDistance = Integer.MAX_VALUE;
                Entity nearEntity = null;
                for (Entity entity : list) {
                    if ((entity == this.shootingEntity && this.age < 5) || (entity instanceof Player && ((Player) entity).getGamemode() == Player.SPECTATOR)) {
                        continue;
                    }
                    AxisAlignedBB bb = entity.boundingBox.grow(0.3, 0.3, 0.3);
                    MovingObjectPosition ob = bb.calculateIntercept(this, moveVector);
                    if (ob == null) continue;
                    double distance = this.distanceSquared(ob.hitVector);
                    if (distance < nearDistance) {
                        nearDistance = distance;
                        nearEntity = entity;
                    }
                }
                if (nearEntity != null) {
                    onCollideWithEntity(nearEntity);
                    return true;
                }
            }

            this.updateMovement();

            if (this.age == 0) {
                this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LAUNCH);
            }

            if (this.age >= this.lifetime) {
                triggerExplosion();
                this.kill(); // Using close() here would remove the firework before the explosion is displayed
                hasUpdate = true;
            }
        }

        return hasUpdate || !this.onGround || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        if (this.firework != null) {
            this.namedTag.putCompound("FireworkItem", NBTIO.putItemHelper(this.firework));
        }
    }

    public void setFirework(Item item) {
        this.firework = item;
        this.setDataProperty(new NBTEntityData(Entity.DATA_DISPLAY_ITEM, firework));
    }
}
