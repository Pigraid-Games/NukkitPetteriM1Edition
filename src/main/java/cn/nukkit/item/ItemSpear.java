package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerSpearChargeEvent;
import cn.nukkit.event.player.PlayerSpearStabEvent;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.AnimateEntityPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all spear variants.
 *
 * Spears are melee weapons with two attack modes:
 * 1. Stab: triggered automatically while sprinting (onSpearStab)
 * 2. Lunge: applies forward motion when the Lunge enchantment is present
 *
 * Ported from PowerNukkitX (authors: Buddelbubi, xRookieFight).
 */
public abstract class ItemSpear extends ItemTool {

    /**
     * The three damage stages of the charge attack.
     * Stage is determined by how long the player has been continuously charging.
     */
    public enum ChargeStage {
        ENGAGED, TIRED, DISENGAGED
    }

    /**
     * Per-player last stab timestamp (milliseconds). Used to enforce the 20-tick stab cooldown.
     */
    private static final ConcurrentHashMap<UUID, Long> lastStabTime = new ConcurrentHashMap<>();

    /** Per-player charge start timestamp (ms). Present only while player is in charge mode. */
    private static final ConcurrentHashMap<UUID, Long> chargeStartTime = new ConcurrentHashMap<>();

    /** Per-player timestamp of the last charge hit, to enforce per-hit cooldown. */
    private static final ConcurrentHashMap<UUID, Long> lastChargeHitTime = new ConcurrentHashMap<>();

    /** Minimum movement speed (attribute value) required to deal charge damage. */
    public float minimumSpeed = 0.13f;

    /** Proximity range for charge attack hit detection (blocks). */
    public static final double CHARGE_HIT_RANGE = 2.5;

    /** Cooldown between individual charge hits (ms) — prevents re-hitting same entity mid-charge. */
    public static final long CHARGE_HIT_COOLDOWN_MS = 500L;

    /** Minimum closing speed (blocks/second) to deal charge damage. */
    public static final float MIN_CHARGE_DAMAGE_BPS = 4.6f;

    /** Minimum closing speed (blocks/second) to apply knockback on a charge hit. */
    public static final float MIN_KNOCKBACK_BPS = 5.1f;

    /** Knockback velocity (Nukkit units) when approach speed ≥ MIN_KNOCKBACK_BPS. */
    public static final float CHARGE_KNOCKBACK = MIN_KNOCKBACK_BPS / 20f;

    /** Minimum food level required to perform a lunge (survival/adventure only). */
    public int minimumLungeFood = 6;

    /** Base food exhaustion applied per lunge level when lunging. */
    public int baseLungeExhaust = 4;

    public ItemSpear(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public boolean isSpear() {
        return true;
    }

    /**
     * Called from Player movement handling when the player is moving at sprint speed with a spear
     * in hand. The caller (Player.java) already gates on sprint-level velocity via distanceSquared
     * threshold — no sprint-flag or speed check needed here.
     * Enforces a 1-second cooldown between stabs.
     *
     * @param player        the attacking player
     * @param movementSpeed the player's current movement speed (informational, passed to event)
     */
    public void onSpearStab(Player player, float movementSpeed) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = lastStabTime.get(uid);
        if (last != null && now - last < getJabCooldownMs()) return;

        PlayerSpearStabEvent event = new PlayerSpearStabEvent(player, this, movementSpeed);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        lastStabTime.put(uid, now); // consume cooldown only after event passes

        AnimateEntityPacket jabAnim = new AnimateEntityPacket();
        jabAnim.animation = "animation.player.first_person.melee_spear_attack";
        jabAnim.runtimeEntityIds.add(player.getId());
        player.dataPacket(jabAnim);

        applyLunge(player);

        Level level = player.getLevel();
        Vector3 eyePos = new Vector3(player.x, player.y + player.getEyeHeight(), player.z);
        Vector3 direction = player.getDirectionVector().normalize();

        double maxDistance = 4.0;
        double hitMargin = 0.125;
        double minDot = 0.866; // cos(30°) — within 60° cone

        AxisAlignedBB searchBox = player.getBoundingBox().grow(maxDistance + hitMargin, maxDistance + hitMargin, maxDistance + hitMargin);

        boolean hitAny = false;
        for (Entity entity : level.getNearbyEntities(searchBox, player)) {
            if (!(entity instanceof EntityLiving living) || !living.isAlive()) continue;

            Vector3 targetPos = new Vector3(entity.x, entity.y + living.getEyeHeight() * 0.5, entity.z);
            double distance = eyePos.distance(targetPos);
            if (distance > maxDistance + hitMargin) continue;

            Vector3 toEntity = targetPos.subtract(eyePos).normalize();
            double dot = direction.dot(toEntity);
            if (dot < minDot) continue;

            EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                    player, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, getJabDamage()
            );
            entity.attack(damageEvent);
            hitAny = true;
        }

