package cn.nukkit.item.enchantment.mace;

import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

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
        return 10 * level;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 15;
    }

    @Override
    public boolean isTreasure() {
        return true;
    }

    /**
     * Returns the upward velocity to apply to the attacker after a smash attack.
     * Each level launches the player ~7 blocks higher (7/14/21 blocks for levels I/II/III).
     * Uses v = sqrt(2 * g * h) where g ≈ 0.08 blocks/tick² in Minecraft physics.
     */
    public double getLaunchVelocity() {
        double targetHeight = this.getLevel() * 7.0;
        return Math.sqrt(2.0 * 0.08 * targetHeight);
    }
}
