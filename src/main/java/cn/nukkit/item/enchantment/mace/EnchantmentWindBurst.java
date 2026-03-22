package cn.nukkit.item.enchantment.mace;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

public class EnchantmentWindBurst extends Enchantment {

    public EnchantmentWindBurst() {
        super(ID_WIND_BURST, "wind_burst", Rarity.VERY_RARE, EnchantmentType.MACE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 10 + (level - 1) * 20;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 50;
    }

    @Override
    public boolean isTreasure() {
        return true;
    }

    /**
     * Returns the upward velocity to apply to the attacker after a smash attack.
     * Uses dynamic scaling based on fall distance, capped at 7.5 blocks.
     * Level bonus: +0.55 for II, +1.3 for III (matches PNX BreezeShootExecutor).
     */
    public double getLaunchVelocity(double fallDistance) {
        double clampedFall = Math.min(fallDistance, 7.5d);
        return 0.72d + clampedFall * 0.10d + switch (this.getLevel()) {
            case 2 -> 0.55d;
            case 3 -> 1.3d;
            default -> 0d;
        };
    }

    /**
     * Applies WindBurst gust effects on a mace smash attack:
     * - Pushes nearby entities outward from the attacker
     * - Spawns wind explosion particles on the attacker and affected entities
     * - Plays the mace smash air sound at the attacker's position
     *
     * The vertical launch of the attacker is handled separately in Player.java via
     * {@link #getLaunchVelocity(double)}, which is applied after this method executes.
     * This method guards against non-smash hits using the same 1.5-block fall
     * threshold used in Player.java.
     */
    @Override
    public void doAttack(Entity attacker, Entity entity) {
        if (attacker.level == null) {
            return;
        }

        // Only apply gust on a genuine smash attack (attacker fell at least 1.5 blocks)
        double fallDistance = Math.max(attacker.fallDistance, attacker.highestPosition - attacker.y);
        if (fallDistance < 1.5d && attacker.motionY < -0.08d) {
            fallDistance = Math.max(fallDistance, Math.min(2.5d, -attacker.motionY * 4d));
        }
        if (fallDistance < 1.5d) {
            return;
        }

        int level = this.getLevel();

        // Push nearby entities outward from the attacker
        AxisAlignedBB box = attacker.boundingBox.grow(2.5d, 2.5d, 2.5d);
        for (Entity nearby : attacker.level.getNearbyEntities(box, attacker)) {
            if (nearby == attacker || nearby.closed) {
                continue;
            }

            Vector3 direction = nearby.subtract(attacker).normalize();
            if (direction.lengthSquared() <= 0) {
                continue;
            }

            double gustStrength = 0.6d + 0.15d * level;
            Vector3 targetMotion = nearby.getMotion().add(direction.multiply(gustStrength));
            double minUpward = 0.4d + 0.1d * level;
            if (targetMotion.y < minUpward) {
                targetMotion.y = minUpward;
            }
            nearby.setMotion(targetMotion);

            attacker.level.addParticle(new GenericParticle(
                    nearby.add(0, nearby.getEyeHeight() * 0.6, 0),
                    Particle.TYPE_WIND_EXPLOSION
            ));
        }

        // Wind explosion particle on the attacker
        attacker.level.addParticle(new GenericParticle(
                attacker.add(0, attacker.getEyeHeight() * 0.6, 0),
                Particle.TYPE_WIND_EXPLOSION
        ));

        // Mace smash air sound
        attacker.level.addLevelSoundEvent(attacker, LevelSoundEventPacket.SOUND_MACE_SMASH_AIR);
    }
}