        if (hitAny) {
            level.addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_ATTACK_STRONG);
        } else {
            level.addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE);
        }
    }

    /**
     * Called from the attack-click handler when the player lands a hit with the spear.
     * Applies the Lunge boost and extends the jab to all additional entities in the forward
     * cone — the primary target (already damaged by the normal attack flow) is excluded.
     * Shares the same 1-second cooldown as the movement-triggered jab.
     *
     * @param player        the attacking player
     * @param primaryTarget the entity already damaged by the normal attack, to skip
     */
    public void onSpearJabClick(Player player, Entity primaryTarget) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = lastStabTime.get(uid);
        if (last != null && now - last < getJabCooldownMs()) return;
        lastStabTime.put(uid, now);

        PlayerSpearStabEvent event = new PlayerSpearStabEvent(player, this, player.getMovementSpeed());
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        Level level = player.getLevel();
        Vector3 eyePos = new Vector3(player.x, player.y + player.getEyeHeight(), player.z);
        Vector3 direction = player.getDirectionVector().normalize();

        double maxDistance = 4.0;
        double hitMargin = 0.125;
        double minDot = 0.866; // cos(30°) — 60° cone

        AxisAlignedBB searchBox = player.getBoundingBox().grow(maxDistance + hitMargin, maxDistance + hitMargin, maxDistance + hitMargin);

        for (Entity entity : level.getNearbyEntities(searchBox, player)) {
            if (entity.getId() == primaryTarget.getId()) continue; // primary already attacked
            if (!(entity instanceof EntityLiving living) || !living.isAlive()) continue;

            Vector3 targetPos = new Vector3(entity.x, entity.y + living.getEyeHeight() * 0.5, entity.z);
            double distance = eyePos.distance(targetPos);
            if (distance > maxDistance + hitMargin) continue;

            Vector3 toEntity = targetPos.subtract(eyePos).normalize();
            double dot = direction.dot(toEntity);
            if (dot < minDot) continue;

            EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                    player, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, getJabDamage()
            );
            entity.attack(damageEvent);
        }
    }

    /**
     * Returns the total damage for a stab, including Lunge enchantment bonus.
     */
    public float getJabDamage() {
        float damage = getAttackDamage();
        int level = getEnchantmentLevel(Enchantment.ID_LUNGE);
        damage += level * 1.5f;
        return damage;
    }

    /**
     * Returns the tier-specific charge damage multiplier.
     * Applied as: multiplier × relativeVelocityBps × stageMultiplier
     * Values per wiki: Wood/Gold=0.7, Stone/Copper=0.82, Iron=0.95, Diamond=1.075, Netherite=1.2
     */
    public abstract float getChargeDamageMultiplier();

    /**
     * Returns the jab attack cooldown in milliseconds.
     * Derived from the use cooldown column (game ticks × 50ms/tick).
     */
    public abstract long getJabCooldownMs();

    /**
     * Time in milliseconds from pressing the use button until the charge becomes active.
     */
    public abstract long getActivationDelayMs();

    /** Duration of Stage 1 (ENGAGED) in milliseconds. */
    public abstract long getStage1DurationMs();

    /** Duration of Stage 2 (TIRED) in milliseconds. */
    public abstract long getStage2DurationMs();

    /** Duration of Stage 3 (DISENGAGED) in milliseconds. After this the charge expires. */
    public abstract long getStage3DurationMs();

    /**
     * Maximum closing speed (blocks/second) at which the charge hit dismounts a rider.
     * Dismount triggers when approachBps is below this value.
     */
    public abstract float getDismountSpeedBps();

    /**
     * Applies a forward momentum boost if the Lunge enchantment is present and conditions are met.
     * Consumes {@code lungeLevel} saturation/hunger points (saturation first) and 1 durability.
     */
    public void applyLunge(Player player) {
        if (!canLunge(player)) return;

        int lungeLevel = getEnchantmentLevel(Enchantment.ID_LUNGE);
        Vector3 dir = player.getDirectionVector();
        dir = new Vector3(dir.x, 0, dir.z);
        if (dir.lengthSquared() == 0) return;

        dir = dir.normalize().multiply(0.5 + (lungeLevel * 0.4));
        player.setMotion(player.getMotion().add(dir));
        player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_ATTACK);

        // Saturation is drained first; hunger only after saturation is depleted.
        // Level I = 1 point, Level II = 2, Level III = 3.
        player.getFoodData().useHunger(lungeLevel);

        // Consume 1 durability, respecting Unbreaking enchantment.
        if (!isUnbreakable() && !isDurable()) {
            meta++;
            player.getInventory().setItemInHand(this);
        }
    }

    /**
     * Returns true if the player currently satisfies all conditions to lunge.
     */
    public boolean canLunge(Player player) {
        int enchantmentLevel = getEnchantmentLevel(Enchantment.ID_LUNGE);
        if (enchantmentLevel <= 0) return false;

        if (player.riding != null) return false;

        if (player.isGliding() || player.isSwimming() || player.isInsideOfWater()) {
            return false;
        }

        int gamemode = player.getGamemode();
        if ((gamemode == Player.SURVIVAL || gamemode == Player.ADVENTURE)
                && player.getFoodData().getLevel() < minimumLungeFood) {
            return false;
        }

        return true;
    }

    /**
     * Called from a MOVING TARGET's movement handler when they approach a nearby spear holder
     * in charge mode. This enables the "target falls/runs onto a stationary spear" scenario:
     * the holder doesn't need to move — the target's own velocity drives the damage.
     *
     * @param holder    the player holding the lowered spear (may be stationary)
     * @param target    the entity moving into the spear
     * @param targetDx  target's X displacement this tick
     * @param targetDy  target's Y displacement this tick
     * @param targetDz  target's Z displacement this tick
     */
    public void onChargeFromTarget(Player holder, Entity target, double targetDx, double targetDy, double targetDz) {
        ChargeStage stage = getChargeStage(holder);
        if (stage == null) return;

        UUID uid = holder.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastHit = lastChargeHitTime.get(uid);
        if (lastHit != null && now - lastHit < CHARGE_HIT_COOLDOWN_MS) return;

        // Unit vector from holder to target
        double toX = target.x - holder.x;
        double toY = target.y - holder.y;
        double toZ = target.z - holder.z;
        double toLen = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (toLen < 0.001) return;
        toX /= toLen; toY /= toLen; toZ /= toLen;

        // Target approaching holder: target's displacement projected onto the holder→target axis,
        // negated because moving toward holder means moving opposite to that direction.
        float approachBps = (float) (-(toX * targetDx + toY * targetDy + toZ * targetDz) * 20.0);
        if (approachBps < MIN_CHARGE_DAMAGE_BPS) return; // must be closing in fast enough

        // Check that target is inside the holder's forward hitbox (spear is pointed somewhere)
        Vector3 hitDir = holder.getDirectionVector().normalize().multiply(1.5);
        AxisAlignedBB hitBox = holder.getBoundingBox()
                .grow(1.5, 1.0, 1.5)
                .offset(hitDir.x, hitDir.y, hitDir.z);
        if (!hitBox.intersectsWith(target.getBoundingBox())) return;

        float damage = getChargeDamage(stage, approachBps);

        PlayerSpearChargeEvent event = new PlayerSpearChargeEvent(holder, this, target, stage, damage);
        holder.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        damage = event.getDamage();
        float knockBack = (approachBps >= MIN_KNOCKBACK_BPS) ? CHARGE_KNOCKBACK : 0f;

        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                holder, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, knockBack
        );
        target.attack(damageEvent);

        if (approachBps < getDismountSpeedBps() && target.riding != null) {
            target.riding.dismountEntity(target);
        }

        lastChargeHitTime.put(uid, now);
        holder.getLevel().addLevelSoundEvent(holder, LevelSoundEventPacket.SOUND_ATTACK_STRONG);
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        // USE button press = jab: apply lunge + AOE entity hit (subject to cooldown)
        onSpearStab(player, player.getMovementSpeed());
        // Also begin charge mode if not already tracking this player
        if (!chargeStartTime.containsKey(player.getUniqueId())) {
            onChargeBegin(player);
        }
        return true;
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        onChargeEnd(player);
        return false;
    }

    // ── Charge attack ────────────────────────────────────────────────────────

    /**
     * Called when the player starts holding the use button with this spear.
     * Initialises the per-player charge state.
     */
    public void onChargeBegin(Player player) {
        UUID uid = player.getUniqueId();
        chargeStartTime.put(uid, System.currentTimeMillis());
        lastChargeHitTime.remove(uid);
    }

    /**
     * Called when the player releases the use button (or item is unequipped).
     * Clears the per-player charge state.
     */
    public void onChargeEnd(Player player) {
        UUID uid = player.getUniqueId();
        chargeStartTime.remove(uid);
        lastChargeHitTime.remove(uid);
    }

    /**
     * Returns the current charge stage for this player, or {@code null} if not charging,
     * still in the activation delay, or if the full charge duration has expired.
     */
    public ChargeStage getChargeStage(Player player) {
        Long start = chargeStartTime.get(player.getUniqueId());
        if (start == null) return null;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed < getActivationDelayMs()) return null; // activation delay not yet passed
        long active = elapsed - getActivationDelayMs();
        long stage1End = getStage1DurationMs();
        long stage2End = stage1End + getStage2DurationMs();
        long stage3End = stage2End + getStage3DurationMs();
        if (active >= stage3End) return null; // charge fully expired
        if (active < stage1End)  return ChargeStage.ENGAGED;
        if (active < stage2End)  return ChargeStage.TIRED;
        return ChargeStage.DISENGAGED;
    }

    /**
     * Returns the charge attack damage.
     * Formula: round(chargeDamageMultiplier × relativeVelocityBps) × stageMultiplier
     * The ENGAGED values produced match the spear damage table (blocks/second → HP).
     *
     * @param relativeVelocityBps closing speed between holder and target in blocks/second
     */
    public float getChargeDamage(ChargeStage stage, float relativeVelocityBps) {
        float stageMult = switch (stage) {
            case ENGAGED    -> 1.0f;
            case TIRED      -> 0.6f;
            case DISENGAGED -> 0.3f;
        };
        return Math.round(getChargeDamageMultiplier() * relativeVelocityBps) * stageMult;
    }

    /**
     * Called from Player movement handling while the player is using the spear (spear is lowered).
     * Damage is based on relative approach velocity between holder and target — a stationary
     * holder deals damage when an enemy charges or falls into the spear.
     *
     * Hitbox: offset in movement direction when holder is moving (handles Y-axis dives),
     * or looking direction when stationary (spear is pointed at where holder is aiming).
     *
     * @param player the player holding the lowered spear
     * @param dx     X displacement this tick (this.x - from.x in handleMovement)
     * @param dy     Y displacement this tick
     * @param dz     Z displacement this tick
     */
    public void onChargeMovement(Player player, double dx, double dy, double dz) {
        ChargeStage stage = getChargeStage(player);
        if (stage == null) return;

        // Per-hit cooldown
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastHit = lastChargeHitTime.get(uid);
        if (lastHit != null && now - lastHit < CHARGE_HIT_COOLDOWN_MS) return;

        Level level = player.getLevel();

        // Hitbox direction: movement direction when moving (handles Y-axis dive attacks),
        // looking direction when stationary (spear is "pointed" at the aimed target).
        double holderSpeedSq = dx * dx + dy * dy + dz * dz;
        Vector3 hitOffset;
        if (holderSpeedSq > 0.0001) {
            double len = Math.sqrt(holderSpeedSq);
            hitOffset = new Vector3(dx / len, dy / len, dz / len).multiply(1.5);
        } else {
            hitOffset = player.getDirectionVector().normalize().multiply(1.5);
        }

        AxisAlignedBB hitBox = player.getBoundingBox()
                .grow(1.5, 1.0, 1.5)
                .offset(hitOffset.x, hitOffset.y, hitOffset.z);

        // Find the target with the highest relative approach velocity.
        // Relative velocity = how fast both entities are closing in on each other:
        //   holderApproach:  holder's per-tick displacement projected toward the target × 20
        //   targetApproach:  target's motion projected toward the holder × 20 (negated because
        //                    moving toward holder = negative projection onto to-target direction)
        // entity.getMotion() is reliable for mobs (server-side physics) and for falling players
        // (Nukkit applies server-side gravity to motionY each tick via Entity.onUpdate).
        EntityLiving target = null;
        float bestRelVelocity = 0;

        for (Entity entity : level.getNearbyEntities(hitBox, player)) {
            if (!(entity instanceof EntityLiving living) || !living.isAlive()) continue;

            double toX = entity.x - player.x;
            double toY = (entity.y + living.getEyeHeight() * 0.5) - (player.y + player.getEyeHeight() * 0.5);
            double toZ = entity.z - player.z;
            double toLen = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
            if (toLen < 0.001) continue;
            toX /= toLen; toY /= toLen; toZ /= toLen;

            Vector3 targetMotion = entity.getMotion();
            float relVelocityBps = (float) (
                    (toX * dx + toY * dy + toZ * dz) * 20.0          // holder approaching target
                  - (toX * targetMotion.x + toY * targetMotion.y + toZ * targetMotion.z) * 20.0  // target approaching holder
            );

            if (relVelocityBps < MIN_CHARGE_DAMAGE_BPS) continue; // minimum closing speed to deal damage
            if (relVelocityBps <= bestRelVelocity) continue;

            bestRelVelocity = relVelocityBps;
            target = living;
        }

        if (target == null) return;

        float damage = getChargeDamage(stage, bestRelVelocity);

        PlayerSpearChargeEvent event = new PlayerSpearChargeEvent(player, this, target, stage, damage);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        damage = event.getDamage();

        float knockBack = (bestRelVelocity >= MIN_KNOCKBACK_BPS) ? CHARGE_KNOCKBACK : 0f;

        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, knockBack
        );
        target.attack(damageEvent);

        if (bestRelVelocity < getDismountSpeedBps() && target.riding != null) {
            target.riding.dismountEntity(target);
        }

        lastChargeHitTime.put(uid, now);
        level.addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_ATTACK_STRONG);
    }
}
